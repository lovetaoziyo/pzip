package com.pathea.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pathea.assistant.databinding.ActivityMainBinding
import com.tim.basevpn.state.ConnectionState
import com.tim.openvpn.connection.OpenVPNVpnConnection
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private var pendingConnect: Pair<String, String>? = null
    private var vpnConnection: OpenVPNVpnConnection? = null
    private var lastVpnState: ConnectionState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = CredentialStore(this)

        binding.usernameInput.setText(credentialStore.username() ?: "")
        binding.passwordInput.setText(credentialStore.password() ?: "")
        binding.rememberCheckbox.isChecked = credentialStore.password() != null

        binding.connectButton.setOnClickListener { onConnectClick() }
        binding.shortcutAd.setOnClickListener { openWeb(getString(R.string.service_ad), URL_AD) }
        binding.shortcutItsm.setOnClickListener { openWeb(getString(R.string.service_itsm), URL_ITSM) }
        binding.shortcutPortal.setOnClickListener { openWeb(getString(R.string.service_portal), URL_PORTAL) }
    }

    private fun onConnectClick() {
        if (isVpnActive()) {
            disconnect()
            return
        }

        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.vpn_need_credential, Toast.LENGTH_SHORT).show()
            return
        }

        if (binding.rememberCheckbox.isChecked) {
            credentialStore.save(username, password)
        } else {
            credentialStore.clear()
        }

        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingConnect = Pair(username, password)
            startActivityForResult(prepare, VPN_REQUEST_CODE)
            return
        }
        doConnect(username, password)
    }

    private fun doConnect(username: String, password: String) {
        setVpnUi(ConnectionState.CONNECTING)
        try {
            VpnHelper.connect(this, username, password)
            Toast.makeText(this, "连接已启动", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            setVpnUi(ConnectionState.DISCONNECTED)
            Toast.makeText(
                this,
                "VPN启动失败: ${t.javaClass.simpleName}: ${t.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // 监听真实连接状态
        vpnConnection = OpenVPNVpnConnection(this) { state ->
            runOnUiThread { onVpnState(state) }
        }
        vpnConnection?.attachListener()
        // 延迟检查是否有底层错误
        Handler(Looper.getMainLooper()).postDelayed({
            val errFile = File(getExternalFilesDir(null) ?: filesDir, "openvpn-error.log")
            if (errFile.exists() && errFile.length() > 0) {
                AlertDialog.Builder(this)
                    .setTitle("OpenVPN 底层错误")
                    .setMessage(errFile.readText())
                    .setPositiveButton("关闭", null)
                    .show()
            }
        }, 4000)
    }

    private fun disconnect() {
        vpnConnection?.stopServiceIfNeed(true)
        vpnConnection = null
        VpnHelper.disconnect(this)
        setVpnUi(ConnectionState.DISCONNECTED)
    }

    private fun onVpnState(state: ConnectionState) {
        setVpnUi(state)
        when (state) {
            ConnectionState.PERMISSION_NOT_GRANTED ->
                Toast.makeText(this, R.string.vpn_permission_hint, Toast.LENGTH_LONG).show()
            ConnectionState.DISCONNECTED -> {
                if (lastVpnState == ConnectionState.CONNECTING) {
                    showVpnError()
                }
            }
            else -> Unit
        }
        lastVpnState = state
    }

    private fun showVpnError() {
        Handler(Looper.getMainLooper()).postDelayed({
            val logFile = File(getExternalFilesDir(null) ?: filesDir, "openvpn.log")
            val tail = if (logFile.exists()) {
                logFile.readLines().takeLast(25).joinToString("\n")
            } else {
                ""
            }
            val msg = if (tail.isBlank()) {
                "（日志为空，说明在配置解析阶段就失败了）"
            } else {
                tail
            }
            AlertDialog.Builder(this)
                .setTitle("VPN 连接失败")
                .setMessage(msg)
                .setPositiveButton("关闭", null)
                .show()
        }, 1200)
    }

    private fun isVpnActive(): Boolean {
        return binding.connectButton.text.toString() == getString(R.string.vpn_disconnect)
    }

    private fun setVpnUi(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTING -> {
                binding.vpnStatusText.text = getString(R.string.vpn_connecting)
                binding.connectButton.isEnabled = false
                binding.connectButton.text = getString(R.string.vpn_connecting)
            }
            ConnectionState.CONNECTED -> {
                binding.vpnStatusText.text = getString(R.string.vpn_connected)
                binding.connectButton.isEnabled = true
                binding.connectButton.text = getString(R.string.vpn_disconnect)
            }
            else -> {
                binding.vpnStatusText.text = getString(R.string.vpn_disconnected)
                binding.connectButton.isEnabled = true
                binding.connectButton.text = getString(R.string.vpn_connect)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Toast.makeText(this, "授权结果: $resultCode", Toast.LENGTH_SHORT).show()
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            pendingConnect?.let { (u, p) -> doConnect(u, p) }
            pendingConnect = null
        } else {
            pendingConnect = null
        }
    }

    private fun openWeb(title: String, url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra(WebActivity.EXTRA_TITLE, title)
        intent.putExtra(WebActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    companion object {
        private const val VPN_REQUEST_CODE = 24
        const val URL_AD = "https://account.patheagames.com:9252"
        const val URL_ITSM = "https://itsm.pathea.com"
        const val URL_PORTAL = "https://hao.pathea.com"
    }
}
