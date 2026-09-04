package com.bolin.photohelper.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import java.io.FileNotFoundException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreGallery(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val thumbnailCache = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val pendingState = appContext.getSharedPreferences("pending_ai_edit", Context.MODE_PRIVATE)

    init {
        sweepRecordedPendingEdit()
    }

    suspend fun page(after: GalleryCursor? = null): Result<GalleryPage> = withContext(Dispatchers.IO) {
        runCatching {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val selection = after?.let {
                "(${MediaStore.Images.Media.DATE_ADDED} < ? OR " +
                    "(${MediaStore.Images.Media.DATE_ADDED} = ? AND ${MediaStore.Images.Media._ID} < ?))"
            }
            val args = after?.let {
                arrayOf(it.dateAddedSeconds.toString(), it.dateAddedSeconds.toString(), it.id.toString())
            }
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media._ID),
                )
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE_SIZE)
            }
            val assets = resolver.query(collection, PROJECTION, queryArgs, null)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val width = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val height = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val date = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val owner = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val rowId = cursor.getLong(id)
                        val ownerPackage = cursor.getString(owner)
                        add(
                            LibraryAsset(
                                id = rowId,
                                uri = ContentUris.withAppendedId(collection, rowId).toString(),
                                displayName = cursor.getString(name).orEmpty(),
                                mimeType = cursor.getString(mime) ?: "image/*",
                                width = cursor.getInt(width),
                                height = cursor.getInt(height),
                                dateAddedSeconds = cursor.getLong(date),
                                source = if (ownerPackage == appContext.packageName) {
                                    AssetSource.APP_CREATED
                                } else {
                                    AssetSource.MEDIA_STORE
                                },
                            ),
                        )
                    }
                }
            }.orEmpty()
            GalleryPage(
                assets = assets,
                nextCursor = assets.lastOrNull()
                    ?.takeIf { assets.size == PAGE_SIZE }
                    ?.let { GalleryCursor(it.dateAddedSeconds, it.id) },
            )
        }
    }

    suspend fun describePicked(uri: Uri): Result<LibraryAsset> = withContext(Dispatchers.IO) {
        runCatching {
            var displayName = "Selected photo"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        ?: displayName
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw FileNotFoundException("The selected photo is unavailable")
            require(options.outWidth > 0 && options.outHeight > 0) { "The selected item is not a readable image" }
            LibraryAsset(
                id = uri.toString().hashCode().toLong(),
                uri = uri.toString(),
                displayName = displayName,
                mimeType = resolver.getType(uri) ?: "image/*",
                width = options.outWidth,
                height = options.outHeight,
                dateAddedSeconds = System.currentTimeMillis() / 1_000,
                source = AssetSource.PHOTO_PICKER,
            )
        }
    }

    suspend fun thumbnail(uri: String, sizePx: Int): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val key = "$uri@$sizePx"
            thumbnailCache.get(key) ?: resolver.loadThumbnail(Uri.parse(uri), Size(sizePx, sizePx), null)
                .also { thumbnailCache.put(key, it) }
        }
    }

    fun canRead(uri: String): Boolean = runCatching {
        resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    suspend fun prepareUpload(uri: String): Result<PreparedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(resolver, Uri.parse(uri))
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, MAX_EDIT_EDGE.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(
                    maxOf(1, (info.size.width * scale).toInt()),
                    maxOf(1, (info.size.height * scale).toInt()),
                )
            }
            try {
                require(bitmap.width.toLong() * bitmap.height <= MAX_EDIT_PIXELS) { "Photo dimensions are too large" }
                val bytes = encodeBoundedJpeg(bitmap)
                PreparedImage(bytes, bitmap.width, bitmap.height, "image/jpeg")
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun contactSheet(assets: List<LibraryAsset>): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            require(assets.size in 1..MAX_SHARE_SELECTION)
            val columns = minOf(3, assets.size)
            val rows = (assets.size + columns - 1) / columns
            val sheet = Bitmap.createBitmap(columns * CONTACT_CELL, rows * CONTACT_CELL, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(sheet)
                canvas.drawColor(Color.BLACK)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 42f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xcc000000.toInt() }
                assets.forEachIndexed { index, asset ->
                    val bitmap = resolver.loadThumbnail(Uri.parse(asset.uri), Size(CONTACT_CELL, CONTACT_CELL), null)
                    val left = (index % columns) * CONTACT_CELL
                    val top = (index / columns) * CONTACT_CELL
                    val sourceRatio = bitmap.width.toFloat() / bitmap.height
                    val targetRatio = 1f
                    val source = if (sourceRatio > targetRatio) {
                        val width = bitmap.height
                        android.graphics.Rect((bitmap.width - width) / 2, 0, (bitmap.width + width) / 2, bitmap.height)
                    } else {
                        val height = bitmap.width
                        android.graphics.Rect(0, (bitmap.height - height) / 2, bitmap.width, (bitmap.height + height) / 2)
                    }
                    canvas.drawBitmap(
                        bitmap,
                        source,
                        android.graphics.Rect(left, top, left + CONTACT_CELL, top + CONTACT_CELL),
                        null,
                    )
                    canvas.drawCircle(left + 30f, top + 34f, 24f, badgePaint)
                    canvas.drawText("${index + 1}", left + 18f, top + 48f, labelPaint)
                    bitmap.recycle()
                }
                encodeContactSheet(sheet)
            } finally {
                sheet.recycle()
            }
        }
    }

    fun newEditResultFile(): File {
        val directory = File(appContext.cacheDir, "ai-edits").apply { mkdirs() }
        return File.createTempFile("result-", ".png", directory)
    }

    suspend fun publishEditedPng(file: File): Result<LibraryAsset> = withContext(Dispatchers.IO) {
        var pendingUri: Uri? = null
        runCatching {
            validatePng(file)
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoHelper_Edit_$now.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, EDIT_DIRECTORY)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            pendingUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Android could not create the edited photo")
            check(pendingState.edit().putString(PENDING_URI, pendingUri.toString()).commit())
            resolver.openOutputStream(pendingUri!!, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Android could not write the edited photo")
            resolver.openInputStream(pendingUri!!)?.use { validatePng(it.readBytes()) }
                ?: throw IOException("Android could not verify the edited photo")
            check(
                resolver.update(
                    pendingUri!!,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            )
            pendingState.edit().remove(PENDING_URI).commit()
            LibraryAsset(
                id = ContentUris.parseId(pendingUri!!),
                uri = pendingUri.toString(),
                displayName = "PhotoHelper_Edit_$now.png",
                mimeType = "image/png",
                width = pngBounds(file).first,
                height = pngBounds(file).second,
                dateAddedSeconds = now / 1_000,
                source = AssetSource.APP_CREATED,
            )
        }.onFailure {
            pendingUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            pendingState.edit().remove(PENDING_URI).commit()
        }.also {
            file.delete()
        }
    }

    private fun encodeBoundedJpeg(bitmap: Bitmap): ByteArray {
        for (quality in listOf(90, 82, 74, 66)) {
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                output.toByteArray()
            }
            if (bytes.size <= MAX_EDIT_IMAGE_BYTES) return bytes
        }
        throw IllegalArgumentException("Photo cannot fit the AI upload limit")
    }

    private fun encodeContactSheet(bitmap: Bitmap): ByteArray {
        for (quality in listOf(82, 72, 62, 52)) {
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                output.toByteArray()
            }
            if (bytes.size <= MAX_CONTACT_SHEET_BYTES) return bytes
        }
        throw IllegalArgumentException("Selected photos cannot fit the caption request limit")
    }

    private fun validatePng(file: File) {
        require(file.length() in 1..MAX_EDIT_RESULT_BYTES.toLong()) { "AI result is too large" }
        file.inputStream().use { input ->
            val signature = ByteArray(PNG_SIGNATURE.size)
            require(input.read(signature) == signature.size && signature.contentEquals(PNG_SIGNATURE)) {
                "AI result is not a PNG image"
            }
        }
        val (width, height) = pngBounds(file)
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_EDIT_PIXELS) { "AI result dimensions are invalid" }
    }

    private fun validatePng(bytes: ByteArray) {
        require(bytes.size in 1..MAX_EDIT_RESULT_BYTES) { "AI result is too large" }
        require(bytes.size >= PNG_SIGNATURE.size && bytes.take(PNG_SIGNATURE.size).toByteArray().contentEquals(PNG_SIGNATURE)) {
            "AI result is not a PNG image"
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth.toLong() * options.outHeight <= MAX_EDIT_PIXELS
        ) { "AI result dimensions are invalid" }
    }

    private fun pngBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private fun sweepRecordedPendingEdit() {
        val value = pendingState.getString(PENDING_URI, null) ?: return
        runCatching { resolver.delete(Uri.parse(value), null, null) }
        pendingState.edit().remove(PENDING_URI).commit()
    }

    private companion object {
        const val PAGE_SIZE = 120
        const val THUMBNAIL_CACHE_BYTES = 24 * 1024 * 1024
        const val MAX_EDIT_EDGE = 2048
        const val MAX_EDIT_PIXELS = 2048L * 2048L
        const val MAX_EDIT_IMAGE_BYTES = 2_500_000
        const val MAX_EDIT_RESULT_BYTES = 25 * 1024 * 1024
        const val CONTACT_CELL = 320
        const val MAX_CONTACT_SHEET_BYTES = 1_000_000
        const val EDIT_DIRECTORY = "Pictures/PhotoHelper/Edits"
        const val PENDING_URI = "pending_uri"
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.OWNER_PACKAGE_NAME,
        )
    }
}

data class PreparedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
)
