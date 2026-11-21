package com.pathea.assistant

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pathea.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private var connected = false
    private var pendingConnect: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = CredentialStore(this)

        // 回填已保存的账号密码
        binding.usernameInput.setText(credentialStore.username() ?: "")
        binding.passwordInput.setText(credentialStore.password() ?: "")
        binding.rememberCheckbox.isChecked = credentialStore.password() != null

        binding.connectButton.setOnClickListener { onConnectClick() }
        binding.shortcutAd.setOnClickListener { openWeb(getString(R.string.service_ad), URL_AD) }
        binding.shortcutItsm.setOnClickListener { openWeb(getString(R.string.service_itsm), URL_ITSM) }
        binding.shortcutPortal.setOnClickListener { openWeb(getString(R.string.service_portal), URL_PORTAL) }
    }

    private fun onConnectClick() {
        if (connected) {
            VpnHelper.disconnect(this)
            connected = false
            updateVpnUi()
            return
        }

        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.vpn_need_credential, Toast.LENGTH_SHORT).show()
            return
        }

        // 记住密码
        if (binding.rememberCheckbox.isChecked) {
            credentialStore.save(username, password)
        } else {
            credentialStore.clear()
        }

        // 请求 VPN 权限（首次）
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingConnect = Pair(username, password)
            startActivityForResult(prepare, VPN_REQUEST_CODE)
            return
        }
        doConnect(username, password)
    }

    private fun doConnect(username: String, password: String) {
        connected = true
        updateVpnUi()
        VpnHelper.connect(this, username, password)
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

    private fun updateVpnUi() {
        if (connected) {
            binding.vpnStatusText.text = getString(R.string.vpn_connected)
            binding.connectButton.text = getString(R.string.vpn_disconnect)
        } else {
            binding.vpnStatusText.text = getString(R.string.vpn_disconnected)
            binding.connectButton.text = getString(R.string.vpn_connect)
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
