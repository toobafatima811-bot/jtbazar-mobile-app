package com.example

import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseUser
import com.example.ui.screens.DriverDocType
import com.example.ui.screens.buildDriverDocumentPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class DriverDocumentUploadTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterClass
    @JvmStatic
    fun tearDownClass() {
      Dispatchers.resetMain()
    }
  }

  @After
  fun tearDown() {
    SupabaseClient.storageUrlOverride = null
    SupabaseClient.clearSession()
  }

  private fun createTestJwt(subject: String): String {
    val header = """{"alg":"HS256","typ":"JWT"}""".encodeUtf8().base64Url().trimEnd('=')
    val payload = """{"sub":"$subject","role":"authenticated"}""".encodeUtf8().base64Url().trimEnd('=')
    return "$header.$payload.test_signature"
  }

  private data class CapturedHttpRequest(
    val method: String,
    val requestTarget: String,
    val headers: Map<String, String>,
    val bodySize: Int
  )

  private fun startStubStorageServer(expectedRequests: Int): Pair<ServerSocket, CopyOnWriteArrayList<CapturedHttpRequest>> {
    val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    val captured = CopyOnWriteArrayList<CapturedHttpRequest>()
    val ready = CountDownLatch(1)

    thread(isDaemon = true) {
      ready.countDown()
      repeat(expectedRequests) {
        try {
          server.accept().use { socket ->
            val input = BufferedInputStream(socket.getInputStream())
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (true) {
              val b = input.read()
              if (b == -1) break
              headerBytes.write(b)
              state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
              }
              if (state == 4) break
            }

            val rawHeaders = headerBytes.toString(Charsets.UTF_8.name())
            val lines = rawHeaders.split("\r\n").filter { it.isNotEmpty() }
            val requestLineParts = lines.firstOrNull()?.split(" ") ?: emptyList()
            val method = requestLineParts.getOrElse(0) { "" }
            val target = requestLineParts.getOrElse(1) { "" }

            val headers = linkedMapOf<String, String>()
            for (line in lines.drop(1)) {
              val colon = line.indexOf(':')
              if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
              }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            var remaining = contentLength
            val buf = ByteArray(1024)
            while (remaining > 0) {
              val read = input.read(buf, 0, minOf(buf.size, remaining))
              if (read <= 0) break
              remaining -= read
            }

            captured.add(CapturedHttpRequest(method, target, headers, contentLength))

            val responseBody = """{"Key":"driver-documents/ok"}"""
            val response = "HTTP/1.1 200 OK\r\n" +
              "Content-Type: application/json\r\n" +
              "Content-Length: ${responseBody.toByteArray().size}\r\n" +
              "Connection: close\r\n\r\n" +
              responseBody
            socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
          }
        } catch (_: Exception) {
          return@thread
        }
      }
    }

    ready.await(2, TimeUnit.SECONDS)
    return server to captured
  }

  @Test
  fun driverDocumentPathFormatMatchesExpectedContract() {
    val uid = "11111111-1111-4111-8111-111111111111"
    val ts = 1700000123456L

    assertEquals("$uid/cnic_front_$ts.jpg", buildDriverDocumentPath(uid, DriverDocType.CNIC_FRONT, ts))
    assertEquals("$uid/cnic_back_$ts.jpg", buildDriverDocumentPath(uid, DriverDocType.CNIC_BACK, ts))
    assertEquals("$uid/vehicle_plate_$ts.jpg", buildDriverDocumentPath(uid, DriverDocType.VEHICLE_PLATE, ts))
  }

  @Test
  fun extractJwtSubjectDecodesAuthenticatedUid() {
    val uid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
    val token = createTestJwt(uid)
    assertEquals(uid, SupabaseClient.extractJwtSubject(token))
    assertNull(SupabaseClient.extractJwtSubject("not-a-jwt"))
  }

  @Test
  fun uploadPrivateFileRejectsUnauthenticatedSessionWithoutNetworkCall() = runBlocking {
    SupabaseClient.clearSession()
    val result = SupabaseClient.uploadPrivateFile(
      bucket = "driver-documents",
      path = "11111111-1111-4111-8111-111111111111/cnic_front_1700000000000.jpg",
      bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
      mimeType = "image/jpeg"
    )
    assertTrue("Upload without session must fail", result.isFailure)
  }

  @Test
  fun uploadPrivateFileRejectsJwtSubjectMismatch() = runBlocking {
    val userId = "11111111-1111-4111-8111-111111111111"
    val otherSubJwt = createTestJwt("22222222-2222-4222-8222-222222222222")
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = otherSubJwt,
        refreshToken = "refresh_tok",
        user = SupabaseUser(id = userId, email = "driver@jtbazar.pk")
      )
    )

    val result = SupabaseClient.uploadPrivateFile(
      bucket = "driver-documents",
      path = "$userId/cnic_front_1700000000000.jpg",
      bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
      mimeType = "image/jpeg"
    )
    assertTrue("Upload with mismatched JWT sub must fail", result.isFailure)
  }

  @Test
  fun uploadPrivateFileRejectsUploadingToDifferentUserFolder() = runBlocking {
    val userId = "11111111-1111-4111-8111-111111111111"
    val otherFolderId = "99999999-9999-4999-8999-999999999999"
    val jwt = createTestJwt(userId)
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = jwt,
        refreshToken = "refresh_tok",
        user = SupabaseUser(id = userId, email = "driver@jtbazar.pk")
      )
    )

    val result = SupabaseClient.uploadPrivateFile(
      bucket = "driver-documents",
      path = "$otherFolderId/cnic_front_1700000000000.jpg",
      bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
      mimeType = "image/jpeg"
    )
    assertTrue("Upload to another user's folder must fail", result.isFailure)
  }

  @Test
  fun uploadPrivateFilePostsAuthenticatedJpegWithoutUpsertForAllThreeDriverDocs() = runBlocking {
    val userId = "11111111-1111-4111-8111-111111111111"
    val jwt = createTestJwt(userId)
    SupabaseClient.setSession(
      SupabaseSession(
        accessToken = jwt,
        refreshToken = "refresh_tok",
        user = SupabaseUser(id = userId, email = "driver@jtbazar.pk")
      )
    )

    val (server, captured) = startStubStorageServer(expectedRequests = 3)
    try {
      SupabaseClient.storageUrlOverride = "http://127.0.0.1:${server.localPort}"
      val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)

      val docPaths = listOf(
        buildDriverDocumentPath(userId, DriverDocType.CNIC_FRONT, 1700000000001L),
        buildDriverDocumentPath(userId, DriverDocType.CNIC_BACK, 1700000000002L),
        buildDriverDocumentPath(userId, DriverDocType.VEHICLE_PLATE, 1700000000003L)
      )

      docPaths.forEach { expectedPath ->
        val result = SupabaseClient.uploadPrivateFile("driver-documents", expectedPath, jpegBytes, "image/jpeg")
        assertTrue("Upload should succeed for $expectedPath: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(expectedPath, result.getOrNull())
      }

      assertEquals(3, captured.size)
      captured.forEachIndexed { index, req ->
        assertEquals("POST", req.method)
        assertEquals("/object/driver-documents/${docPaths[index]}", req.requestTarget)
        assertEquals("Bearer $jwt", req.headers["authorization"])
        assertFalse("x-upsert must NOT be sent for private driver-documents", req.headers.containsKey("x-upsert"))
        assertEquals("image/jpeg", req.headers["content-type"])
        assertEquals(jpegBytes.size, req.bodySize)
      }
    } finally {
      server.close()
    }
  }
}
