package com.paperbox.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.paperbox.app.data.api.ApiClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaperboxApp : Application(), ImageLoaderFactory {

    private var _imageLoader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        if (_imageLoader == null) {
            try {
                val apiClient = ApiClient(this)
                _imageLoader = ImageLoader.Builder(this)
                    .okHttpClient(apiClient.okHttpClient)
                    .crossfade(true)
                    .build()
                Log.d("PaperboxApp", "ImageLoader created with auth OkHttpClient")
            } catch (e: Exception) {
                Log.e("PaperboxApp", "Failed to create ImageLoader", e)
                // fallback: 不带 auth 的 ImageLoader
                _imageLoader = ImageLoader.Builder(this).crossfade(true).build()
            }
        }
        return _imageLoader!!
    }
}
