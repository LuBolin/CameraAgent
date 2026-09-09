package com.bolin.photohelper.gallery

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private val ThumbnailShape = RoundedCornerShape(6.dp)

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
                    TextButton(onClick = viewModel::beginSelection) {
                        Text("Select", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onPickPhotos) {
                    Text("Choose photos", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (state.galleryAccess != GalleryAccess.FULL && !state.galleryBannerDismissed) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.galleryAccess == GalleryAccess.PARTIAL) {
                                "Showing the photos you allowed."
                            } else {
                                "Allow gallery access to browse photos without leaving Photo Helper."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::dismissGalleryBanner) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRequestGalleryAccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(if (state.galleryAccess == GalleryAccess.PARTIAL) "Choose more" else "Allow access")
                        }
                        OutlinedButton(onClick = onPickPhotos) { Text("Use photo picker") }
                    }
                }
            }
        }
        if (state.selecting) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.selectedUris.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = viewModel::requestDeleteSelected,
                        enabled = state.selectedUris.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = viewModel::clearSelection) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { viewModel.openShare() },
                        enabled = state.selectedUris.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("Next") }
                }
            }
        }
        if (state.deleteConfirmationVisible) {
            val count = state.selectedUris.size
            AlertDialog(
                onDismissRequest = viewModel::dismissDeleteConfirmation,
                title = { Text("Delete $count photo${if (count > 1) "s" else ""}?") },
                text = { Text("This will permanently remove ${if (count > 1) "these photos" else "this photo"} from your device.") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDeleteSelected) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDeleteConfirmation) { Text("Cancel") }
                },
            )
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.visibleAssets.isEmpty() -> EmptyGallery(onPickPhotos)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .testTag(PhotoWorkflowTestTags.GALLERY),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(state.visibleAssets, key = LibraryAsset::uri) { asset ->
                    val selectedIndex = state.selectedUris.indexOf(asset.uri)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(ThumbnailShape)
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
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .25f))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, ThumbnailShape),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    "${selectedIndex + 1}",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
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
                            if (state.loadingMore) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Load more", color = MaterialTheme.colorScheme.primary)
                            }
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
        Icon(
            Icons.Rounded.PhotoLibrary,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("No readable photos", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPickPhotos) {
            Text("Choose photos", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ViewerScreen(state: PhotoWorkflowUiState, viewModel: PhotoWorkflowViewModel) {
    val asset = state.activeAsset ?: return
    var deleteConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(PhotoWorkflowTestTags.VIEWER)) {
        Header(asset.friendlyTitle(), viewModel::back) {
            IconButton(onClick = { deleteConfirmation = true }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete photo",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        ZoomableImage(asset.uri, Modifier.fillMaxWidth().weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = { viewModel.openShare(asset) }) { Text("Share") }
            Button(
                onClick = { viewModel.openEditor(asset) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("Edit with AI") }
        }
    }
    if (deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = false },
            title = { Text("Delete this photo?") },
            text = { Text("This will permanently remove the photo from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmation = false
                    viewModel.deleteActiveAsset()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorScreen(
    state: PhotoWorkflowUiState,
    viewModel: PhotoWorkflowViewModel,
    onVoiceInput: (VoiceInputTarget) -> Unit,
) {
    val session = state.editSession ?: return
    val isLoading = state.editStatus == RequestStatus.RUNNING

    // (#5) Auto-dismiss status messages after 3 seconds
    state.message?.let { message ->
        LaunchedEffect(message) {
            delay(3000L)
            viewModel.consumeMessage()
        }
    }

    // (#4) Shimmer alpha for loading state
    val shimmerAlpha by if (isLoading) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shimmerAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // (#6) Before/after comparison state
    var compareVariantUri by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag(PhotoWorkflowTestTags.EDITOR)) {
        Header("AI edit", viewModel::back)

        // (#1) Image area with framing — rounded clip, padding, subtle border
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            ZoomableImage(session.workingUri, Modifier.fillMaxSize())

            // (#4) Loading shimmer overlay
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Applying edit…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // (#6) Before/after overlay when long-pressing a variant chip
            compareVariantUri?.let { compareUri ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Before",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            FullImage(
                                session.original.uri,
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "After",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            FullImage(
                                compareUri,
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }

        // (#2) Variant chips with overlay styling + Mango save icon
        if (session.variants.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = session.workingVariantId == null,
                            onClick = {
                                compareVariantUri = null
                                viewModel.selectWorkingVariant(null)
                            },
                            label = { Text("Original") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    items(session.variants, key = EditVariant::id) { variant ->
                        val index = session.variants.indexOf(variant) + 1
                        FilterChip(
                            selected = session.workingVariantId == variant.id,
                            onClick = {
                                compareVariantUri = null
                                viewModel.selectWorkingVariant(variant.id)
                            },
                            label = { Text("Edit $index") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    compareVariantUri = null
                                    viewModel.selectWorkingVariant(variant.id)
                                },
                                onLongClick = {
                                    compareVariantUri = if (compareVariantUri == variant.uri) null else variant.uri
                                },
                            ),
                        )
                    }
                }
                if (session.workingVariantId != null) {
                    IconButton(onClick = viewModel::saveCurrentEdit) {
                        Icon(
                            Icons.Rounded.SaveAlt,
                            contentDescription = "Save to camera roll",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // (#3) Bottom sheet surface wrapping the input area
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = state.editInstruction,
                    onValueChange = viewModel::updateEditInstruction,
                    label = { Text("What should change?") },
                    supportingText = { Text("Only ask for the change you want. The original stays saved.") },
                    trailingIcon = {
                        VoiceInputButton(VoiceInputTarget.EDIT_INSTRUCTION, state, viewModel, onVoiceInput)
                    },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag(PhotoWorkflowTestTags.EDIT_INSTRUCTION),
                )

                // (#5) Snackbar-style status feedback
                AnimatedVisibility(
                    visible = state.message != null,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    state.message?.let { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if ("Saved" in message) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ) {
                            Text(
                                message,
                                style = MaterialTheme.typography.labelMedium,
                                color = if ("Saved" in message) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                // Full-width CTA button
                Button(
                    onClick = viewModel::requestEditConfirmation,
                    enabled = state.editInstruction.isNotBlank() && !isLoading &&
                        state.voiceInputTarget == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (state.editStatus == RequestStatus.RETRYABLE) "Retry edit" else "Generate edit",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
    if (state.editConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditConfirmation,
            title = { Text("Use AI to edit this photo?") },
            text = {
                Text(
                    "Photo Helper will send a reduced copy to an AI service for editing. " +
                        "AI can make unintended changes. Your original will not be overwritten. " +
                        "You can revoke this in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEdit) {
                    Text("Allow", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditConfirmation) { Text("Not now") }
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
    var captionExpanded by remember { mutableStateOf(state.captionDraft.isNotBlank()) }
    val hasGeneratedCaption = state.captionDraft.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .testTag(PhotoWorkflowTestTags.SHARE),
    ) {
        Header("Share ${assets.size} photo${if (assets.size == 1) "" else "s"}", viewModel::back)

        // Photo strip
        if (assets.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(assets.size, key = { assets[it].uri }) { index ->
                    val asset = assets[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            GalleryThumbnail(
                                asset,
                                viewModel.gallery,
                                Modifier.size(96.dp).clip(ThumbnailShape),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
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
        }

        // Share buttons — primary actions first
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onShare(assets, state.captionDraft) },
                enabled = assets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share…")
            }
            OutlinedButton(
                onClick = { onTelegram(assets, state.captionDraft) },
                enabled = assets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Telegram") }
            Text(
                "Use Share… for WeChat and other installed apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Caption section — collapsible, optional
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { captionExpanded = !captionExpanded }) {
                Text(
                    if (hasGeneratedCaption) "Caption" else "Add a caption",
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (captionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (captionExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(
            visible = captionExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptionLength.entries.forEach { length ->
                        FilterChip(
                            selected = state.captionLength == length,
                            onClick = { viewModel.setCaptionLength(length) },
                            label = { Text(if (length == CaptionLength.SHORT) "Short" else "Long") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
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
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(PhotoWorkflowTestTags.CAPTION),
                )
                // Show feedback field only after a caption has been generated
                if (hasGeneratedCaption) {
                    OutlinedTextField(
                        value = state.captionFeedback,
                        onValueChange = viewModel::updateCaptionFeedback,
                        label = { Text("What should be different?") },
                        placeholder = { Text("e.g. Don't mention the weather") },
                        trailingIcon = {
                            VoiceInputButton(VoiceInputTarget.CAPTION_FEEDBACK, state, viewModel, onVoiceInput)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.captionStatus == RequestStatus.RUNNING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Writing caption…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = viewModel::requestCaptionConfirmation,
                        enabled = state.captionStatus != RequestStatus.RUNNING && state.voiceInputTarget == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(if (hasGeneratedCaption) "Revise caption" else "Generate caption")
                    }
                }
                state.message?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (state.captionConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCaptionConfirmation,
            title = { Text("Use AI to write captions?") },
            text = {
                Text(
                    "Photo Helper will send a reduced, metadata-free contact sheet of your selected photos " +
                        "to an AI service for caption generation. You can revoke this in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCaption) {
                    Text("Allow", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCaptionConfirmation) { Text("Not now") }
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
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (listening) "Finish voice input" else "Dictate text",
        )
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}

@Composable
private fun GalleryThumbnail(asset: LibraryAsset, gallery: MediaStoreGallery, modifier: Modifier = Modifier) {
    val bitmap by produceState<Result<android.graphics.Bitmap>?>(null, asset.uri) {
        value = gallery.thumbnail(asset.uri, 320)
    }
    val image = bitmap?.getOrNull()
    if (image != null) {
        Image(
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
            if (bitmap == null) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LibraryAsset.friendlyTitle(): String {
    if (dateAddedSeconds > 0) {
        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return format.format(Date(dateAddedSeconds * 1000))
    }
    return "Photo"
}

@Composable
private fun ZoomableImage(uri: String, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        FullImage(
            uri = uri,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y,
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
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
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = "Selected photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            image == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            else -> Text("Photo unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
