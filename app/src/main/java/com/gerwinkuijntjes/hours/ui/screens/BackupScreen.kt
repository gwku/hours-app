package com.gerwinkuijntjes.hours.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gerwinkuijntjes.hours.HoursViewModel
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.backup.BackupSettings
import com.gerwinkuijntjes.hours.backup.DavError
import com.gerwinkuijntjes.hours.backup.davError
import com.gerwinkuijntjes.hours.ui.components.SectionHeader
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.davErrorText
import com.gerwinkuijntjes.hours.ui.longDateTime
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: HoursViewModel,
    onOpenFolderPicker: () -> Unit,
    onBack: () -> Unit
) {
    val locale = currentLocale()
    val scope = rememberCoroutineScope()

    val running by viewModel.backupInProgress.collectAsState()
    val lastBackup by viewModel.lastBackupMillis.collectAsState()
    val problem by viewModel.backupProblem.collectAsState()
    val lastError by viewModel.lastBackupError.collectAsState()
    val server by viewModel.serverUrl.collectAsState()
    val folder by viewModel.folderPath.collectAsState()
    val user by viewModel.backupUsername.collectAsState()
    val password by viewModel.backupPassword.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<DavError?>(null) }
    var testOk by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_section)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
            Text(
                text = stringResource(R.string.backup_explanation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = server,
                onValueChange = { viewModel.setServerUrl(it); testOk = false; testResult = null },
                label = { Text(stringResource(R.string.backup_server_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            OutlinedTextField(
                value = user,
                onValueChange = { viewModel.setBackupUsername(it); testOk = false; testResult = null },
                label = { Text(stringResource(R.string.backup_username)) },
                singleLine = true,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.setBackupPassword(it); testOk = false; testResult = null },
                label = { Text(stringResource(R.string.backup_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text(
                text = stringResource(R.string.backup_folder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )

            // The folder is browsed rather than typed: a path that does not exist
            // was the very first thing to go wrong here.
            OutlinedButton(
                onClick = onOpenFolderPicker,
                enabled = problem == BackupSettings.Problem.NONE ||
                    problem == BackupSettings.Problem.NO_FOLDER,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 14.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text(folder.ifEmpty { stringResource(R.string.backup_folder_root) })
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            testing = true; testResult = null; testOk = false
                            viewModel.webDavClient().checkConnection().fold(
                                onSuccess = { testOk = true },
                                onFailure = { testResult = it.davError() }
                            )
                            testing = false
                        }
                    },
                    enabled = !testing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.test_connection))
                    }
                }
            }

            when {
                testOk -> StatusLine(stringResource(R.string.connection_ok), isError = false)
                testResult != null -> StatusLine(davErrorText(testResult!!), isError = true)
            }

            Text(
                text = when {
                    problem != BackupSettings.Problem.NONE -> stringResource(
                        when (problem) {
                            BackupSettings.Problem.NO_FOLDER -> R.string.backup_needs_folder
                            BackupSettings.Problem.NOT_SECURE -> R.string.backup_needs_https
                            else -> R.string.backup_needs_login
                        }
                    )
                    lastBackup == 0L -> stringResource(R.string.backup_never)
                    else -> stringResource(
                        R.string.backup_last_success,
                        longDateTime(lastBackup, locale)
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 18.dp)
            )

            // Almost every upload happens in the background, so the reason the last
            // one failed has to survive until somebody comes to look at it.
            if (lastError.isNotEmpty()) {
                StatusLine(stringResource(R.string.last_error, lastError), isError = true)
            }

            Button(
                onClick = viewModel::backupNow,
                enabled = !running,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 10.dp)
            ) {
                if (running) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.backup_running))
                } else {
                    Text(stringResource(R.string.backup_now))
                }
            }

            SectionHeader(stringResource(R.string.save_to_file))

            OutlinedButton(
                onClick = { exportLauncher.launch("hours-backup-${LocalDate.now()}.json") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(stringResource(R.string.save_to_file)) }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp)
            ) { Text(stringResource(R.string.restore_from_file)) }

            Spacer(Modifier.height(32.dp))
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFrom(uri)
                    pendingImport = null
                }) { Text(stringResource(R.string.restore_from_file)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StatusLine(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}
