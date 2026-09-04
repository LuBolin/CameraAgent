package com.bolin.photohelper.gallery

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoWorkflowTestTags {
    const val ROOT = "photo_workflow"
    const val GALLERY = "gallery_grid"
    const val VIEWER = "photo_viewer"
    const val EDITOR = "photo_editor"
    const val SHARE = "photo_share"
    const val EDIT_INSTRUCTION = "edit_instruction"
    const val CAPTION = "caption_draft"
}

@Composable
fun PhotoWorkflowScreen(
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onRequestGalleryAccess: () -> Unit,
    onPickPhotos: () -> Unit,
    onShare: (List<LibraryAsset>, String) -> Unit,
    onTelegram: (List<LibraryAsset>, String) -> Unit,
    onVoiceInput: (VoiceInputTarget) -> Unit,
) {
    BackHandler { viewModel.back() }
    Surface(
        modifier = Modifier.fillMaxSize().testTag(PhotoWorkflowTestTags.ROOT),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state.destination) {
            PhotoDestination.CAMERA -> Unit
            PhotoDestination.GALLERY -> GalleryScreen(state, viewModel, onRequestGalleryAccess, onPickPhotos)
            PhotoDestination.VIEWER -> ViewerScreen(state, viewModel)
            PhotoDestination.EDITOR -> EditorScreen(state, viewModel, onVoiceInput)
            PhotoDestination.SHARE -> ShareScreen(state, viewModel, onShare, onTelegram, onVoiceInput)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onRequestGalleryAccess: () -> Unit,
    onPickPhotos: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header("Gallery", viewModel::back) {
            Row {
                if (!state.selecting && state.visibleAssets.isNotEmpty()) {
                    TextButton(onClick = viewModel::beginSelection) { Text("Select") }
                }
                TextButton(onClick = onPickPhotos) { Text("Choose photos") }
            }
        }
        if (state.galleryAccess != GalleryAccess.FULL) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (state.galleryAccess == GalleryAccess.PARTIAL) {
                            "Showing the photos you allowed."
                        } else {
                            "Allow gallery access to browse photos without leaving Photo Helper."
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestGalleryAccess) {
                            Text(if (state.galleryAccess == GalleryAccess.PARTIAL) "Choose more" else "Allow access")
                        }
                        OutlinedButton(onClick = onPickPhotos) { Text("Use photo picker") }
                    }
                }
            }
        }
        if (state.selecting) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.selectedUris.size} selected", modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearSelection) { Text("Cancel") }
                Button(onClick = { viewModel.openShare() }, enabled = state.selectedUris.isNotEmpty()) { Text("Next") }
            }
        }
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.visibleAssets.isEmpty() -> EmptyGallery(onPickPhotos)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize().testTag(PhotoWorkflowTestTags.GALLERY),
            ) {
                items(state.visibleAssets, key = LibraryAsset::uri) { asset ->
                    val selectedIndex = state.selectedUris.indexOf(asset.uri)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .combinedClickable(
                                onClick = {
                                    if (state.selecting) viewModel.toggleSelection(asset) else viewModel.openViewer(asset)
                                },
                                onLongClick = { viewModel.startSelection(asset) },
                            ),
                    ) {
                        GalleryThumbnail(asset, viewModel.gallery, Modifier.fillMaxSize())
                        if (selectedIndex >= 0) {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .28f)),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${selectedIndex + 1}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                if (state.nextCursor != null) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        TextButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Load more")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(onPickPhotos: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No readable photos", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPickPhotos) { Text("Choose photos") }
    }
}

@Composable
private fun ViewerScreen(state: PhotoWorkflowUiState, viewModel: PhotoWorkflowViewModel) {
    val asset = state.activeAsset ?: return
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(PhotoWorkflowTestTags.VIEWER)) {
        Header(asset.displayName.ifBlank { "Photo" }, viewModel::back)
        FullImage(asset.uri, Modifier.fillMaxWidth().weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = { viewModel.openShare(asset) }) { Text("Share") }
            Button(onClick = { viewModel.openEditor(asset) }) { Text("Edit with AI") }
        }
    }
}

