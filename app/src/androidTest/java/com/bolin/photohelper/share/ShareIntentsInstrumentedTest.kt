package com.bolin.photohelper.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bolin.photohelper.gallery.AssetSource
import com.bolin.photohelper.gallery.LibraryAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentsInstrumentedTest {
    @Test
    fun multipleImagesCreateReadableClipData() {
        val assets = listOf(asset(1), asset(2))

        val intent = shareIntent(assets, "A caption")

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(2, intent.clipData?.itemCount)
        assertEquals(assets[0].uri, intent.clipData?.getItemAt(0)?.uri.toString())
        assertEquals(assets[1].uri, intent.clipData?.getItemAt(1)?.uri.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun asset(id: Long) = LibraryAsset(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        dateAddedSeconds = id,
        source = AssetSource.MEDIA_STORE,
    )
}
