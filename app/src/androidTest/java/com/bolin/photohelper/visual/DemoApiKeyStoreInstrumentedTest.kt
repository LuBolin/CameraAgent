package com.bolin.photohelper.visual

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoApiKeyStoreInstrumentedTest {
    private val store by lazy {
        DemoApiKeyStore(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Before
    @After
    fun clearStore() {
        store.clear()
    }

    @Test
    fun keyRoundTripsEncryptedAndClearRemovesIt() {
        val input = "disposable-demo-key".toCharArray()

        store.save(input)

        assertTrue(input.all { it == '\u0000' })
        assertTrue(store.hasKey())
        val loaded = store.load()
        assertArrayEquals("disposable-demo-key".toCharArray(), loaded)
        loaded?.fill('\u0000')

        store.clear()

        assertFalse(store.hasKey())
        assertNull(store.load())
    }

    @Test
    fun overlongKeyIsRejectedClearedAndNotStored() {
        val input = CharArray(513) { 'x' }

        val failure = runCatching { store.save(input) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(input.all { it == '\u0000' })
        assertFalse(store.hasKey())
    }
}
