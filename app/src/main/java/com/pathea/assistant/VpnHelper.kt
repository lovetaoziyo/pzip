package com.pathea.assistant

import android.content.Context
import com.tim.openvpn.configuration.OpenVPNConfig
import com.tim.openvpn.service.OpenVPNService
import java.io.File

/**
 * 一键连接 / 断开 OpenVPN。
 * 把 LDAP 用户名密码写入文件，用 auth-user-pass <file> 方式注入配置，
 * 并开启 log 日志便于排查连接失败原因。
 */
object VpnHelper {

    fun connect(context: Context, username: String, password: String) {
        val base = readRawConfig(context)

        // 凭据写入文件，用 auth-user-pass <file> 引用（OpenVPN 3 兼容性最好）
        val credsFile = File(context.filesDir, "creds.txt")
        credsFile.writeText("$username\n$password")

        // 日志写入外部应用目录（文件管理器可读：Android/data/com.pathea.assistant/files/）
        val logDir = context.getExternalFilesDir(null) ?: context.filesDir
        val logFile = File(logDir, "openvpn.log")

        val config = base
            .replace("auth-user-pass", "auth-user-pass ${credsFile.absolutePath}")
            .replace("verb 3", "verb 4\nlog ${logFile.absolutePath}")

        val ovpnConfig = OpenVPNConfig(configuration = config)
        OpenVPNService.startService(context, ovpnConfig)
    }

    fun disconnect(context: Context) {
        OpenVPNService.stopService(context)
    }

    private fun readRawConfig(context: Context): String {
        return context.resources.openRawResource(R.raw.pathea)
            .bufferedReader().use { it.readText() }
    }
}
