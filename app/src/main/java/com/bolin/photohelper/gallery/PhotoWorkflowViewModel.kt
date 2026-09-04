package com.bolin.photohelper.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bolin.photohelper.visual.BailianImageEditClient
import com.bolin.photohelper.visual.BailianVisualClient
import com.bolin.photohelper.visual.CaptionRequest
import com.bolin.photohelper.visual.CaptionResult
import com.bolin.photohelper.visual.ImageEditRequest
import com.bolin.photohelper.visual.ImageEditResult
import com.bolin.photohelper.voice.VoiceIo
import com.bolin.photohelper.voice.VoiceResult
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PhotoWorkflowViewModel(
    val gallery: MediaStoreGallery,
    private val imageEditor: BailianImageEditClient,
    private val captionClient: BailianVisualClient,
    private val loadQwenKey: () -> CharArray?,
    private val voice: VoiceIo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoWorkflowUiState())
    val uiState: StateFlow<PhotoWorkflowUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var editJob: Job? = null
    private var captionJob: Job? = null
    private var voiceJob: Job? = null
    private var operationToken = 0L

    fun refreshAccess(access: GalleryAccess) {
        _uiState.update { it.copy(galleryAccess = access) }
        refresh()
    }

    fun openGallery(access: GalleryAccess) {
        _uiState.update {
            it.copy(
                destination = PhotoDestination.GALLERY,
                galleryAccess = access,
                activeAsset = null,
                selectedUris = emptyList(),
                selectionMode = false,
                message = null,
            )
        }
        refresh()
    }

    fun openViewer(asset: LibraryAsset) {
        _uiState.update { it.copy(destination = PhotoDestination.VIEWER, activeAsset = asset, message = null) }
    }

    fun openEditor(asset: LibraryAsset? = _uiState.value.activeAsset) {
        val selected = asset ?: return
        val current = _uiState.value.editSession
        val session = current?.takeIf { it.original.uri == selected.uri } ?: EditSession(selected)
        _uiState.update {
            it.copy(
                destination = PhotoDestination.EDITOR,
                activeAsset = selected,
                editSession = session,
                editInstruction = "",
                message = null,
            )
        }
    }

    fun updateEditInstruction(value: String) {
        _uiState.update { it.copy(editInstruction = value.take(MAX_EDIT_INSTRUCTION_CHARACTERS)) }
    }

    fun requestEditConfirmation() {
        if (_uiState.value.editInstruction.isBlank() || _uiState.value.voiceInputTarget != null) return
        _uiState.update { it.copy(editConfirmationVisible = true) }
    }

    fun dismissEditConfirmation() {
        _uiState.update { it.copy(editConfirmationVisible = false) }
    }

    fun confirmEdit() {
        val state = _uiState.value
        val session = state.editSession ?: return
        val instruction = state.editInstruction.trim().takeIf(String::isNotEmpty) ?: return
        val key = loadQwenKey()
        if (key == null) {
            _uiState.update {
                it.copy(editConfirmationVisible = false, editStatus = RequestStatus.RETRYABLE, message = "Add a Qwen API key in Settings first.")
            }
            return
        }
        editJob?.cancel()
        val token = ++operationToken
        editJob = viewModelScope.launch {
            _uiState.update { it.copy(editConfirmationVisible = false, editStatus = RequestStatus.RUNNING, message = null) }
            val original = gallery.prepareUpload(session.original.uri).getOrElse {
                key.fill('\u0000')
                failEdit(token, "The original photo cannot be read.")
                return@launch
            }
            val working = if (session.workingUri == session.original.uri) {
                null
            } else {
                gallery.prepareUpload(session.workingUri).getOrElse {
                    original.bytes.fill(0)
                    key.fill('\u0000')
                    failEdit(token, "The current edit cannot be read.")
                    return@launch
                }
            }
            val request = runCatching { ImageEditRequest(listOfNotNull(original, working), instruction) }
                .getOrElse {
                    original.bytes.fill(0)
                    working?.bytes?.fill(0)
                    key.fill('\u0000')
                    failEdit(token, "These photos are too large to send together.")
                    return@launch
                }
            val resultFile = gallery.newEditResultFile()
            try {
                val result = imageEditor.edit(
                    request,
                    key,
                    resultFile,
                )
                if (token != operationToken) return@launch
                when (result) {
                    is ImageEditResult.Ready -> gallery.publishEditedPng(result.file).fold(
                        onSuccess = { asset ->
                            if (token == operationToken) {
                                _uiState.update { current ->
                                    val activeSession = current.editSession ?: return@update current
                                    val variant = EditVariant(
                                        id = UUID.randomUUID().toString(),
                                        uri = asset.uri,
                                        parentVariantId = activeSession.workingVariantId,
                                        createdAtMs = System.currentTimeMillis(),
                                    )
                                    current.copy(
                                        assets = listOf(asset) + current.assets.filterNot { it.uri == asset.uri },
                                        editSession = activeSession.append(variant),
                                        editInstruction = "",
                                        editStatus = RequestStatus.IDLE,
                                    )
                                }
                            }
                        },
                        onFailure = { failEdit(token, "The edited photo could not be saved.") },
                    )
                    is ImageEditResult.Failed -> failEdit(token, result.message)
                    ImageEditResult.CredentialsRejected -> failEdit(token, "The Qwen API key was rejected.")
                    ImageEditResult.Unavailable -> failEdit(token, "AI editing is unavailable.")
                }
            } finally {
                original.bytes.fill(0)
                working?.bytes?.fill(0)
                resultFile.delete()
            }
        }
    }

    fun selectWorkingVariant(variantId: String?) {
        _uiState.update { state ->
            state.copy(editSession = state.editSession?.selectWorking(variantId))
        }
    }

    fun beginSelection() {
        _uiState.update { it.copy(selectionMode = true, message = null) }
    }

    fun startSelection(asset: LibraryAsset) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedUris = if (asset.uri in it.selectedUris) it.selectedUris else it.selectedUris + asset.uri,
                message = null,
            )
        }
    }

    fun toggleSelection(asset: LibraryAsset) {
        _uiState.update { state ->
            val next = toggleSelection(state.selectedUris, asset.uri)
            state.copy(
                selectedUris = next,
                selectionMode = true,
                message = if (next == state.selectedUris && asset.uri !in next) {
                    "Choose up to $MAX_SHARE_SELECTION photos."
                } else {
                    null
                },
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedUris = emptyList(), selectionMode = false, message = null) }
    }

    fun removeSelected(uri: String) {
        _uiState.update { state ->
            val selected = state.selectedUris - uri
            state.copy(
                destination = if (selected.isEmpty()) PhotoDestination.GALLERY else state.destination,
                selectedUris = selected,
                selectionMode = true,
            )
        }
    }

    fun moveSelected(uri: String, offset: Int) {
        _uiState.update { it.copy(selectedUris = moveSelection(it.selectedUris, uri, offset)) }
    }

    fun openShare(asset: LibraryAsset? = null) {
        if (asset != null) startSelection(asset)
        if (_uiState.value.selectedAssets.isEmpty()) return
        _uiState.update { it.copy(destination = PhotoDestination.SHARE, message = null) }
    }

    fun setCaptionLength(length: CaptionLength) {
        _uiState.update {
            it.copy(captionLength = length, captionDraft = it.captionDraft.takeCodePoints(length.maxCodePoints))
        }
    }

    fun updateCaptionDraft(value: String) {
        val limit = _uiState.value.captionLength.maxCodePoints
        _uiState.update { it.copy(captionDraft = value.takeCodePoints(limit)) }
    }

    fun updateCaptionFeedback(value: String) {
        _uiState.update { it.copy(captionFeedback = value.take(MAX_CAPTION_FEEDBACK_CHARACTERS)) }
    }

    fun requestCaptionConfirmation() {
        if (_uiState.value.selectedAssets.isEmpty() || _uiState.value.voiceInputTarget != null) return
        _uiState.update { it.copy(captionConfirmationVisible = true) }
    }

    fun dismissCaptionConfirmation() {
        _uiState.update { it.copy(captionConfirmationVisible = false) }
    }

    fun startVoiceInput(target: VoiceInputTarget) {
        val state = _uiState.value
        if (state.voiceInputTarget != null || state.editStatus == RequestStatus.RUNNING ||
            state.captionStatus == RequestStatus.RUNNING
        ) return
        if (!runCatching(voice::isOnDeviceRecognitionAvailable).getOrDefault(false)) {
            _uiState.update { it.copy(message = "On-device speech recognition is unavailable.") }
            return
        }
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            _uiState.update { it.copy(voiceInputTarget = target, message = null) }
            val result = withTimeoutOrNull(VOICE_INPUT_TIMEOUT_MS) { voice.listenOnce() }
                ?: VoiceResult.Failed("Voice input timed out. Tap the mic to try again.")
            when (result) {
                is VoiceResult.Heard -> when (target) {
                    VoiceInputTarget.EDIT_INSTRUCTION -> updateEditInstruction(result.text)
                    VoiceInputTarget.CAPTION_DRAFT -> updateCaptionDraft(result.text)
                    VoiceInputTarget.CAPTION_FEEDBACK -> updateCaptionFeedback(result.text)
                }
                is VoiceResult.Unavailable -> _uiState.update { it.copy(message = result.message) }
                is VoiceResult.Failed -> _uiState.update { it.copy(message = result.message) }
            }
            _uiState.update { it.copy(voiceInputTarget = null) }
            voiceJob = null
        }
    }

    fun finishVoiceInput() {
        voice.finishListening()
    }

    fun confirmCaption() {
        val state = _uiState.value
        val assets = state.selectedAssets
        if (assets.isEmpty()) return
        val key = loadQwenKey()
        if (key == null) {
            _uiState.update {
                it.copy(captionConfirmationVisible = false, captionStatus = RequestStatus.RETRYABLE, message = "Add a Qwen API key in Settings first.")
            }
            return
        }
        captionJob?.cancel()
        val token = ++operationToken
        captionJob = viewModelScope.launch {
            _uiState.update { it.copy(captionConfirmationVisible = false, captionStatus = RequestStatus.RUNNING, message = null) }
            val sheet = gallery.contactSheet(assets).getOrElse {
                key.fill('\u0000')
                failCaption(token, "The selected photos could not be prepared.")
                return@launch
            }
            try {
                when (
                    val result = captionClient.caption(
                        CaptionRequest(
                            contactSheetJpeg = sheet,
                            photoCount = assets.size,
                            length = state.captionLength,
                            locale = Locale.getDefault().toLanguageTag(),
                            currentDraft = state.captionDraft,
                            feedback = state.captionFeedback,
                        ),
                        key,
                    )
                ) {
                    is CaptionResult.Available -> if (token == operationToken) {
                        _uiState.update {
                            it.copy(
                                captionDraft = result.caption,
                                captionFeedback = "",
                                captionStatus = RequestStatus.IDLE,
                            )
                        }
                    }
                    is CaptionResult.Failed -> failCaption(token, result.message)
                    CaptionResult.CredentialsRejected -> failCaption(token, "The Qwen API key was rejected.")
                    CaptionResult.Unavailable -> failCaption(token, "AI captioning is unavailable.")
                }
            } finally {
                sheet.fill(0)
            }
        }
    }

    fun backToCamera() {
        stopVoiceInput()
        _uiState.update {
            it.copy(
                destination = PhotoDestination.CAMERA,
                activeAsset = null,
                selectedUris = emptyList(),
                selectionMode = false,
                message = null,
            )
        }
    }

    fun back() {
        stopVoiceInput()
        when (_uiState.value.destination) {
            PhotoDestination.CAMERA -> Unit
            PhotoDestination.GALLERY -> if (_uiState.value.selectionMode) clearSelection() else backToCamera()
            PhotoDestination.VIEWER -> _uiState.update { it.copy(destination = PhotoDestination.GALLERY, activeAsset = null) }
            PhotoDestination.EDITOR -> _uiState.update { it.copy(destination = PhotoDestination.VIEWER) }
            PhotoDestination.SHARE -> _uiState.update { it.copy(destination = PhotoDestination.GALLERY) }
        }
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val additions = uris.take(MAX_SHARE_SELECTION).mapNotNull { gallery.describePicked(it).getOrNull() }
            _uiState.update { state ->
                val picked = (state.pickedAssets + additions).distinctBy(LibraryAsset::uri)
                state.copy(
                    pickedAssets = picked,
                    message = if (additions.isEmpty()) "The selected photos are unavailable." else null,
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            gallery.page(cursor).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            assets = (it.assets + page.assets).distinctBy(LibraryAsset::uri),
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(loadingMore = false, message = "More photos could not be loaded.")
                    }
                },
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onBackground() {
        operationToken++
        editJob?.cancel()
        captionJob?.cancel()
        voiceJob?.cancel()
        voice.stop()
        _uiState.update { state ->
            state.copy(
                editConfirmationVisible = false,
                captionConfirmationVisible = false,
                voiceInputTarget = null,
                editStatus = if (state.editStatus == RequestStatus.RUNNING) RequestStatus.RETRYABLE else state.editStatus,
                captionStatus = if (state.captionStatus == RequestStatus.RUNNING) RequestStatus.RETRYABLE else state.captionStatus,
            )
        }
    }

    override fun onCleared() {
        voice.close()
    }

    private fun stopVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        voice.stop()
        _uiState.update { it.copy(voiceInputTarget = null) }
    }

    private fun failEdit(token: Long, message: String) {
        if (token != operationToken) return
        _uiState.update { it.copy(editStatus = RequestStatus.RETRYABLE, message = message) }
    }

    private fun failCaption(token: Long, message: String) {
        if (token != operationToken) return
        _uiState.update { it.copy(captionStatus = RequestStatus.RETRYABLE, message = message) }
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadingMore = false, message = null) }
            gallery.page().fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(assets = page.assets, nextCursor = page.nextCursor, loading = false)
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            assets = emptyList(),
                            nextCursor = null,
                            loading = false,
                            message = if (it.galleryAccess == GalleryAccess.NONE) {
                                "Choose photos or allow gallery access."
                            } else {
                                "The photo library could not be loaded."
                            },
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val MAX_EDIT_INSTRUCTION_CHARACTERS = 800
        const val MAX_CAPTION_FEEDBACK_CHARACTERS = 500
        const val VOICE_INPUT_TIMEOUT_MS = 20_000L
    }
}

internal fun String.takeCodePoints(maxCodePoints: Int): String {
    if (codePointCount(0, length) <= maxCodePoints) return this
    val proposedEnd = offsetByCodePoints(0, maxCodePoints)
    val boundaries = BreakIterator.getCharacterInstance().apply { setText(this@takeCodePoints) }
    val safeEnd = if (boundaries.isBoundary(proposedEnd)) proposedEnd else boundaries.preceding(proposedEnd)
    return substring(0, safeEnd.coerceAtLeast(0))
}
