package com.pathea.assistant

import android.content.Context
import android.content.SharedPreferences

/**
 * 存储 VPN / AD 域账号密码。
 * 注：当前使用普通 SharedPreferences 存储，后续可升级为 Keystore 加密。
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vpn_credentials", Context.MODE_PRIVATE)

    fun save(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun username(): String? = prefs.getString(KEY_USERNAME, null)

    fun password(): String? = prefs.getString(KEY_PASSWORD, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
