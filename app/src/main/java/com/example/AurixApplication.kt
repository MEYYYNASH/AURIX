package com.example

import android.app.Application
import android.content.Intent
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AurixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().log("AurixApplication initialized successfully")
        }
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Report to Firebase Crashlytics
                runCatching {
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                }

                val logFile = File(filesDir, "aurix_crash_logs.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()

                FileWriter(logFile, true).use { writer ->
                    writer.append("\n==============================\n")
                    writer.append("CRASH TIMESTAMP: $timestamp\n")
                    writer.append("THREAD: ${thread.name}\n")
                    writer.append(stackTraceString)
                    writer.append("==============================\n")
                }
                Log.e("AurixCrashLogger", "Intercepted fatal exception, logged to Crashlytics & disk", throwable)
            } catch (e: Exception) {
                Log.e("AurixCrashLogger", "Failed to write crash log", e)
            } finally {
                // Gracefully restart MainActivity instead of letting the app die silently or freeze
                try {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}

                try {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                } catch (_: Exception) {}
            }
        }
    }
}
