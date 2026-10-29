package io.github.riyadmondol2006.idsheetstudio.ui.settings

import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.riyadmondol2006.idsheetstudio.BuildConfig
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.formatDecimal
import io.github.riyadmondol2006.idsheetstudio.data.settings.AppSettings
import io.github.riyadmondol2006.idsheetstudio.data.settings.RemoveBgSize
import io.github.riyadmondol2006.idsheetstudio.data.settings.ThemeMode
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import io.github.riyadmondol2006.idsheetstudio.ui.appContainer
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.components.SectionCard
import io.github.riyadmondol2006.idsheetstudio.ui.components.SegmentedChoice
import io.github.riyadmondol2006.idsheetstudio.ui.components.SwitchRow
import io.github.riyadmondol2006.idsheetstudio.ui.onboarding.ApiKeyViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.onboarding.REMOVE_BG_API_PAGE
import kotlinx.coroutines.launch

const val REPO_URL = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    checkingUpdates: Boolean,
) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val keyVm = appViewModel { c, _ -> ApiKeyViewModel(c) }
    val keyState by keyVm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newKey by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("settings_list"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("remove.bg API key", icon = Icons.Rounded.Key, subtitle = "Used only for background removal") {
                if (keyState.savedKeyPresent && !editingKey) {
                    Text("Saved key: ${keyState.savedKeyHint}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("saved_key_hint"))
                    keyState.account?.let {
                        Text(
                            "Credits: ${formatDecimal(it.totalCredits, 2)} · Free preview calls: ${it.freeCalls}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("api_credits"),
                        )
                    }
                    keyState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = keyVm::checkSaved, enabled = !keyState.verifying, modifier = Modifier.testTag("check_key")) {
                            if (keyState.verifying) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Check balance")
                        }
                        OutlinedButton(onClick = { editingKey = true; newKey = "" }, modifier = Modifier.testTag("change_key")) { Text("Change") }
                        TextButton(onClick = keyVm::removeKey) { Text("Remove") }
                    }
                } else {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it.trim() },
                        label = { Text("API key") },
                        singleLine = true,
                        isError = keyState.error != null,
                        supportingText = keyState.error?.let { { Text(it) } },
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "Hide key" else "Show key")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_api_key_field"),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { keyVm.verifyAndSave(newKey) { editingKey = false; newKey = "" } },
                            enabled = newKey.isNotBlank() && !keyState.verifying,
                            modifier = Modifier.testTag("settings_api_key_save"),
                        ) { Text(if (keyState.verifying) "Verifying…" else "Verify & save") }
                        if (editingKey) TextButton(onClick = { editingKey = false }) { Text("Cancel") }
                    }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, REMOVE_BG_API_PAGE.toUri())) }) {
                        Text("Get a free API key")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
                Text("Result quality", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    RemoveBgSize.entries.forEach { size ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = settings.removeBgSize == size,
                                    onClick = { scope.launch { container.settings.setRemoveBgSize(size) } },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.removeBgSize == size, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(size.title, style = MaterialTheme.typography.bodyLarge)
                                Text(size.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            SectionCard("Appearance", icon = Icons.Rounded.Palette) {
                SegmentedChoice(
                    ThemeMode.entries,
                    settings.themeMode,
                    { m -> scope.launch { container.settings.setThemeMode(m) } },
                    { when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
                )
                SwitchRow(
                    "Dynamic colour",
                    settings.dynamicColor,
                    { e -> scope.launch { container.settings.setDynamicColor(e) } },
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Match your wallpaper colours" else "Requires Android 12 or newer",
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
            }

            SectionCard("New sheets & export", icon = Icons.Rounded.Print) {
                Text("Default unit", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(LengthUnit.entries, settings.defaultUnit, { u -> scope.launch { container.settings.setDefaultUnit(u) } }, { it.symbol })
                Text("Default paper", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperSizes.all.forEach { paper ->
                        FilterChip(settings.defaultPaperId == paper.id, { scope.launch { container.settings.setDefaultPaper(paper.id) } }, label = { Text(paper.name) })
                    }
                }
                Text("Default photo size", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("passport_35x45", "passport_us", "stamp_20x25", "id_25x35", "country_bd", "passport_ca").forEach { id ->
                        val preset = PhotoSizePresets.byId(id) ?: return@forEach
                        FilterChip(settings.defaultPresetId == id, { scope.launch { container.settings.setDefaultPreset(id) } }, label = { Text(preset.name) })
                    }
                }
                Text("PDF resolution", style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(listOf(300, 450, 600), settings.exportDpi, { d -> scope.launch { container.settings.setExportDpi(d) } }, { "$it DPI" })
            }

            SectionCard("Updates", icon = Icons.Rounded.SystemUpdate, subtitle = "Installed version ${BuildConfig.VERSION_NAME}") {
                SwitchRow(
                    "Check automatically",
                    settings.autoUpdateCheck,
                    { e -> scope.launch { container.settings.setAutoUpdateCheck(e) } },
                    subtitle = "Checks GitHub for new releases when you open the app and every few hours in the background, then shows an update popup",
                )
                FilledTonalButton(onClick = onCheckUpdates, enabled = !checkingUpdates, modifier = Modifier.testTag("check_updates")) {
                    if (checkingUpdates) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Check for updates")
                }
            }

            SectionCard("About", icon = Icons.Rounded.Info) {
                Text(
                    "IDSheet Studio is open source. Photos and projects stay on this device; images are only sent to remove.bg when you ask for background removal.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri())) }) {
                    Text("Source code on GitHub")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                }
            }
        }
    }
}
