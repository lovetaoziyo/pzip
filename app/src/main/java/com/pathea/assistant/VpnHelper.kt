package com.pathea.assistant

import android.content.Context
import com.tim.openvpn.configuration.OpenVPNConfig
import com.tim.openvpn.service.OpenVPNService
import java.io.File

/**
 * 一键连接 / 断开 OpenVPN。
 * LDAP 用户名密码通过 provide_creds 回调传给 OpenVPN 3，并开启日志便于排查。
 */
object VpnHelper {

    fun connect(context: Context, username: String, password: String) {
        val base = readRawConfig(context)

        // 日志写入外部应用目录（文件管理器可读）
        val logDir = context.getExternalFilesDir(null) ?: context.filesDir
        val logFile = File(logDir, "openvpn.log")

        val config = base.replace("verb 3", "verb 4\nlog ${logFile.absolutePath}")

        val ovpnConfig = OpenVPNConfig(
            configuration = config,
            username = username,
            password = password
        )
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
