package io.github.riyadmondol2006.idsheetstudio.ui.update

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.riyadmondol2006.idsheetstudio.BuildConfig

@Composable
fun UpdateDialog(state: UpdateUiState, onUpdate: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    val info = state.info ?: return
    val context = LocalContext.current
    val busy = state.phase is UpdatePhase.Downloading || state.phase == UpdatePhase.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        properties = DialogProperties(dismissOnClickOutside = !busy),
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update available") },
        modifier = Modifier.testTag("update_dialog"),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "IDSheet Studio ${info.version} is ready. You have ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
                        Text("What's new", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(4.dp))
                        Text(renderMarkdown(info.notes.ifBlank { "Bug fixes and improvements." }), style = MaterialTheme.typography.bodySmall)
                    }
                }
                AnimatedContent(state.phase, label = "phase") { phase ->
                    when (phase) {
                        is UpdatePhase.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Downloading… ${(phase.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                            LinearProgressIndicator(progress = { phase.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        UpdatePhase.Installing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Verified signature and checksum. Waiting for the installer…", style = MaterialTheme.typography.labelLarge)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        UpdatePhase.NeedsPermission -> Column {
                            Text(
                                "Allow IDSheet Studio to install updates, then come back and tap Update again.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                                )
                            }) { Text("Open settings") }
                        }
                        is UpdatePhase.Failed -> Column {
                            Text(phase.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, info.htmlUrl.toUri())) }) {
                                Text("Open release page")
                            }
                        }
                        UpdatePhase.Idle -> Row {}
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !busy, modifier = Modifier.testTag("update_confirm")) { Text("Update") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip, enabled = !busy) { Text("Skip version") }
                TextButton(onClick = onLater, modifier = Modifier.testTag("update_later")) { Text("Later") }
            }
        },
    )
}

/** Very small Markdown subset for release notes: headings, bullets and **bold**. */
fun renderMarkdown(text: String) = buildAnnotatedString {
    text.lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        val line = raw.trimEnd()
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.trimStart('#', ' ')) }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                append("  •  ")
                appendInline(line.trimStart().drop(2))
            }
            else -> appendInline(line)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(text: String) {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
