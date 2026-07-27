package com.gerwinkuijntjes.hours.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.backup.DavError
import com.gerwinkuijntjes.hours.backup.DavFolder
import com.gerwinkuijntjes.hours.backup.davError
import com.gerwinkuijntjes.hours.ui.davErrorText
import kotlinx.coroutines.launch

/**
 * Browse the folders on the server and pick one, or make a new one.
 *
 * Typing a path by hand is how the first attempt failed: a folder that did not
 * exist, with nothing to say so. Here the server itself says what is there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    viewModel: HoursViewModel,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(viewModel.folderPath.value) }
    var folders by remember { mutableStateOf<List<DavFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<DavError?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            viewModel.listFolders(path).fold(
                onSuccess = { folders = it },
                onFailure = { folders = emptyList(); error = it.davError() }
            )
            loading = false
        }
    }

    LaunchedEffect(path) { load() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_folder)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.new_folder)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = path.ifEmpty { stringResource(R.string.backup_folder_root) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            when {
                loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) { CircularProgressIndicator() }

                error != null -> Text(
                    text = davErrorText(error!!),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(18.dp)
                )

                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    if (path.isNotEmpty()) {
                        item {
                            ListItem(
                                headlineContent = { Text("..") },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.up_one_level)
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier.clickable {
                                    path = path.substringBeforeLast('/', "")
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    if (folders.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.folder_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }

                    items(folders, key = { it.path }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.clickable { path = folder.path }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(18.dp)
            ) {
                Button(
                    onClick = { onPick(path) },
                    enabled = error == null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(stringResource(R.string.use_this_folder)) }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf<DavError?>(null) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; createError = null },
                        label = { Text(stringResource(R.string.new_folder_name)) },
                        singleLine = true,
                        isError = createError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    createError?.let {
                        Text(
                            text = davErrorText(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.createFolder(path, name).fold(
                                onSuccess = {
                                    creating = false
                                    path = "$path/${name.trim()}"
                                },
                                onFailure = { createError = it.davError() }
                            )
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
