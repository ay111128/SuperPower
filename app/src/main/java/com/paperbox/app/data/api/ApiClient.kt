package com.paperbox.app.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperbox.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "paperbox_prefs")

object PrefsKeys {
    val TOKEN = stringPreferencesKey("auth_token")
    val USERNAME = stringPreferencesKey("username")
    val SERVER_URL = stringPreferencesKey("server_url")
}

@Singleton
class ApiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val authInterceptor = Interceptor { chain ->
        val token = runCatching {
            runBlocking {
                context.dataStore.data.map { it[PrefsKeys.TOKEN] ?: "" }.first()
            }
        }.getOrDefault("")

        val request = if (token.isNotEmpty()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val retryInterceptor = Interceptor { chain ->
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return@Interceptor chain.proceed(chain.request())
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    Thread.sleep(1000L * (attempt + 1))
                }
            }
        }
        throw lastException ?: Exception("连接失败")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getBaseUrl(): String {
        return runCatching {
            runBlocking {
                context.dataStore.data.map {
                    it[PrefsKeys.SERVER_URL] ?: BuildConfig.API_BASE_URL
                }.first()
            }
        }.getOrDefault(BuildConfig.API_BASE_URL)
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("${getBaseUrl()}/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** 用自定义 base URL 创建 ApiService（用于测试/切换服务器） */
    fun createService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl("${baseUrl}/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
