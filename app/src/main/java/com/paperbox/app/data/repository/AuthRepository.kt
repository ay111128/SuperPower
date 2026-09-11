package com.paperbox.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.paperbox.app.data.api.ApiClient
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.data.api.dataStore
import com.paperbox.app.data.api.models.LoginRequest
import com.paperbox.app.data.api.models.RegisterRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient
) {
    val apiService get() = apiClient.apiService

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                context.dataStore.edit { prefs ->
                    prefs[PrefsKeys.TOKEN] = body.token
                    prefs[PrefsKeys.USERNAME] = username
                }
                Result.success(body.token)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string()?.let { org.json.JSONObject(it).optString("error") }
                } catch (_: Exception) { null }
                Result.failure(Exception(errorMsg ?: "登录失败：${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误：${e.message}"))
        }
    }

    suspend fun register(username: String, password: String): Result<String> {
        return try {
            val response = apiService.register(RegisterRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                context.dataStore.edit { prefs ->
                    prefs[PrefsKeys.TOKEN] = body.token
                    prefs[PrefsKeys.USERNAME] = username
                }
                Result.success(body.token)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string()?.let { org.json.JSONObject(it).optString("error") }
                } catch (_: Exception) { null }
                Result.failure(Exception(errorMsg ?: "注册失败：${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误：${e.message}"))
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(PrefsKeys.TOKEN)
            prefs.remove(PrefsKeys.USERNAME)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return context.dataStore.data.map { it[PrefsKeys.TOKEN] }.first()?.isNotEmpty() == true
    }

    suspend fun getUsername(): String {
        return context.dataStore.data.map { it[PrefsKeys.USERNAME] ?: "" }.first()
    }
}
