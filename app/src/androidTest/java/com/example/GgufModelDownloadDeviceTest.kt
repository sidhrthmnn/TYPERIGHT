package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Opt-in actual HTTPS download; normal instrumented test runs do not fetch model weights. */
@RunWith(AndroidJUnit4::class)
class GgufModelDownloadDeviceTest {
    @Test fun verifiesDownloadWithoutDeletingOtherModels() = runBlocking {
        val id = InstrumentationRegistry.getArguments().getString("download_model_id")
        assumeTrue("Pass download_model_id for the network download check", id != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = GgufModelCatalog.resolve(context, id!!)
        val model = if (InstrumentationRegistry.getArguments().getString("custom_model") == "true")
            GgufModelCatalog.custom("Verified custom editing model", source.url, source.sha256, source.format)
        else source
        if (model.custom) GgufModelCatalog.saveCustom(context, model)
        val installed = GgufModelCatalog.all(context).filter { LocalGgufModel.isReady(context, it) }
        LocalGgufModel.download(context, model)
        val installedModel = GgufModelCatalog.resolve(context, model.id)
        assertTrue(LocalGgufModel.isReady(context, installedModel))
        assertEquals(source.bytes, installedModel.bytes)
        assertFalse(java.io.File(LocalGgufModel.file(context, model).path + ".part").exists())
        assertTrue(installed.all { LocalGgufModel.isReady(context, it) })
        val digest = MessageDigest.getInstance("SHA-256")
        LocalGgufModel.file(context, model).inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        assertEquals(model.sha256, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
