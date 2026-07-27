package com.gerwinkuijntjes.hours.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.data.Client
import com.gerwinkuijntjes.hours.ui.components.ClientDot
import com.gerwinkuijntjes.hours.ui.components.SectionHeader
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.formatMoney
import com.gerwinkuijntjes.hours.ui.shortDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HoursViewModel,
    contentPadding: PaddingValues,
    onOpenClient: (String) -> Unit,
    onOpenBackup: () -> Unit
) {
    val locale = currentLocale()
    val clients by viewModel.clients.collectAsState()
    val counts by viewModel.visitCounts.collectAsState()
    val lastBackup by viewModel.lastBackupMillis.collectAsState()

    var addingClient by remember { mutableStateOf(false) }

    // A newly added client only has a name; send the user straight to its details
    // so the rate and days can be filled in while they are still thinking about it.
    val newClientId by viewModel.openClientId.collectAsState()
    LaunchedEffect(newClientId) {
        newClientId?.let {
            viewModel.consumeOpenClient()
            onOpenClient(it)
        }
    }
    var confirmErase by remember { mutableStateOf(false) }

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        ) {
            item { SectionHeader(stringResource(R.string.settings_clients), Modifier.padding(horizontal = 18.dp)) }

            items(clients, key = { it.id }) { client ->
                ListItem(
                    headlineContent = { Text(client.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = {
                        Text(clientSummary(client, locale), style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingContent = { ClientDot(client.color, size = 13) },
                    trailingContent = {
                        Text(
                            text = pluralStringResource(R.plurals.visits_recorded, counts[client.id] ?: 0, counts[client.id] ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.clickable { onOpenClient(client.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.add_client)) },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        headlineColor = MaterialTheme.colorScheme.primary,
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.clickable { addingClient = true }
                )
            }

            item { SectionHeader(stringResource(R.string.backup_section), Modifier.padding(horizontal = 18.dp)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_section)) },
                    supportingContent = {
                        Text(
                            if (lastBackup == 0L) stringResource(R.string.backup_subtitle_never)
                            else stringResource(R.string.backup_subtitle_ok, shortDate(lastBackup, locale))
                        )
                    },
                    leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.clickable { onOpenBackup() }
                )
            }

            item { SectionHeader(stringResource(R.string.erase_section), Modifier.padding(horizontal = 18.dp)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.erase_all)) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        headlineColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.clickable { confirmErase = true }
                )
            }
        }
    }

    if (addingClient) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingClient = false },
            title = { Text(stringResource(R.string.add_client)) },
            text = {
                Column {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.new_client_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addClient(name)
                        addingClient = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { addingClient = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eraseAll()
                    confirmErase = false
                }) {
                    Text(
                        stringResource(R.string.erase_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun clientSummary(client: Client, locale: Locale): String {
    val rate = formatMoney(client.rate, locale)
    val days = client.days.sortedBy { it.value }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    return if (days.isEmpty()) {
        stringResource(R.string.client_summary_no_days, rate)
    } else {
        stringResource(R.string.client_summary_days, rate, days)
    }
}
