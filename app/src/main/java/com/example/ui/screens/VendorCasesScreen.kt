package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorService
import com.example.ui.theme.JTOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun VendorCasesScreen() {
  val scope = rememberCoroutineScope()
  var cases by remember { mutableStateOf(JSONArray()) }
  var tickets by remember { mutableStateOf(JSONArray()) }
  var messages by remember { mutableStateOf(JSONArray()) }
  var selectedTicket by remember { mutableStateOf<String?>(null) }
  var subject by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var reply by remember { mutableStateOf("") }
  var caseReplies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(true) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }

  suspend fun reload() {
    errorMessage = ""
    try {
      cases = VendorService.listCases()
      tickets = VendorService.listSupportTickets()
      selectedTicket?.let { messages = VendorService.listSupportMessages(it) }
    } catch (e: Exception) { errorMessage = e.message ?: "Cases and support could not be loaded." }
    finally { loading = false }
  }
  LaunchedEffect(Unit) { reload() }
  LaunchedEffect(selectedTicket) {
    val id = selectedTicket ?: return@LaunchedEffect
    while (true) {
      try { messages = VendorService.listSupportMessages(id) }
      catch (e: Exception) { errorMessage = e.message ?: "Support conversation could not be refreshed." }
      delay(10_000)
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Cases & support", style = MaterialTheme.typography.headlineSmall)
      TextButton(onClick = { scope.launch { reload() } }) { Text("Refresh") }
    }
    Text("Customer cases are limited to your own shop's order items. Contact JTBazar Support here for account or listing help.", style = MaterialTheme.typography.bodySmall)
    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary)
    if (loading) CircularProgressIndicator(color = JTOrange)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Customer cases", style = MaterialTheme.typography.titleLarge)
      Text("${cases.length()} case(s)", style = MaterialTheme.typography.labelMedium)
    }
    if (!loading && cases.length() == 0) Text("No customer cases are open for your shop.")
    for (i in 0 until cases.length()) {
      val item = cases.optJSONObject(i) ?: continue
      val id = item.optString("id")
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("${item.optString("case_type", "Customer case")} • ${item.optString("status", "open").replace('_', ' ')}", style = MaterialTheme.typography.titleSmall)
          Text(item.optString("reason", "No reason supplied."))
          if (item.optString("seller_response").isNotBlank()) Text("Your previous response: ${item.optString("seller_response")}", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = caseReplies[id].orEmpty(),
            onValueChange = { value -> caseReplies = caseReplies.toMutableMap().also { it[id] = value.take(4000) } },
            label = { Text("Your response to JTBazar") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
          )
          Button(enabled = !saving && caseReplies[id].orEmpty().trim().length >= 3, onClick = {
            val response = caseReplies[id].orEmpty()
            scope.launch {
              saving = true
              try {
                VendorService.respondToCase(id, response)
                caseReplies = caseReplies.toMutableMap().also { it.remove(id) }
                cases = VendorService.listCases()
                info = "Your case response was submitted."
              } catch (e: Exception) { errorMessage = e.message ?: "Case response could not be sent." }
              finally { saving = false }
            }
          }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Send case response") }
        }
      }
    }

    Divider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Text("JTBazar Support", style = MaterialTheme.typography.titleLarge)
      Button(onClick = { showCreateDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Create support") }
    }
    if (!loading && tickets.length() == 0) Text("No support tickets yet. Create one if you need help.")
    for (i in 0 until tickets.length()) {
      val ticket = tickets.optJSONObject(i) ?: continue
      val id = ticket.optString("id")
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text("${ticket.optString("subject")} • ${ticket.optString("status", "open").replace('_', ' ')}", style = MaterialTheme.typography.titleSmall)
          Text(ticket.optString("message"), style = MaterialTheme.typography.bodySmall)
          if (ticket.optString("admin_response").isNotBlank()) Text("Support update: ${ticket.optString("admin_response")}", style = MaterialTheme.typography.bodySmall)
          OutlinedButton(onClick = {
            selectedTicket = if (selectedTicket == id) null else id
            if (selectedTicket != null) scope.launch {
              try { messages = VendorService.listSupportMessages(id) }
              catch (e: Exception) { errorMessage = e.message ?: "Conversation could not be loaded." }
            }
          }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedTicket == id) "Close conversation" else "Open conversation") }
          if (selectedTicket == id) {
            Text("Conversation", style = MaterialTheme.typography.labelLarge)
            for (m in 0 until messages.length()) {
              val message = messages.optJSONObject(m) ?: continue
              val seller = message.optString("sender_role") == "seller"
              Surface(
                color = if (seller) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(Modifier.padding(10.dp)) {
                  Text(if (seller) "You" else "JTBazar Support", style = MaterialTheme.typography.labelSmall)
                  Text(message.optString("message"))
                  Text(message.optString("created_at"), style = MaterialTheme.typography.labelSmall)
                }
              }
            }
            if (ticket.optString("status").equals("resolved", true)) {
              Text("This ticket is resolved. Create a new support ticket for another issue.")
            } else {
              OutlinedTextField(reply, { reply = it.take(4000) }, label = { Text("Write your message") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
              Button(enabled = !saving && reply.isNotBlank(), onClick = {
                scope.launch {
                  saving = true
                  try {
                    VendorService.sendSupportMessage(id, reply)
                    reply = ""
                    messages = VendorService.listSupportMessages(id)
                    info = "Your support message was sent."
                  } catch (e: Exception) { errorMessage = e.message ?: "Message could not be sent." }
                  finally { saving = false }
                }
              }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text("Send message") }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { if (!saving) showCreateDialog = false },
      title = { Text("Create support request") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Tell JTBazar Support what you need help with.")
          OutlinedTextField(subject, { subject = it.take(160) }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
          OutlinedTextField(description, { description = it.take(4000) }, label = { Text("Describe your issue *") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        }
      },
      confirmButton = {
        Button(enabled = !saving && subject.trim().length >= 3 && description.trim().length >= 5, onClick = {
          scope.launch {
            saving = true; errorMessage = ""
            try {
              VendorService.createSupportTicket(subject, description)
              subject = ""; description = ""; showCreateDialog = false
              tickets = VendorService.listSupportTickets()
              info = "Support request created. Open it below to chat with JTBazar Support."
            } catch (e: Exception) { errorMessage = e.message ?: "Support request could not be created." }
            finally { saving = false }
          }
        }, colors = ButtonDefaults.buttonColors(containerColor = JTOrange)) { Text(if (saving) "Creating…" else "Create request") }
      },
      dismissButton = { TextButton(onClick = { showCreateDialog = false }, enabled = !saving) { Text("Cancel") } }
    )
  }
}
