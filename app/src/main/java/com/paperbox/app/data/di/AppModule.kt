package com.paperbox.app.data.di

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.paperbox.app.data.api.ApiClient
import com.paperbox.app.data.api.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiClient(@ApplicationContext context: Context): ApiClient {
        return ApiClient(context)
    }

    @Provides
    @Singleton
    fun provideApiService(apiClient: ApiClient): ApiService {
        return apiClient.apiService
    }

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, apiClient: ApiClient): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(apiClient.okHttpClient)
            .crossfade(true)
            .build()
    }
}
