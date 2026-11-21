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
        val ovpnConfig = OpenVPNConfig(
            configuration = base,
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
