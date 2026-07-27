package com.gerwinkuijntjes.hours.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.ui.components.ClientDot
import com.gerwinkuijntjes.hours.ui.components.SectionHeader
import com.gerwinkuijntjes.hours.ui.components.onFocusLost
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.formatHours
import com.gerwinkuijntjes.hours.ui.parseNumber
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    viewModel: HoursViewModel,
    clientId: String,
    onBack: () -> Unit
) {
    val locale = currentLocale()
    val clientFlow = remember(clientId) { viewModel.clientFlowById(clientId) }
    val client by clientFlow.collectAsState()
    val counts by viewModel.visitCounts.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    val current = client
    if (current == null) {
        // The client was deleted from under us; nothing left to show.
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClientDot(current.color, size = 13)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(
                    text = pluralStringResource(R.plurals.visits_recorded, counts[current.id] ?: 0, counts[current.id] ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            TextSetting(
                label = stringResource(R.string.client_name),
                value = current.name,
                onCommit = { if (it.isNotBlank()) viewModel.updateClient(current.copy(name = it.trim())) }
            )
            NumberSetting(
                label = stringResource(R.string.client_rate),
                value = formatHours(current.rate, locale),
                onCommit = { viewModel.updateClient(current.copy(rate = it)) }
            )
            NumberSetting(
                label = stringResource(R.string.client_default_hours),
                value = formatHours(current.defaultHours, locale),
                onCommit = { viewModel.updateClient(current.copy(defaultHours = it)) }
            )
            NumberSetting(
                label = stringResource(R.string.client_extra),
                value = formatHours(current.extra, locale),
                onCommit = { viewModel.updateClient(current.copy(extra = it)) }
            )

            SectionHeader(stringResource(R.string.client_fixed_days))

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in current.days
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                shape
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .clickable { viewModel.toggleClientDay(current, day) }
                    ) {
                        Text(
                            text = day.getDisplayName(TextStyle.SHORT, locale).take(2),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.rate_change_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 32.dp)
            )
        }
    }

    if (confirmDelete) {
        val count = counts[current.id] ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_client_title, current.name)) },
            text = {
                Text(
                    if (count > 0) stringResource(R.string.delete_client_message, count)
                    else stringResource(R.string.delete_client_message_empty)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteClient(current)
                    onBack()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Settings fields keep their own draft text and only report upward when focus
 * leaves, so a half-typed "13," is never parsed and written back as 13.
 */
@Composable
private fun TextSetting(label: String, value: String, onCommit: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .onFocusLost { if (draft != value) onCommit(draft) }
    )
}

@Composable
private fun NumberSetting(label: String, value: String, onCommit: (Double) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .onFocusLost { if (draft != value) parseNumber(draft)?.let(onCommit) }
    )
}
