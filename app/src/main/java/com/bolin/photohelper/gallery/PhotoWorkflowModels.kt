package com.bolin.photohelper.gallery

const val MAX_SHARE_SELECTION = 9

enum class PhotoDestination { CAMERA, GALLERY, VIEWER, EDITOR, SHARE }

enum class GalleryAccess { NONE, PARTIAL, FULL }

enum class AssetSource { MEDIA_STORE, PHOTO_PICKER, APP_CREATED }

data class LibraryAsset(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val dateAddedSeconds: Long,
    val source: AssetSource,
)

data class GalleryCursor(val dateAddedSeconds: Long, val id: Long)

data class GalleryPage(
    val assets: List<LibraryAsset>,
    val nextCursor: GalleryCursor?,
)

data class EditVariant(
    val id: String,
    val uri: String,
    val parentVariantId: String?,
    val createdAtMs: Long,
)

data class EditSession(
    val original: LibraryAsset,
    val variants: List<EditVariant> = emptyList(),
    val workingVariantId: String? = null,
) {
    val workingUri: String
        get() = workingVariantId
            ?.let { id -> variants.firstOrNull { it.id == id }?.uri }
            ?: original.uri

    fun selectWorking(variantId: String?): EditSession =
        if (variantId == null || variants.any { it.id == variantId }) copy(workingVariantId = variantId) else this

    fun append(variant: EditVariant): EditSession =
        copy(variants = variants + variant, workingVariantId = variant.id)
}

enum class CaptionLength(val maxCodePoints: Int) {
    SHORT(80),
    LONG(300),
}

enum class RequestStatus { IDLE, RUNNING, RETRYABLE }

enum class VoiceInputTarget { EDIT_INSTRUCTION, CAPTION_DRAFT, CAPTION_FEEDBACK }

data class PhotoWorkflowUiState(
    val destination: PhotoDestination = PhotoDestination.CAMERA,
    val galleryAccess: GalleryAccess = GalleryAccess.NONE,
    val assets: List<LibraryAsset> = emptyList(),
    val pickedAssets: List<LibraryAsset> = emptyList(),
    val nextCursor: GalleryCursor? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val message: String? = null,
    val activeAsset: LibraryAsset? = null,
    val selectedUris: List<String> = emptyList(),
    val selectionMode: Boolean = false,
    val editSession: EditSession? = null,
    val editInstruction: String = "",
    val editConfirmationVisible: Boolean = false,
    val editStatus: RequestStatus = RequestStatus.IDLE,
    val captionLength: CaptionLength = CaptionLength.SHORT,
    val captionDraft: String = "",
    val captionFeedback: String = "",
    val captionConfirmationVisible: Boolean = false,
    val captionStatus: RequestStatus = RequestStatus.IDLE,
    val voiceInputTarget: VoiceInputTarget? = null,
) {
    val visibleAssets: List<LibraryAsset>
        get() = (pickedAssets + assets).distinctBy(LibraryAsset::uri)

    val selectedAssets: List<LibraryAsset>
        get() = selectedUris.mapNotNull { uri -> visibleAssets.firstOrNull { it.uri == uri } }

    val selecting: Boolean get() = selectionMode
}

fun galleryAccessFor(
    apiLevel: Int,
    legacyReadGranted: Boolean,
    mediaImagesGranted: Boolean,
    selectedImagesGranted: Boolean,
): GalleryAccess = when {
    apiLevel >= 34 && mediaImagesGranted -> GalleryAccess.FULL
    apiLevel >= 34 && selectedImagesGranted -> GalleryAccess.PARTIAL
    apiLevel >= 34 -> GalleryAccess.NONE
    apiLevel >= 33 && mediaImagesGranted -> GalleryAccess.FULL
    apiLevel >= 33 -> GalleryAccess.NONE
    legacyReadGranted -> GalleryAccess.FULL
    else -> GalleryAccess.NONE
}

fun toggleSelection(current: List<String>, uri: String): List<String> = when {
    uri in current -> current - uri
    current.size >= MAX_SHARE_SELECTION -> current
    else -> current + uri
}

fun moveSelection(current: List<String>, uri: String, offset: Int): List<String> {
    val from = current.indexOf(uri)
    if (from < 0) return current
    val to = (from + offset).coerceIn(0, current.lastIndex)
    if (from == to) return current
    return current.toMutableList().apply { add(to, removeAt(from)) }
}
