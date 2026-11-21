package com.pathea.assistant

import android.content.Context
import android.content.Intent
import de.blinkt.openvpn.activities.DisconnectVPN
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import java.io.StringReader

/**
 * 一键连接 / 断开 OpenVPN（基于 ics-openvpn，OpenVPN 2.x）。
 * 原生支持 LDAP 用户名密码 + 标准 OpenVPN 配置（tls-crypt / data-ciphers / tun-mtu 等）。
 */
object VpnHelper {

    fun connect(context: Context, username: String, password: String) {
        val config = readRawConfig(context)

        // 解析标准 .ovpn 配置
        val parser = ConfigParser()
        parser.parseConfig(StringReader(config))
        val profile = parser.convertProfile()
        profile.mName = "Pathea VPN"
        profile.mUsername = username
        profile.mPassword = password

        // 保存 profile（VPN 服务运行在独立进程，需持久化到磁盘）
        val pm = ProfileManager.getInstance(context)
        pm.addProfile(profile)
        pm.saveProfile(context, profile)

        // 启动 VPN
        VPNLaunchHelper.startOpenVpn(profile, context)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, DisconnectVPN::class.java)
        intent.setAction(OpenVPNService.DISCONNECT_VPN)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun readRawConfig(context: Context): String {
        return context.resources.openRawResource(R.raw.pathea)
            .bufferedReader().use { it.readText() }
    }
}
