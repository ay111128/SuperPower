package com.paperbox.app

import android.content.Context
import android.os.Build
import android.util.Log
import com.paperbox.app.data.api.ApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

/**
 * 崩溃日志自动落盘 + 自动上传（全程无需人工操作）：
 *
 * - 未捕获崩溃 → 落盘 last_crash.txt → 进程退出；
 * - 下次冷启动 [install] 里后台 POST 到 `/materials-api/client-crash`，成功后删文件；
 *   失败（无网等）保留文件，再下次启动重试；
 * - 已被诊断包裹 catch 住的崩溃走 [record]：落盘并立即上传（进程还活着）。
 *
 * 服务端把报告追加到 `server/logs/client-crashes.log`，SSH tail 即可查看。
 * 传输复用 [ApiClient] 的 OkHttpClient —— 和登录等接口同一条 TLS 链路（自签名证书同样生效）。
 */
object CrashDiagnostics {

    private const val TAG = "CrashDiagnostics"
    private const val FILE_NAME = "last_crash.txt"
    private const val SEP = "\n\n===== crash separator =====\n\n"

    @Volatile private var uploading = false
    @Volatile private var lastRecorded = ""

    /** Application.onCreate 调用：装全局 handler + 上传上次遗留的崩溃。 */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val dir = appContext.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            appendCrash(dir, throwable)
            prev?.uncaughtException(thread, throwable)
        }
        uploadPending(appContext)
    }

    /** 已被 catch 住的崩溃：落盘并立即上传（同一条异常只处理一次）。 */
    fun record(e: Throwable, context: Context) {
        if (e.toString() == lastRecorded) return
        lastRecorded = e.toString()
        appendCrash(context.applicationContext.filesDir, e)
        uploadPending(context)
    }

    private fun appendCrash(dir: File, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val f = File(dir, FILE_NAME)
            val prefix = if (f.exists() && f.length() > 0) f.readText() + SEP else ""
            f.writeText(prefix + sw.toString())
        } catch (_: Throwable) {
            // 落盘失败不能挡住原有崩溃处理
        }
    }

    /** 后台上传落盘的崩溃；无网络/失败保留文件，下次启动重试。 */
    fun uploadPending(context: Context) {
        if (uploading) return
        uploading = true
        Thread {
            try {
                val f = File(context.applicationContext.filesDir, FILE_NAME)
                if (!f.exists() || f.length() == 0L) return@Thread
                val stack = f.readText()
                if (stack.isBlank()) return@Thread

                val json = JSONObject().apply {
                    put("version", BuildConfig.VERSION_NAME)
                    put("code", BuildConfig.VERSION_CODE)
                    put("sdk", Build.VERSION.SDK_INT)
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("stack", stack)
                }
                val client = ApiClient(context).okHttpClient.newBuilder()
                    .callTimeout(15, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url("${BuildConfig.API_BASE_URL}/materials-api/client-crash")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        f.delete()
                        Log.i(TAG, "crash report uploaded")
                    } else {
                        Log.w(TAG, "crash upload failed: HTTP ${resp.code}")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "crash upload error: $t")
            } finally {
                uploading = false
            }
        }.start()
    }
}
