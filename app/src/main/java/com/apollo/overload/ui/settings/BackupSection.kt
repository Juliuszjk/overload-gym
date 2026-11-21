package com.apollo.overload.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollo.overload.data.Repository
import com.apollo.overload.ui.common.GhostButton
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Export / import of the whole database as one JSON file (plans, workouts, custom exercises). */
@Composable
fun BackupSection(repo: Repository, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val openWorkout by remember { repo.dao.openWorkout() }.collectAsStateWithLifecycle(null)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val text = repo.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                        ?: error("Can't open the file")
                }
            }.fold({ "Backup saved." }, { "Export failed: ${it.message}" })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Backup", style = Type.display(17.sp, 800, tracking = -0.01f), color = p.ink)
        Text(
            "Save all plans, workouts and custom exercises to a file, or restore them from one.",
            style = Type.small, color = p.mute,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton({ exportLauncher.launch("overload-backup-${LocalDate.now()}.json") }, Modifier.weight(1f)) {
                Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                Text("Export backup", Modifier.padding(start = 6.dp), style = Type.label)
            }
            GhostButton({ importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.weight(1f)) {
                Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp))
                Text("Import backup", Modifier.padding(start = 6.dp), style = Type.label)
            }
        }
        status?.let { Text(it, style = Type.small, color = p.ink2) }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null }, containerColor = p.raise, titleContentColor = p.ink, textContentColor = p.ink2,
            title = { Text("Import backup?", style = Type.display(20.sp, 800)) },
            text = {
                Text(
                    "Replaces all plans and workouts with the ones in the file." +
                        (if (openWorkout != null) " The workout in progress will be lost too." else "") + " This can't be undone.",
                    style = Type.body,
                )
            },
            confirmButton = {
                TextButton({
                    pendingImport = null
                    scope.launch {
                        status = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                                    ?: error("Can't open the file")
                            }
                            repo.importJson(text)
                        }.fold({ "Backup imported." }, { "Import failed: ${it.message}" })
                    }
                }) { Text("Import", style = Type.label, color = p.ink) }
            },
            dismissButton = { TextButton({ pendingImport = null }) { Text("Cancel", style = Type.label, color = p.mute) } },
        )
    }
}
