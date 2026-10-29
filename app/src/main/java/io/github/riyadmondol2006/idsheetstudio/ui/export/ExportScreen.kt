package io.github.riyadmondol2006.idsheetstudio.ui.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.print.PrintManager
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.pdf.ExportResult
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfPrintAdapter
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.home.plural
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion

@Composable
fun ExportScreen(projectId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "export-$projectId") { c, _ -> ExportViewModel(c, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print-ready PDF") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = when {
                state.error != null -> 2
                state.result != null -> 1
                else -> 0
            },
            transitionSpec = { (fadeIn(ExpressiveMotion.effectsSlow()) + scaleIn(ExpressiveMotion.spatial(), initialScale = 0.96f)) togetherWith fadeOut() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "export",
        ) { phase ->
            when (phase) {
                0 -> Working(state.progress)
                2 -> Failure(state.error.orEmpty(), vm::export)
                else -> state.result?.let { result ->
                    Ready(
                        state = state,
                        result = result,
                        onShare = { share(context, result) },
                        onSave = { saver.launch(result.file.name) },
                        onPrint = { print(context, result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Working(progress: Float) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rendering photos at full resolution…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("export_progress"))
    }
}

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.testTag("export_error"))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun Ready(state: ExportState, result: ExportResult, onShare: () -> Unit, onSave: () -> Unit, onPrint: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().testTag("export_ready"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.projectName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    val mm = LengthUnit.MM
                    val inch = LengthUnit.INCH
                    Text(
                        "${mm.format(result.pageSize.width, false)} × ${mm.format(result.pageSize.height)}  (${inch.format(result.pageSize.width, false)} × ${inch.format(result.pageSize.height)})",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("export_page_size"),
                    )
                    Text(
                        "${plural(result.pageCount, "page")} · ${plural(result.copies, "photo")} · ${result.dpi} DPI · " +
                            Formatter.formatShortFileSize(context, state.fileSizeBytes),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("export_summary"),
                    )
                    Text(
                        "✓ Verified: ${result.verification.pagesChecked} page sizes and ${result.verification.photosChecked} photo sizes read back from the PDF match exactly.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("export_verified"),
                    )
                    if (result.missingCopies > 0) {
                        Text("${plural(result.missingCopies, "copy", "copies")} didn't fit and ${if (result.missingCopies == 1) "was" else "were"} left out.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPrint, modifier = Modifier.weight(1f).testTag("export_print")) {
                    Icon(Icons.Rounded.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print")
                }
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f).testTag("export_share")) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share")
                }
                FilledTonalButton(onClick = onSave, modifier = Modifier.weight(1f).testTag("export_save")) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save")
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Straighten, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "For exact sizes, print at 100% / \"Actual size\" with scaling and \"fit to page\" turned off.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (result.pageCount > state.pages.size) {
            item {
                Text(
                    "Previewing the first ${state.pages.size} of ${result.pageCount} pages. The PDF contains all pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(state.pages) { index, page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(shadowElevation = 6.dp, shape = MaterialTheme.shapes.extraSmall) {
                    Box {
                        Image(
                            page.asImageBitmap(),
                            contentDescription = "PDF page ${index + 1}",
                            modifier = Modifier.fillMaxWidth().aspectRatio(page.width.toFloat() / page.height),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Page ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun share(context: Context, result: ExportResult) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", result.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, result.file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun print(context: Context, result: ExportResult) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    manager.print(
        "IDSheet Studio – ${result.file.nameWithoutExtension}",
        PdfPrintAdapter(result.file, result.pageCount),
        PdfPrintAdapter.attributesFor(result.pageSize),
    )
}
