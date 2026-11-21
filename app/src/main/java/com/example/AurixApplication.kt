package com.example

import android.app.Application
import android.util.Log
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
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
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
                Log.e("AurixCrashLogger", "Fatal crash caught and written to ${logFile.absolutePath}", throwable)
            } catch (e: Exception) {
                Log.e("AurixCrashLogger", "Failed to write crash log to disk", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
