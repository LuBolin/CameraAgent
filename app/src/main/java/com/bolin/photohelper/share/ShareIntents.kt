package com.bolin.photohelper.share

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.bolin.photohelper.gallery.LibraryAsset

private val TELEGRAM_PACKAGES = listOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.thunderdog.challegram",
)

fun shareIntent(
    assets: List<LibraryAsset>,
    caption: String,
    packageName: String? = null,
): Intent {
    require(assets.isNotEmpty())
    val uris = assets.map { Uri.parse(it.uri) }
    val type = assets.map(LibraryAsset::mimeType).distinct().singleOrNull() ?: "image/*"
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        this.type = type
        if (uris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        clipData = ClipData(assets.first().displayName, arrayOf(type), ClipData.Item(uris.first())).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        packageName?.let(::setPackage)
    }
    return intent
}

fun chooserIntent(assets: List<LibraryAsset>, caption: String): Intent =
    Intent.createChooser(shareIntent(assets, caption), null)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

fun telegramIntent(
    packageManager: PackageManager,
    assets: List<LibraryAsset>,
    caption: String,
): Intent? = TELEGRAM_PACKAGES.firstNotNullOfOrNull { packageName ->
    shareIntent(assets, caption, packageName).takeIf { it.resolveActivity(packageManager) != null }
}
