package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.ui.image.AurixImagePipeline
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AurixApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
    }

    override fun newImageLoader(): ImageLoader {
        return AurixImagePipeline.createImageLoader(this)
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(createCrashHandler(this, defaultHandler))
    }

    companion object {
        private val logLock = Any()
        private const val MAX_CRASH_LOG_BYTES = 512L * 1024L

        internal fun createCrashHandler(
            context: Context,
            defaultHandler: Thread.UncaughtExceptionHandler?
        ): Thread.UncaughtExceptionHandler {
            val appContext = context.applicationContext ?: context
            return Thread.UncaughtExceptionHandler { thread, throwable ->
                recordCrashToDisk(appContext, thread, throwable)
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable)
                } else {
                    runCatching {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
        }

        internal fun recordCrashToDisk(
            context: Context,
            thread: Thread,
            throwable: Throwable
        ) {
            try {
                synchronized(logLock) {
                    val logFile = File(context.filesDir, "aurix_crash_logs.txt")
                    if (logFile.exists() && logFile.length() > MAX_CRASH_LOG_BYTES) {
                        logFile.writeText("")
                    }
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    pw.flush()

                    FileWriter(logFile, true).use { writer ->
                        writer.append("\n==============================\n")
                        writer.append("CRASH TIMESTAMP: $timestamp\n")
                        writer.append("THREAD: ${thread.name}\n")
                        writer.append(sw.toString())
                        writer.append("==============================\n")
                    }
                }
                Log.e("AurixCrashLogger", "Intercepted fatal exception, logged to disk", throwable)
            } catch (e: Exception) {
                Log.e("AurixCrashLogger", "Failed to write crash log", e)
            }
        }
    }
}
