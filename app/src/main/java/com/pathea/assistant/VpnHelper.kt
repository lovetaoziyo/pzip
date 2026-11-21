package com.pathea.assistant

import android.content.Context
import com.tim.openvpn.configuration.OpenVPNConfig
import com.tim.openvpn.service.OpenVPNService

/**
 * 一键连接 / 断开 OpenVPN。
 * LDAP 用户名密码通过 provide_creds 回调传给 OpenVPN 3。
 */
object VpnHelper {

    fun connect(context: Context, username: String, password: String) {
        val base = readRawConfig(context)
        // 内联 <auth-user-pass> 注入凭据（OpenVPN 3 不调用 provide_creds，需直接写进配置）
        val config = base.replace(
            "auth-user-pass",
            "<auth-user-pass>\n$username\n$password\n</auth-user-pass>"
        )
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
