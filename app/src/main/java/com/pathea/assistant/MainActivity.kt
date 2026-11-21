package com.pathea.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pathea.assistant.databinding.ActivityMainBinding
import com.tim.basevpn.state.ConnectionState
import com.tim.openvpn.connection.OpenVPNVpnConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private var pendingConnect: Pair<String, String>? = null
    private var vpnConnection: OpenVPNVpnConnection? = null
    private var vpnConnected = false
    private var intranetConnected = false
    private var credentialsSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = CredentialStore(this)

        val savedUser = credentialStore.username()
        val savedPass = credentialStore.password()
        credentialsSaved = savedUser != null && savedPass != null

        binding.usernameInput.setText(savedUser ?: "")
        binding.passwordInput.setText(savedPass ?: "")
        binding.rememberCheckbox.isChecked = credentialsSaved

        binding.connectButton.setOnClickListener { onSaveClick() }
        binding.vpnStatusText.setOnClickListener { onStatusClick() }
        binding.shortcutAd.setOnClickListener { openWeb(getString(R.string.service_ad), URL_AD) }
        binding.shortcutItsm.setOnClickListener { openWeb(getString(R.string.service_itsm), URL_ITSM) }
        binding.shortcutPortal.setOnClickListener { openWeb(getString(R.string.service_portal), URL_PORTAL) }
        binding.shortcutWiki.setOnClickListener { openWeb(getString(R.string.service_wiki), URL_WIKI) }
        binding.shortcutRustdesk.setOnClickListener { onRustdeskClick() }
        binding.rustdeskDownloadNote.setOnClickListener { openWeb("RustDesk 下载", RUSTDESK_URL) }

        if (credentialsSaved) {
            collapseCard()
            pingCheck()
        } else {
            showCard()
        }
    }

    private fun onSaveClick() {
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.vpn_need_credential, Toast.LENGTH_SHORT).show()
            return
        }
        credentialStore.save(username, password)
        credentialsSaved = true
        collapseCard()
        pingCheck()
    }

    private fun pingCheck() {
        setStatusText(getString(R.string.checking_network))
        lifecycleScope.launch(Dispatchers.IO) {
            val reachable = isReachable(INTRANET_IP)
            withContext(Dispatchers.Main) {
                intranetConnected = reachable
                if (reachable) {
                    setStatusText(getString(R.string.vpn_connected))
                } else {
                    setStatusText(getString(R.string.vpn_disconnected))
                    showVpnNeededDialog()
                }
            }
        }
    }

    private fun showVpnNeededDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.vpn_needed_title)
            .setMessage(R.string.vpn_needed_message)
            .setPositiveButton(R.string.vpn_connect) { _, _ -> connectVpn() }
            .setNegativeButton(R.string.vpn_modify_account) { _, _ -> showCard() }
            .show()
    }

    private fun connectVpn() {
        val username = credentialStore.username()
        val password = credentialStore.password()
        if (username == null || password == null) {
            showCard()
            return
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
        setConnecting()
        try {
            VpnHelper.connect(this, username, password)
        } catch (t: Throwable) {
            setDisconnected()
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
        vpnConnected = false
        setDisconnected()
    }

    private fun onStatusClick() {
        when {
            vpnConnected -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.vpn_status_dialog_title)
                    .setPositiveButton(R.string.vpn_disconnect) { _, _ -> disconnect() }
                    .setNegativeButton(R.string.vpn_modify_account) { _, _ ->
                        disconnect()
                        showCard()
                        binding.usernameInput.requestFocus()
                    }
                    .show()
            }
            intranetConnected -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.intranet_title)
                    .setMessage(R.string.intranet_message)
                    .setPositiveButton(R.string.vpn_modify_account) { _, _ -> showCard() }
                    .setNegativeButton("关闭", null)
                    .show()
            }
            else -> showVpnNeededDialog()
        }
    }

    private fun onVpnState(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTED -> {
                vpnConnected = true
                setStatusText(getString(R.string.vpn_connected))
                collapseCard()
            }
            ConnectionState.CONNECTING -> setConnecting()
            else -> {
                vpnConnected = false
                setDisconnected()
            }
        }
    }

    private fun showCard() {
        binding.vpnCard.visibility = View.VISIBLE
    }

    private fun collapseCard() {
        binding.vpnCard.visibility = View.GONE
    }

    private fun setStatusText(text: String) {
        binding.vpnStatusText.text = text
    }

    private fun setConnecting() {
        setStatusText(getString(R.string.vpn_connecting))
        binding.connectButton.isEnabled = false
        binding.connectButton.text = getString(R.string.vpn_connecting)
    }

    private fun setDisconnected() {
        setStatusText(getString(R.string.vpn_disconnected))
        binding.connectButton.isEnabled = true
        binding.connectButton.text = getString(R.string.login_save)
    }

    private fun isReachable(host: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", host))
            process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
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
        private const val INTRANET_IP = "10.96.8.9"
        private const val RUSTDESK_PACKAGE = "com.carriez.flutter_hbb"
        const val URL_AD = "https://account.patheagames.com:9252"
        const val URL_ITSM = "https://itsm.pathea.com"
        const val URL_PORTAL = "https://hao.pathea.com"
        const val URL_WIKI = "https://wiki.pathea.com"
        const val RUSTDESK_URL = "https://soft.pathea.com/software/rustdesk-1.5.3.apk"
    }
}
