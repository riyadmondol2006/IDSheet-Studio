package io.github.riyadmondol2006.idsheetstudio.ui.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class ProjectTab(val title: String) { PHOTOS("Photos"), PAPER("Paper"), LAYOUT("Layout") }

@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    onEditPhoto: (PhotoItem) -> Unit,
    onExport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel(key = "project-$projectId") { c, _ -> ProjectViewModel(c, projectId) }
    val project by vm.project.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ProjectTab.PHOTOS) }
    var renaming by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)) { uris ->
        vm.importPhotos(uris)
    }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val needsKey = message.contains("API key", ignoreCase = true)
            val result = snackbar.showSnackbar(message, actionLabel = if (needsKey) "Settings" else null, withDismissAction = !needsKey)
            if (needsKey && result == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenSettings()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            project?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("project_title"),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename sheet") }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    ProjectTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.title) },
                            icon = {
                                Icon(
                                    when (t) {
                                        ProjectTab.PHOTOS -> Icons.Rounded.PhotoLibrary
                                        ProjectTab.PAPER -> Icons.Rounded.Description
                                        ProjectTab.LAYOUT -> Icons.Rounded.Dashboard
                                    },
                                    null,
                                )
                            },
                            modifier = Modifier.testTag("tab_${t.name.lowercase()}"),
                        )
                    }
                }
                AnimatedVisibility(importing) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (scaleIn(ExpressiveMotion.spatialFast()) + fadeIn()) togetherWith (scaleOut(ExpressiveMotion.effects()) + fadeOut())
                },
                label = "fab",
            ) { current ->
                when (current) {
                    ProjectTab.PHOTOS -> ExtendedFloatingActionButton(
                        onClick = pickPhotos,
                        icon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                        text = { Text("Add photos") },
                        modifier = Modifier.testTag("fab_add_photos"),
                    )
                    else -> if ((project?.totalCopies ?: 0) > 0) {
                        ExtendedFloatingActionButton(
                            // Navigation must happen on the main thread: after flush() (a Room write) the
                            // coroutine could otherwise resume on a database thread under some dispatchers.
                            onClick = { scope.launch(Dispatchers.Main.immediate) { vm.flush(); onExport() } },
                            icon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                            text = { Text("Export PDF") },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.testTag("fab_export"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val p = project
            val s = settings
            if (p != null && s != null) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(ExpressiveMotion.effectsSlow()) togetherWith fadeOut(ExpressiveMotion.effects()) },
                    label = "tab",
                ) { current ->
                    when (current) {
                        ProjectTab.PHOTOS -> PhotosTab(
                            project = p,
                            preview = preview,
                            onAdd = pickPhotos,
                            onEdit = onEditPhoto,
                            onQuantity = vm::setQuantity,
                            onDuplicate = vm::duplicate,
                            onDelete = vm::delete,
                            onRemoveBackground = { vm.removeBackground(listOf(it)) },
                            onRemoveAllBackgrounds = { vm.removeBackground(p.photos.filter { ph -> ph.cutoutPath == null }) },
                        )
                        ProjectTab.PAPER -> PaperTab(settings = s, onChange = vm::updateSettings)
                        ProjectTab.LAYOUT -> LayoutTab(
                            project = p,
                            settings = s,
                            preview = preview,
                            onMove = vm::move,
                            onResetPositions = vm::resetManualPositions,
                            onChange = vm::updateSettings,
                            onGoToPhotos = { tab = ProjectTab.PHOTOS },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(project?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            icon = { Icon(Icons.Rounded.Edit, null) },
            title = { Text("Rename sheet") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.testTag("rename_field"))
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(name); renaming = false }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
