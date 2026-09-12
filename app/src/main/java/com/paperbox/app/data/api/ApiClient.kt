package com.paperbox.app.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paperbox.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    val SAVED_USERNAME = stringPreferencesKey("saved_username")
    val SAVED_PASSWORD = stringPreferencesKey("saved_password")
    val REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
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

        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Connection", "keep-alive")
            .apply {
                if (token.isNotEmpty()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
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

    /** 自动日志上传：失败请求和诊断日志 POST 到服务器 */
    private fun uploadLog(message: String) {
        try {
            val body = """{"level":"android","message":"[AppLog] $message","source":"app"}"""
                .toRequestBody("application/json".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("${getBaseUrl()}/materials-api/app-logs")
                .post(body)
                .build()
            okHttpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build().newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    /** 外部可调用的诊断日志上传 */
    fun uploadDiagLog(message: String) = uploadLog(message)

    private val networkLogInterceptor = Interceptor { chain ->
        val request = chain.request()
        val start = System.currentTimeMillis()
        try {
            val response = chain.proceed(request)
            val ms = System.currentTimeMillis() - start
            if (response.code >= 400) {
                uploadLog("HTTP ${response.code} ${request.method} ${request.url.encodedPath} (${ms}ms)")
            }
            response
        } catch (e: Exception) {
            val ms = System.currentTimeMillis() - start
            uploadLog("HTTP_ERR ${request.method} ${request.url.encodedPath} ${e.javaClass.simpleName}: ${e.message} (${ms}ms)")
            throw e
        }
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(retryInterceptor)
        .addInterceptor(networkLogInterceptor)
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
