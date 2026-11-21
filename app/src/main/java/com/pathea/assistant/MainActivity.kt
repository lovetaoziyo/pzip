package com.pathea.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pathea.assistant.databinding.ActivityMainBinding
import com.tim.basevpn.state.ConnectionState
import com.tim.openvpn.connection.OpenVPNVpnConnection

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private var pendingConnect: Pair<String, String>? = null
    private var vpnConnection: OpenVPNVpnConnection? = null
    private var vpnConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = CredentialStore(this)

        binding.usernameInput.setText(credentialStore.username() ?: "")
        binding.passwordInput.setText(credentialStore.password() ?: "")
        binding.rememberCheckbox.isChecked = credentialStore.password() != null

        binding.connectButton.setOnClickListener { onConnectClick() }
        binding.vpnStatusText.setOnClickListener { onStatusClick() }
        binding.shortcutAd.setOnClickListener { openWeb(getString(R.string.service_ad), URL_AD) }
        binding.shortcutItsm.setOnClickListener { openWeb(getString(R.string.service_itsm), URL_ITSM) }
        binding.shortcutPortal.setOnClickListener { openWeb(getString(R.string.service_portal), URL_PORTAL) }
        binding.shortcutWiki.setOnClickListener { openWeb(getString(R.string.service_wiki), URL_WIKI) }
        binding.shortcutRustdesk.setOnClickListener { onRustdeskClick() }
    }

    private fun onConnectClick() {
        if (vpnConnected) {
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
        } catch (t: Throwable) {
            setVpnUi(ConnectionState.DISCONNECTED)
            Toast.makeText(
                this,
                "VPN启动失败: ${t.javaClass.simpleName}: ${t.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        vpnConnection = OpenVPNVpnConnection(this) { state ->
            runOnUiThread { onVpnState(state) }
        }
        vpnConnection?.attachListener()
    }

    private fun disconnect() {
        vpnConnection?.stopServiceIfNeed(true)
        vpnConnection = null
        VpnHelper.disconnect(this)
        setVpnUi(ConnectionState.DISCONNECTED)
    }

    private fun onStatusClick() {
        if (vpnConnected) {
            showVpnStatusDialog()
        }
    }

    private fun showVpnStatusDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.vpn_status_dialog_title)
            .setPositiveButton(R.string.vpn_disconnect) { _, _ -> disconnect() }
            .setNegativeButton(R.string.vpn_modify_account) { _, _ ->
                disconnect()
                binding.usernameInput.requestFocus()
            }
            .show()
    }

    private fun onVpnState(state: ConnectionState) {
        setVpnUi(state)
    }

    private fun setVpnUi(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTING -> {
                vpnConnected = false
                binding.vpnStatusText.text = getString(R.string.vpn_connecting)
                binding.vpnCard.visibility = View.VISIBLE
                binding.connectButton.isEnabled = false
                binding.connectButton.text = getString(R.string.vpn_connecting)
            }
            ConnectionState.CONNECTED -> {
                vpnConnected = true
                binding.vpnStatusText.text = getString(R.string.vpn_connected)
                binding.vpnCard.visibility = View.GONE
            }
            else -> {
                vpnConnected = false
                binding.vpnStatusText.text = getString(R.string.vpn_disconnected)
                binding.vpnCard.visibility = View.VISIBLE
                binding.connectButton.isEnabled = true
                binding.connectButton.text = getString(R.string.vpn_connect)
            }
        }
    }

    private fun onRustdeskClick() {
        val intent = packageManager.getLaunchIntentForPackage(RUSTDESK_PACKAGE)
        if (intent != null) {
            startActivity(intent)
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.rustdesk_not_installed)
                .setMessage(R.string.rustdesk_install_note)
                .setPositiveButton(R.string.rustdesk_download) { _, _ ->
                    openWeb("RustDesk 下载", RUSTDESK_URL)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
        private const val RUSTDESK_PACKAGE = "com.carriez.flutter_hbb"
        const val URL_AD = "https://account.patheagames.com:9252"
        const val URL_ITSM = "https://itsm.pathea.com"
        const val URL_PORTAL = "https://hao.pathea.com"
        const val URL_WIKI = "https://wiki.pathea.com"
        const val RUSTDESK_URL = "https://soft.pathea.com/software/rustdesk-1.5.3.apk"
    }
}
