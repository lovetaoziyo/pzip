package com.pathea.assistant

import android.content.Context
import com.tim.openvpn.configuration.OpenVPNConfig
import com.tim.openvpn.service.OpenVPNService

/**
 * 一键连接 / 断开 OpenVPN。
 * 把 LDAP 用户名密码以 <auth-user-pass> 内联凭据方式注入 .ovpn 配置。
 */
object VpnHelper {

    fun connect(context: Context, username: String, password: String) {
        val base = readRawConfig(context)
        // 内联 <auth-user-pass> 凭据
        val config = base.replace(
            "auth-user-pass",
            "<auth-user-pass>\n$username\n$password\n</auth-user-pass>"
        )
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
