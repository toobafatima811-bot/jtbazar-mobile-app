package com.example

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.data.supabase.AppErrorMapper
import com.example.data.supabase.ErrorContext
import com.example.data.supabase.VendorBusinessType
import com.example.data.supabase.VendorOrderStatus
import com.example.localization.AppLanguage
import com.example.localization.AppLanguageManager
import com.example.localization.JTBazarLocalizedContainer
import com.example.localization.LocalAppLanguage
import com.example.localization.appText
import com.example.ui.screens.AccountSettingsScreen
import com.example.ui.screens.BusinessTypeDropdown
import com.example.ui.screens.CustomerDeliveryOtpEligibility
import com.example.ui.screens.CustomerSupportScreen
import com.example.ui.screens.DriverDeliveryFilter
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReturnsRefundsScreen
import com.example.ui.screens.SecurityPasswordScreen
import com.example.ui.screens.VendorBottomTab
import com.example.ui.screens.deliveryOtpEligibilityHint
import com.example.ui.screens.mapCustomerDeliveryOtpError
import com.example.ui.screens.missingCompletionStepsMessage
import com.example.ui.screens.validateDeliveryProofImage
import com.example.ui.theme.JTBazarTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UrduLocalizationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @After
  fun resetLanguage() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppLanguageManager.setLanguage(AppLanguage.ENGLISH, context)
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
  }

  private fun containsUrduScript(text: String): Boolean {
    return text.any { ch -> ch in '\u0600'..'\u06FF' }
  }

  @Test
  fun languagePreferencePersistsAcrossReinitialization() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AppLanguageManager.setLanguage(AppLanguage.URDU, context)
    assertEquals(AppLanguage.URDU, AppLanguageManager.currentLanguage.value)
    assertTrue(AppLanguageManager.isUrdu)

    // Simulate cold start reading SharedPreferences back into state.
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    AppLanguageManager.init(context)
    assertEquals(AppLanguage.URDU, AppLanguageManager.currentLanguage.value)

    AppLanguageManager.setLanguage(AppLanguage.ENGLISH, context)
    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    AppLanguageManager.init(context)
    assertEquals(AppLanguage.ENGLISH, AppLanguageManager.currentLanguage.value)
  }

  @Test
  fun domainHelpersAndErrorMapperTranslateToProperUrduScript() {
    AppLanguageManager.setLanguageForTest(AppLanguage.ENGLISH)
    val englishNetworkMsg = AppErrorMapper.toCustomerMessage(
      IOException("Failed"),
      ErrorContext.PRODUCT_LOAD,
      isOnline = false
    )
    assertEquals("No internet connection. Please check your connection and try again.", englishNetworkMsg)

    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)

    ErrorContext.entries.forEach { ctx ->
      val urduDefault = AppErrorMapper.defaultMessageFor(ctx)
      assertTrue("Expected Urdu script for ErrorContext.$ctx: $urduDefault", containsUrduScript(urduDefault))
    }

    val urduNetworkMsg = AppErrorMapper.toCustomerMessage(
      IOException("Failed"),
      ErrorContext.PRODUCT_LOAD,
      isOnline = false
    )
    assertTrue("Expected Urdu script in network error: $urduNetworkMsg", containsUrduScript(urduNetworkMsg))

    VendorOrderStatus.entries.forEach { status ->
      val localized = status.localizedLabel()
      assertTrue("Expected Urdu script for status ${status.name}: $localized", containsUrduScript(localized))
      assertNotEquals(status.label, localized)
    }

    VendorBottomTab.entries.forEach { tab ->
      val localized = tab.localizedLabel()
      assertTrue("Expected Urdu script for vendor tab ${tab.name}: $localized", containsUrduScript(localized))
    }

    DriverDeliveryFilter.entries.forEach { filter ->
      val localized = filter.localizedLabel()
      assertTrue("Expected Urdu script for driver filter ${filter.name}: $localized", containsUrduScript(localized))
    }

    CustomerDeliveryOtpEligibility.entries
      .filter { it != CustomerDeliveryOtpEligibility.ELIGIBLE }
      .forEach { eligibility ->
        val hint = deliveryOtpEligibilityHint(eligibility)
        assertNotNull(hint)
        assertTrue("Expected Urdu script for OTP hint $eligibility: $hint", containsUrduScript(hint!!))
      }

    val (_, otpErr) = mapCustomerDeliveryOtpError(IOException("42501: Delivery not found for this customer"))
    assertTrue("Expected Urdu script for mapped OTP error: $otpErr", containsUrduScript(otpErr))

    val missingSteps = missingCompletionStepsMessage(otpVerified = false, proofSaved = false)
    assertNotNull(missingSteps)
    assertTrue("Expected Urdu script for missing driver steps: $missingSteps", containsUrduScript(missingSteps!!))

    val validationError = runCatching {
      validateDeliveryProofImage(ByteArray(0), "image/jpeg")
    }.exceptionOrNull()?.message.orEmpty()
    assertTrue("Expected Urdu script for empty proof image error: $validationError", containsUrduScript(validationError))
  }

  @Test
  fun localizedContainerAppliesRtlInUrduAndAccountSettingsSwitchesLanguageLive() {
    var observedDirection: LayoutDirection? = null
    var observedLanguage: AppLanguage? = null

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        JTBazarLocalizedContainer {
          observedDirection = LocalLayoutDirection.current
          observedLanguage = LocalAppLanguage.current
          AccountSettingsScreen(
            onNavigateToSecurity = {},
            onBack = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(AppLanguage.ENGLISH, observedLanguage)
    assertEquals(LayoutDirection.Ltr, observedDirection)
    composeTestRule.onNodeWithText("Account Settings").assertIsDisplayed()

    composeTestRule.onNodeWithTag("language_chip_urdu").performClick()
    composeTestRule.waitForIdle()

    assertEquals(AppLanguage.URDU, observedLanguage)
    assertEquals(LayoutDirection.Rtl, observedDirection)
    composeTestRule.onNodeWithText("اکاؤنٹ کی ترتیبات").assertIsDisplayed()
    composeTestRule.onNodeWithText("پاس ورڈ اور سیکیورٹی").assertIsDisplayed()

    composeTestRule.onNodeWithTag("language_chip_english").performClick()
    composeTestRule.waitForIdle()

    assertEquals(AppLanguage.ENGLISH, observedLanguage)
    assertEquals(LayoutDirection.Ltr, observedDirection)
    composeTestRule.onNodeWithText("Account Settings").assertIsDisplayed()
  }

  @Test
  fun customerScreensRenderUrduLabelsAndRtlWhenUrduSelected() {
    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    val showScreen = androidx.compose.runtime.mutableStateOf("notifications")
    var capturedDirection: LayoutDirection? = null
    var sampleGreeting = ""

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        JTBazarLocalizedContainer {
          capturedDirection = LocalLayoutDirection.current
          sampleGreeting = appText("Notifications", "اطلاعات")
          when (showScreen.value) {
            "notifications" -> NotificationsScreen(onBack = {})
            "returns" -> ReturnsRefundsScreen(onBack = {})
            else -> SecurityPasswordScreen(onBack = {})
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(LayoutDirection.Rtl, capturedDirection)
    assertEquals("اطلاعات", sampleGreeting)
    composeTestRule.onNodeWithText("اطلاعات").assertIsDisplayed()
    composeTestRule.onNodeWithText("ابھی تک کوئی اطلاع نہیں").assertIsDisplayed()

    showScreen.value = "returns"
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("واپسی اور رقم کی واپسی").assertIsDisplayed()

    showScreen.value = "security"
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("پاس ورڈ اور سیکیورٹی").assertIsDisplayed()
    composeTestRule.onNodeWithText("اکاؤنٹ کا پاس ورڈ تبدیل کریں").assertIsDisplayed()
  }

  @Test
  fun activityResultRegistryOwnerRemainsAvailableInsideLocalizedContainer() {
    var ownerInEnglish: Any? = null
    var ownerInUrdu: Any? = null

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        JTBazarLocalizedContainer {
          val activeLanguage = LocalAppLanguage.current
          val owner = LocalActivityResultRegistryOwner.current
          val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
          ) { }
          assertNotNull(launcher)
          if (activeLanguage == AppLanguage.URDU) {
            ownerInUrdu = owner
          } else {
            ownerInEnglish = owner
          }
          androidx.compose.material3.Text(
            text = appText("Ready English", "تیار اردو")
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    assertNotNull("ActivityResultRegistryOwner must be available in English", ownerInEnglish)
    composeTestRule.onNodeWithText("Ready English").assertIsDisplayed()

    val context = ApplicationProvider.getApplicationContext<Context>()
    AppLanguageManager.setLanguage(AppLanguage.URDU, context)
    composeTestRule.waitForIdle()

    assertNotNull("ActivityResultRegistryOwner must remain available in Urdu", ownerInUrdu)
    composeTestRule.onNodeWithText("تیار اردو").assertIsDisplayed()
  }

  @Test
  fun customerSupportAndVendorBusinessTypeRenderUrduAndRtl() {
    AppLanguageManager.setLanguageForTest(AppLanguage.URDU)
    val showSupport = androidx.compose.runtime.mutableStateOf(true)
    var observedDirection: LayoutDirection? = null

    composeTestRule.setContent {
      JTBazarTheme(darkTheme = false) {
        JTBazarLocalizedContainer {
          observedDirection = LocalLayoutDirection.current
          if (showSupport.value) {
            CustomerSupportScreen(onBack = {})
          } else {
            BusinessTypeDropdown(
              selectedType = VendorBusinessType.GROCERY_STORE.key,
              onTypeSelected = {}
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(LayoutDirection.Rtl, observedDirection)
    composeTestRule.onNodeWithText("کسٹمر سپورٹ اور شکایات").assertIsDisplayed()
    composeTestRule.onNodeWithText("آرڈر ڈیلیوری").assertIsDisplayed()

    showSupport.value = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("آپ کس قسم کا کاروبار کرتے ہیں؟ *").assertIsDisplayed()
    composeTestRule.onNodeWithText("کریانہ اسٹور").assertIsDisplayed()
  }
}