@Composable
private fun EditorScreen(
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onVoiceInput: (VoiceInputTarget) -> Unit,
) {
    val session = state.editSession ?: return
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(PhotoWorkflowTestTags.EDITOR)) {
        Header("AI edit", viewModel::back)
        FullImage(session.workingUri, Modifier.fillMaxWidth().weight(1f))
        if (session.variants.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = session.workingVariantId == null,
                        onClick = { viewModel.selectWorkingVariant(null) },
                        label = { Text("Original") },
                    )
                }
                items(session.variants, key = EditVariant::id) { variant ->
                    FilterChip(
                        selected = session.workingVariantId == variant.id,
                        onClick = { viewModel.selectWorkingVariant(variant.id) },
                        label = { Text("Edit ${session.variants.indexOf(variant) + 1}") },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.editInstruction,
            onValueChange = viewModel::updateEditInstruction,
            label = { Text("What should change?") },
            supportingText = { Text("Only ask for the change you want. The original stays saved.") },
            trailingIcon = {
                VoiceInputButton(VoiceInputTarget.EDIT_INSTRUCTION, state, viewModel, onVoiceInput)
            },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(PhotoWorkflowTestTags.EDIT_INSTRUCTION),
        )
        state.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Button(
            onClick = viewModel::requestEditConfirmation,
            enabled = state.editInstruction.isNotBlank() && state.editStatus != RequestStatus.RUNNING &&
                state.voiceInputTarget == null,
            modifier = Modifier.align(Alignment.End).padding(16.dp),
        ) {
            if (state.editStatus == RequestStatus.RUNNING) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                Text(if (state.editStatus == RequestStatus.RETRYABLE) "Retry edit" else "Generate edit")
            }
        }
    }
    if (state.editConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditConfirmation,
            title = { Text("Use AI to edit this photo?") },
            text = {
                Text(
                    if (session.workingVariantId == null) {
                        "Photo Helper will send a reduced, metadata-free copy to Alibaba Cloud in China. " +
                            "AI can make unintended changes. Your original will not be overwritten."
                    } else {
                        "Photo Helper will send the original and current edit to Alibaba Cloud in China. " +
                            "AI can make unintended changes. Neither saved photo will be overwritten."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEdit) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditConfirmation) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ShareScreen(
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onShare: (List<LibraryAsset>, String) -> Unit,
    onTelegram: (List<LibraryAsset>, String) -> Unit,
    onVoiceInput: (VoiceInputTarget) -> Unit,
) {
    val assets = state.selectedAssets
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .testTag(PhotoWorkflowTestTags.SHARE),
    ) {
        Header("Share ${assets.size} photo${if (assets.size == 1) "" else "s"}", viewModel::back)
        LazyRow(
            Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(assets.size, key = { assets[it].uri }) { index ->
                val asset = assets[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        GalleryThumbnail(asset, viewModel.gallery, Modifier.size(96.dp))
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text("${index + 1}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                    Row {
                        IconButton(
                            onClick = { viewModel.moveSelected(asset.uri, -1) },
                            enabled = index > 0,
                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Move photo earlier") }
                        IconButton(onClick = { viewModel.removeSelected(asset.uri) }) {
                            Icon(Icons.Rounded.Close, "Remove photo")
                        }
                        IconButton(
                            onClick = { viewModel.moveSelected(asset.uri, 1) },
                            enabled = index < assets.lastIndex,
                        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Move photo later") }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionLength.entries.forEach { length ->
                FilterChip(
                    selected = state.captionLength == length,
                    onClick = { viewModel.setCaptionLength(length) },
                    label = { Text(if (length == CaptionLength.SHORT) "Short caption" else "Long caption") },
                )
            }
        }
        OutlinedTextField(
            value = state.captionDraft,
            onValueChange = viewModel::updateCaptionDraft,
            label = { Text("Caption") },
            supportingText = {
                Text("${state.captionDraft.codePointCount(0, state.captionDraft.length)}/${state.captionLength.maxCodePoints}")
            },
            trailingIcon = {
                VoiceInputButton(VoiceInputTarget.CAPTION_DRAFT, state, viewModel, onVoiceInput)
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(PhotoWorkflowTestTags.CAPTION),
        )
        OutlinedTextField(
            value = state.captionFeedback,
            onValueChange = viewModel::updateCaptionFeedback,
            label = { Text("Caption feedback") },
            placeholder = { Text("Don't mention the weather") },
            trailingIcon = {
                VoiceInputButton(VoiceInputTarget.CAPTION_FEEDBACK, state, viewModel, onVoiceInput)
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.captionStatus == RequestStatus.RUNNING) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = viewModel::requestCaptionConfirmation,
                enabled = state.captionStatus != RequestStatus.RUNNING && state.voiceInputTarget == null,
            ) {
                Text(if (state.captionDraft.isBlank()) "Generate caption" else "Revise caption")
            }
        }
        state.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onShare(assets, state.captionDraft) },
                enabled = assets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share...")
            }
            OutlinedButton(
                onClick = { onTelegram(assets, state.captionDraft) },
                enabled = assets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Telegram") }
            Text(
                "Use Share… for WeChat and other installed apps.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (state.captionConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCaptionConfirmation,
            title = { Text("Use AI to write this caption?") },
            text = {
                Text(
                    "Photo Helper will send a reduced, metadata-free contact sheet of ${assets.size} photo" +
                        if (assets.size == 1) " to Alibaba Cloud in China." else "s to Alibaba Cloud in China.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCaption) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCaptionConfirmation) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun VoiceInputButton(
    target: VoiceInputTarget,
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onVoiceInput: (VoiceInputTarget) -> Unit,
) {
    val listening = state.voiceInputTarget == target
    IconButton(
        onClick = { if (listening) viewModel.finishVoiceInput() else onVoiceInput(target) },
        enabled = state.voiceInputTarget == null || listening,
    ) {
        Icon(
            imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (listening) "Finish voice input" else "Dictate text",
        )
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun GalleryThumbnail(asset: LibraryAsset, gallery: MediaStoreGallery, modifier: Modifier = Modifier) {
    val bitmap by produceState<Result<android.graphics.Bitmap>?>(null, asset.uri) {
        value = gallery.thumbnail(asset.uri, 320)
    }
    val image = bitmap?.getOrNull()
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(),
            contentDescription = asset.displayName.ifBlank { "Photo" },
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant).semantics {
                contentDescription = if (bitmap == null) "Loading photo" else "Photo unavailable"
            },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) CircularProgressIndicator(Modifier.size(20.dp))
            else Icon(Icons.Rounded.PhotoLibrary, null)
        }
    }
}

@Composable
private fun FullImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<Result<ImageBitmap>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val sourceLongEdge = maxOf(info.size.width, info.size.height)
                    if (sourceLongEdge > 1600) {
                        decoder.setTargetSize(
                            maxOf(1, info.size.width * 1600 / sourceLongEdge),
                            maxOf(1, info.size.height * 1600 / sourceLongEdge),
                        )
                    }
                }.asImageBitmap()
            }
        }
    }
    val bitmap = image?.getOrNull()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = "Selected photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            image == null -> CircularProgressIndicator()
            else -> Text("Photo unavailable")
        }
    }
}
