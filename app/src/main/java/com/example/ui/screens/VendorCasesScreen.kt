package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.supabase.VendorService
import com.example.ui.components.AppCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusTone
import com.example.ui.components.VendorAlertBanner
import com.example.ui.components.VendorOutlinedTextField
import com.example.ui.components.VendorSkeletonCard
import com.example.ui.theme.VendorCenterTheme
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes
import com.example.ui.theme.VendorSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.optSafeString(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

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
  var dialogError by remember { mutableStateOf("") }
  var info by remember { mutableStateOf("") }

  suspend fun reload() {
    errorMessage = ""
    try {
      cases = VendorService.listCases()
      tickets = VendorService.listSupportTickets()
      selectedTicket?.let { messages = VendorService.listSupportMessages(it) }
    } catch (e: Exception) {
      errorMessage = e.message ?: "Cases and support could not be loaded."
    } finally {
      loading = false
    }
  }

  LaunchedEffect(Unit) { reload() }

  LaunchedEffect(selectedTicket) {
    val id = selectedTicket ?: return@LaunchedEffect
    while (true) {
      try {
        messages = VendorService.listSupportMessages(id)
      } catch (e: Exception) {
        errorMessage = e.message ?: "Support conversation could not be refreshed."
      }
      delay(10_000)
    }
  }

  val caseItems = remember(cases) {
    (0 until cases.length()).mapNotNull { cases.optJSONObject(it) }.map { item ->
      VendorCaseItemUiModel(
        id = item.optSafeString("id"),
        caseType = item.optSafeString("case_type", "Customer case").ifBlank { "Customer case" },
        status = item.optSafeString("status", "open").ifBlank { "open" },
        reason = item.optSafeString("reason", "No reason supplied.").ifBlank { "No reason supplied." },
        sellerResponse = item.optSafeString("seller_response")
      )
    }
  }

  val ticketItems = remember(tickets) {
    (0 until tickets.length()).mapNotNull { tickets.optJSONObject(it) }.map { ticket ->
      VendorSupportTicketUiModel(
        id = ticket.optSafeString("id"),
        subject = ticket.optSafeString("subject", "Support request"),
        message = ticket.optSafeString("message"),
        status = ticket.optSafeString("status", "open").ifBlank { "open" },
        adminResponse = ticket.optSafeString("admin_response")
      )
    }
  }

  val ticketMessageItems = remember(messages) {
    (0 until messages.length()).mapNotNull { messages.optJSONObject(it) }.map { msg ->
      VendorSupportMessageUiModel(
        senderRole = msg.optSafeString("sender_role"),
        message = msg.optSafeString("message"),
        createdAt = msg.optSafeString("created_at")
      )
    }
  }

  val casesState = when {
    loading -> VendorSectionState.Loading
    errorMessage.isNotBlank() && caseItems.isEmpty() && ticketItems.isEmpty() ->
      VendorSectionState.Error(errorMessage)
    caseItems.isEmpty() -> VendorSectionState.Empty
    else -> VendorSectionState.Content(caseItems)
  }

  val ticketsState = when {
    loading -> VendorSectionState.Loading
    errorMessage.isNotBlank() && caseItems.isEmpty() && ticketItems.isEmpty() ->
      VendorSectionState.Error(errorMessage)
    ticketItems.isEmpty() -> VendorSectionState.Empty
    else -> VendorSectionState.Content(ticketItems)
  }

  val uiState = VendorCasesUiState(
    casesState = casesState,
    ticketsState = ticketsState,
    selectedTicketId = selectedTicket,
    selectedTicketMessages = ticketMessageItems,
    isSaving = saving,
    bannerError = if (caseItems.isNotEmpty() || ticketItems.isNotEmpty()) errorMessage else "",
    infoMessage = info
  )

  VendorCasesContent(
    uiState = uiState,
    caseReplies = caseReplies,
    supportReply = reply,
    onRefresh = {
      scope.launch {
        loading = true
        reload()
      }
    },
    onCaseReplyChange = { id, value ->
      caseReplies = caseReplies.toMutableMap().also { it[id] = value.take(4000) }
    },
    onSendCaseResponse = { caseId ->
      val response = caseReplies[caseId].orEmpty()
      scope.launch {
        saving = true
        errorMessage = ""
        info = ""
        try {
          VendorService.respondToCase(caseId, response)
          caseReplies = caseReplies.toMutableMap().also { it.remove(caseId) }
          cases = VendorService.listCases()
          info = "Your case response was submitted."
        } catch (e: Exception) {
          errorMessage = e.message ?: "Case response could not be sent."
        } finally {
          saving = false
        }
      }
    },
    onToggleTicketConversation = { ticketId ->
      selectedTicket = if (selectedTicket == ticketId) null else ticketId
    },
    onSupportReplyChange = { reply = it.take(4000) },
    onSendSupportMessage = { ticketId ->
      scope.launch {
        saving = true
        errorMessage = ""
        info = ""
        try {
          VendorService.sendSupportMessage(ticketId, reply)
          reply = ""
          messages = VendorService.listSupportMessages(ticketId)
          info = "Your support message was sent."
        } catch (e: Exception) {
          errorMessage = e.message ?: "Message could not be sent."
        } finally {
          saving = false
        }
      }
    },
    onOpenCreateTicket = {
      dialogError = ""
      showCreateDialog = true
    },
    onDismissAlert = {
      errorMessage = ""
      info = ""
    }
  )

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { if (!saving) showCreateDialog = false },
      containerColor = VendorColors.CardWhite,
      shape = VendorShapes.card,
      title = {
        Text(
          text = "Create support ticket",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = VendorColors.TextPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
          Text(
            text = "Tell JTBazar Support what you need help with.",
            style = MaterialTheme.typography.bodySmall,
            color = VendorColors.TextSecondary
          )
          VendorOutlinedTextField(
            value = subject,
            onValueChange = {
              subject = it.take(160)
              dialogError = ""
            },
            label = "Subject *",
            leadingIcon = Icons.Rounded.Subject,
            enabled = !saving
          )
          VendorOutlinedTextField(
            value = description,
            onValueChange = {
              description = it.take(4000)
              dialogError = ""
            },
            label = "Describe your issue *",
            leadingIcon = Icons.Rounded.Description,
            minLines = 4,
            enabled = !saving
          )
          if (dialogError.isNotBlank()) {
            VendorAlertBanner(message = dialogError, isError = true)
          }
        }
      },
      confirmButton = {
        PrimaryButton(
          text = if (saving) "Creating..." else "Create ticket",
          loading = saving,
          enabled = subject.trim().length >= 3 && description.trim().length >= 5,
          onClick = {
            scope.launch {
              saving = true
              errorMessage = ""
              dialogError = ""
              try {
                VendorService.createSupportTicket(subject, description)
                subject = ""
                description = ""
                showCreateDialog = false
                tickets = VendorService.listSupportTickets()
                info = "Support ticket created. Open it below to chat with JTBazar Support."
              } catch (e: Exception) {
                dialogError = e.message ?: "Support request could not be created."
              } finally {
                saving = false
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(
          onClick = { showCreateDialog = false },
          enabled = !saving,
          modifier = Modifier.height(VendorSpacing.touchMin)
        ) {
          Text("Cancel", color = VendorColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        }
      }
    )
  }
}

@Composable
fun VendorCasesContent(
  uiState: VendorCasesUiState,
  caseReplies: Map<String, String>,
  supportReply: String,
  onRefresh: () -> Unit,
  onCaseReplyChange: (String, String) -> Unit,
  onSendCaseResponse: (String) -> Unit,
  onToggleTicketConversation: (String) -> Unit,
  onSupportReplyChange: (String) -> Unit,
  onSendSupportMessage: (String) -> Unit,
  onOpenCreateTicket: () -> Unit,
  onDismissAlert: () -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(VendorColors.Background),
    contentPadding = PaddingValues(VendorSpacing.md),
    verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)
  ) {
    item {
      SectionHeader(
        title = "Cases & support",
        subtitle = "Customer cases for your order items and direct JTBazar Support.",
        actionLabel = "Refresh",
        actionIcon = Icons.Rounded.Refresh,
        onActionClick = onRefresh
      )
    }

    if (uiState.bannerError.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.bannerError,
          isError = true,
          onDismiss = onDismissAlert
        )
      }
    }

    if (uiState.infoMessage.isNotBlank()) {
      item {
        VendorAlertBanner(
          message = uiState.infoMessage,
          isError = false,
          onDismiss = onDismissAlert
        )
      }
    }

    // Section 1: Customer Cases
    item {
      val caseCount = (uiState.casesState as? VendorSectionState.Content)?.data?.size ?: 0
      SectionHeader(
        title = "Customer cases",
        subtitle = "$caseCount case(s) assigned to your shop"
      )
    }

    when (val state = uiState.casesState) {
      VendorSectionState.Loading -> {
        items(2) { VendorSkeletonCard() }
      }

      is VendorSectionState.Error -> {
        item {
          ErrorState(
            title = "Could not load cases & support",
            message = state.message,
            onRetry = onRefresh
          )
        }
      }

      VendorSectionState.Empty -> {
        item {
          EmptyState(
            icon = Icons.Rounded.Gavel,
            title = "No customer cases",
            description = "No customer cases are currently open for your shop."
          )
        }
      }

      is VendorSectionState.Content -> {
        items(state.data, key = { it.id }) { item ->
          val isResponded = item.status.equals("seller_responded", true) ||
            item.status.equals("resolved", true)
          val draftReply = caseReplies[item.id].orEmpty()

          AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = item.caseType.replace('_', ' '),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = VendorColors.TextPrimary,
                  modifier = Modifier.weight(1f)
                )
                StatusBadge(
                  text = item.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                  tone = if (isResponded) StatusTone.SUCCESS else StatusTone.WARNING,
                  icon = if (isResponded) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber
                )
              }

              Text(
                text = item.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = VendorColors.TextPrimary
              )

              if (item.sellerResponse.isNotBlank()) {
                Surface(
                  shape = VendorShapes.input,
                  color = VendorColors.Background,
                  border = BorderStroke(1.dp, VendorColors.BorderSubtle),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(VendorSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Text(
                      text = "Your previous response",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = VendorColors.TextSecondary
                    )
                    Text(
                      text = item.sellerResponse,
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextPrimary
                    )
                  }
                }
              }

              VendorOutlinedTextField(
                value = draftReply,
                onValueChange = { onCaseReplyChange(item.id, it) },
                label = "Your response to JTBazar",
                leadingIcon = Icons.Rounded.ChatBubbleOutline,
                minLines = 2
              )

              PrimaryButton(
                text = "Send case response",
                icon = Icons.Rounded.Send,
                enabled = !uiState.isSaving && draftReply.trim().length >= 3,
                loading = uiState.isSaving,
                onClick = { onSendCaseResponse(item.id) },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }
        }
      }
    }

    // Section 2: JTBazar Support Tickets
    if (uiState.ticketsState !is VendorSectionState.Error) {
      item {
        Spacer(modifier = Modifier.height(VendorSpacing.xxs))
        HorizontalDivider(color = VendorColors.BorderSubtle)
        Spacer(modifier = Modifier.height(VendorSpacing.sm))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "JTBazar Support",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = VendorColors.TextPrimary
            )
            Text(
              text = "Get help with listings, payouts or account questions",
              style = MaterialTheme.typography.bodySmall,
              color = VendorColors.TextSecondary
            )
          }
        }
        Spacer(modifier = Modifier.height(VendorSpacing.xs))
        PrimaryButton(
          text = "Create support ticket",
          icon = Icons.Rounded.Add,
          onClick = onOpenCreateTicket,
          modifier = Modifier.fillMaxWidth()
        )
      }

      when (val tState = uiState.ticketsState) {
        VendorSectionState.Loading -> {
          items(2) { VendorSkeletonCard() }
        }

        is VendorSectionState.Error -> Unit

        VendorSectionState.Empty -> {
          item {
            EmptyState(
              icon = Icons.Rounded.SupportAgent,
              title = "No support tickets yet",
              description = "Create a support ticket if you need help from the JTBazar marketplace team."
            )
          }
        }

        is VendorSectionState.Content -> {
          items(tState.data, key = { it.id }) { ticket ->
            val isExpanded = uiState.selectedTicketId == ticket.id
            val isResolved = ticket.status.equals("resolved", true)

            AppCard {
              Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = ticket.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VendorColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                  )
                  StatusBadge(
                    text = ticket.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    tone = if (isResolved) StatusTone.SUCCESS else StatusTone.PRIMARY,
                    icon = if (isResolved) Icons.Rounded.MarkChatRead else Icons.Rounded.Forum
                  )
                }

                Text(
                  text = ticket.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = VendorColors.TextSecondary
                )

                if (ticket.adminResponse.isNotBlank()) {
                  Surface(
                    shape = VendorShapes.input,
                    color = VendorColors.SuccessGreenBg.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(
                      modifier = Modifier.padding(VendorSpacing.sm),
                      verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                      Text(
                        text = "Support update",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VendorColors.SuccessGreen
                      )
                      Text(
                        text = ticket.adminResponse,
                        style = MaterialTheme.typography.bodySmall,
                        color = VendorColors.TextPrimary
                      )
                    }
                  }
                }

                SecondaryButton(
                  text = if (isExpanded) "Close conversation" else "Open conversation",
                  icon = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.Forum,
                  onClick = { onToggleTicketConversation(ticket.id) },
                  modifier = Modifier.fillMaxWidth()
                )

                if (isExpanded) {
                  HorizontalDivider(color = VendorColors.Divider)
                  Text(
                    text = "Conversation",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = VendorColors.TextPrimary
                  )

                  if (uiState.selectedTicketMessages.isEmpty()) {
                    Text(
                      text = "No messages yet in this thread.",
                      style = MaterialTheme.typography.bodySmall,
                      color = VendorColors.TextSecondary
                    )
                  } else {
                    Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.xs)) {
                      uiState.selectedTicketMessages.forEach { message ->
                        val isSeller = message.senderRole == "seller"
                        Surface(
                          color = if (isSeller) VendorColors.PrimaryOrangeSoft else VendorColors.Background,
                          shape = RoundedCornerShape(12.dp),
                          border = BorderStroke(
                            1.dp,
                            if (isSeller) VendorColors.PrimaryOrangeBorder else VendorColors.BorderSubtle
                          ),
                          modifier = Modifier.fillMaxWidth()
                        ) {
                          Column(
                            modifier = Modifier.padding(VendorSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(VendorSpacing.xxs)
                          ) {
                            Text(
                              text = if (isSeller) "You" else "JTBazar Support",
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold,
                              color = if (isSeller) VendorColors.PrimaryOrange else VendorColors.NavyHeader
                            )
                            Text(
                              text = message.message,
                              style = MaterialTheme.typography.bodyMedium,
                              color = VendorColors.TextPrimary
                            )
                            if (message.createdAt.isNotBlank()) {
                              Text(
                                text = message.createdAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = VendorColors.TextSecondary
                              )
                            }
                          }
                        }
                      }
                    }
                  }

                  if (isResolved) {
                    VendorAlertBanner(
                      message = "This ticket is resolved. Create a new support ticket for another issue.",
                      isError = false
                    )
                  } else {
                    VendorOutlinedTextField(
                      value = supportReply,
                      onValueChange = onSupportReplyChange,
                      label = "Write your message",
                      leadingIcon = Icons.Rounded.ChatBubbleOutline,
                      minLines = 2
                    )
                    PrimaryButton(
                      text = "Send message",
                      icon = Icons.Rounded.Send,
                      enabled = !uiState.isSaving && supportReply.isNotBlank(),
                      loading = uiState.isSaving,
                      onClick = { onSendSupportMessage(ticket.id) },
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VendorCasesContentPreview() {
  VendorCenterTheme {
    VendorCasesContent(
      uiState = VendorCasesUiState(
        casesState = VendorSectionState.Empty,
        ticketsState = VendorSectionState.Content(
          listOf(
            VendorSupportTicketUiModel(
              id = "t1",
              subject = "Listing category update request",
              message = "Please move my ceramic platter set to Home & Kitchen.",
              status = "open",
              adminResponse = ""
            )
          )
        )
      ),
      caseReplies = emptyMap(),
      supportReply = "",
      onRefresh = {},
      onCaseReplyChange = { _, _ -> },
      onSendCaseResponse = {},
      onToggleTicketConversation = {},
      onSupportReplyChange = {},
      onSendSupportMessage = {},
      onOpenCreateTicket = {},
      onDismissAlert = {}
    )
  }
}
