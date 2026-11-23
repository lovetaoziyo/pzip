package com.pathea.assistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = "Thread: ${thread.name}\n" +
                "${throwable.javaClass.name}: ${throwable.message}\n" +
                throwable.stackTraceToString()
            try {
                getExternalFilesDir(null)?.resolve("crash.log")?.writeText(msg)
            } catch (_: Throwable) {
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    getString(R.string.crash_message, throwable.javaClass.simpleName, throwable.message),
                    Toast.LENGTH_LONG
                ).show()
            }
            try {
                Thread.sleep(2500)
            } catch (_: InterruptedException) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
