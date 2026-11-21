package com.pathea.assistant

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pathea.assistant.databinding.ActivityMainBinding
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.VpnStatus

class MainActivity : AppCompatActivity(), VpnStatus.StateListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private var pendingConnect: Pair<String, String>? = null

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

        VpnStatus.addStateListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        VpnStatus.removeStateListener(this)
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
        }
    }

    private fun disconnect() {
        VpnHelper.disconnect(this)
        setDisconnected()
    }

    override fun updateState(state: String?, logmessage: String?, resid: Int, level: ConnectionStatus?) {
        runOnUiThread {
            when (level) {
                ConnectionStatus.LEVEL_CONNECTED -> setConnected()
                ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    setDisconnected()
                    Toast.makeText(this, R.string.vpn_auth_failed, Toast.LENGTH_LONG).show()
                }
                ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
                ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
                ConnectionStatus.LEVEL_START -> setConnecting()
                else -> setDisconnected()
            }
        }
    }

    private fun isVpnActive(): Boolean {
        return binding.connectButton.text.toString() == getString(R.string.vpn_disconnect)
    }

    private fun setConnecting() {
        binding.vpnStatusText.text = getString(R.string.vpn_connecting)
        binding.connectButton.isEnabled = false
        binding.connectButton.text = getString(R.string.vpn_connecting)
    }

    private fun setConnected() {
        binding.vpnStatusText.text = getString(R.string.vpn_connected)
        binding.connectButton.isEnabled = true
        binding.connectButton.text = getString(R.string.vpn_disconnect)
    }

    private fun setDisconnected() {
        binding.vpnStatusText.text = getString(R.string.vpn_disconnected)
        binding.connectButton.isEnabled = true
        binding.connectButton.text = getString(R.string.vpn_connect)
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
        const val URL_AD = "https://account.patheagames.com:9252"
        const val URL_ITSM = "https://itsm.pathea.com"
        const val URL_PORTAL = "https://hao.pathea.com"
    }
}
