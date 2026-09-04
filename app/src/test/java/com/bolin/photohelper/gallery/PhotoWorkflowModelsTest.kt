package com.bolin.photohelper.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoWorkflowModelsTest {
    @Test
    fun `android 14 distinguishes full partial and no gallery access`() {
        assertEquals(GalleryAccess.FULL, galleryAccessFor(34, false, true, true))
        assertEquals(GalleryAccess.PARTIAL, galleryAccessFor(34, false, false, true))
        assertEquals(GalleryAccess.NONE, galleryAccessFor(34, true, false, false))
        assertEquals(GalleryAccess.FULL, galleryAccessFor(33, false, true, false))
        assertEquals(GalleryAccess.FULL, galleryAccessFor(32, true, false, false))
    }

    @Test
    fun `selection preserves order and stops at nine`() {
        val selected = (1..10).fold(emptyList<String>()) { current, index ->
            toggleSelection(current, "content://photo/$index")
        }

        assertEquals((1..9).map { "content://photo/$it" }, selected)
        assertEquals(selected - "content://photo/4", toggleSelection(selected, "content://photo/4"))
    }

    @Test
    fun `selection can be reordered without losing an item`() {
        val selected = listOf("one", "two", "three")

        assertEquals(listOf("two", "one", "three"), moveSelection(selected, "two", -1))
        assertEquals(selected, moveSelection(selected, "one", -1))
        assertEquals(selected, moveSelection(selected, "missing", 1))
    }

    @Test
    fun `editing an older variant creates a branch from that working asset`() {
        val original = asset("content://photo/original")
        val first = EditVariant("first", "content://photo/first", null, 1)
        val second = EditVariant("second", "content://photo/second", "first", 2)
        val branch = EditVariant("branch", "content://photo/branch", "first", 3)

        val session = EditSession(original)
            .append(first)
            .append(second)
            .selectWorking("first")
            .append(branch)

        assertEquals("branch", session.workingVariantId)
        assertEquals("first", session.variants.last().parentVariantId)
        assertEquals(original.uri, session.selectWorking(null).workingUri)
    }

    @Test
    fun `caption limit counts unicode code points instead of utf16 units`() {
        val emoji = "A😀B"
        assertEquals("A😀", emoji.takeCodePoints(2))
    }

    @Test
    fun `caption limit keeps a combining grapheme intact`() {
        val value = "a".repeat(79) + "e\u0301"
        assertEquals("a".repeat(79), value.takeCodePoints(80))
    }

    private fun asset(uri: String) = LibraryAsset(
        id = 1,
        uri = uri,
        displayName = "photo",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        dateAddedSeconds = 1,
        source = AssetSource.MEDIA_STORE,
    )
}
