package com.glosc.images

import android.app.Application
import com.glosc.images.core.security.ApiKeyStore
import com.glosc.images.data.api.OpenAiImageGenerationClient
import com.glosc.images.data.db.GloscDatabase
import com.glosc.images.data.repository.AppRepository
import com.glosc.images.data.storage.ImageFileStorage

class GloscImagesApp : Application() {
    val repository: AppRepository by lazy {
        AppRepository(
            dao = GloscDatabase.get(this).dao(),
            storage = ImageFileStorage(this),
            keyStore = ApiKeyStore(this),
            imageClient = OpenAiImageGenerationClient()
        )
    }
}
