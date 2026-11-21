package com.pathea.assistant

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.blinkt.openvpn.core.ICSOpenVPNApplication

class App : ICSOpenVPNApplication() {

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
                    "崩溃: ${throwable.javaClass.simpleName}: ${throwable.message}",
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
