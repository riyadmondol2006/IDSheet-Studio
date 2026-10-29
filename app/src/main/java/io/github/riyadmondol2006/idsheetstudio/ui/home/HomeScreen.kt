package io.github.riyadmondol2006.idsheetstudio.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.ProjectSummary
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.ui.appContainer
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.components.PhotoThumbnail
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val projects: StateFlow<List<ProjectSummary>?> = container.projects.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var creating = false

    /** Creates a sheet; repeated taps while one is being created are ignored. */
    fun create(onCreated: (String) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                onCreated(createSheet())
            } finally {
                creating = false
            }
        }
    }

    private suspend fun createSheet(): String {
        val prefs = container.settings.current()
        val name = "Sheet " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())
        val settings = SheetSettings(
            paperId = PaperSizes.byId(prefs.defaultPaperId)?.id ?: PaperSizes.A4.id,
            displayUnit = prefs.defaultUnit,
        )
        return container.projects.createProject(name, settings)
    }

    fun rename(id: String, name: String) = viewModelScope.launch { container.projects.rename(id, name) }
    fun duplicate(id: String) = viewModelScope.launch { container.projects.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { container.projects.delete(id) }
}

@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onExportProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("IDSheet Studio") },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) { Icon(Icons.Rounded.Settings, "Settings") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.create(onOpenProject) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New sheet") },
                modifier = Modifier.testTag("new_sheet"),
            )
        },
    ) { padding ->
        val list = projects
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> EmptyHome(Modifier.padding(padding), onCreate = { vm.create(onOpenProject) })
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize().testTag("project_grid"),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${list.size} saved ${if (list.size == 1) "sheet" else "sheets"} · stored on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(list, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onRename = { renaming = project },
                        onDuplicate = { vm.duplicate(project.id) },
                        onExport = { onExportProject(project.id) },
                        onDelete = { deleting = project },
                        modifier = Modifier.animateItem(fadeInSpec = ExpressiveMotion.effects(), placementSpec = ExpressiveMotion.offset, fadeOutSpec = ExpressiveMotion.effects()),
                    )
                }
            }
        }
    }

    renaming?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            icon = { Icon(Icons.Rounded.Edit, null) },
            title = { Text("Rename sheet") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.rename(project.id, name); renaming = null }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Delete sheet?") },
            text = { Text("\"${project.name}\" and its processed photos will be permanently deleted from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(project.id); deleting = null }, modifier = Modifier.testTag("confirm_delete")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = appContainer().renderer
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag("project_card"), shape = MaterialTheme.shapes.large) {
        Box {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                if (project.coverPhoto != null) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(18.dp)) {
                        val aspect = (project.coverPhoto.size.width / project.coverPhoto.size.height).toFloat()
                        Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.extraSmall) {
                            PhotoThumbnail(project.coverPhoto, renderer, Modifier.fillMaxHeight().aspectRatio(aspect))
                        }
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GridView, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("project_menu")) { Icon(Icons.Rounded.MoreVert, "Sheet options") }
                DropdownMenu(menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text("Export PDF") },
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                        enabled = project.copyCount > 0,
                        onClick = { menu = false; onExport() },
                    )
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${plural(project.photoCount, "photo")} · ${plural(project.copyCount, "copy", "copies")} · ${project.paperName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                updatedLabel(project.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onCreate: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Make your first photo sheet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Turn any portrait into passport, visa, ID or stamp photos, arranged on paper at their exact physical size.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = onCreate, modifier = Modifier.testTag("empty_new_sheet")) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Create a sheet")
                }
            }
        }
        Step(Icons.Rounded.AutoFixHigh, "Edit", "Crop, rotate and remove the background for a pure white finish.")
        Step(Icons.Rounded.GridView, "Arrange", "Pick sizes and quantities; the smart layout fills every page efficiently.")
        Step(Icons.Rounded.Print, "Print", "Export a precise multi-page PDF, then share, save or print it.")
    }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(4.dp).clip(MaterialTheme.shapes.small))
}

internal fun plural(count: Int, one: String, many: String = one + "s") = "$count ${if (count == 1) one else many}"

/** "Just now" for the last minute, then "5 minutes ago", "Yesterday", dates… */
private fun updatedLabel(time: Long): String {
    val now = System.currentTimeMillis()
    return if (now - time in 0 until DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
