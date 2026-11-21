package com.pathea.assistant

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.pathea.assistant.databinding.ActivityWebBinding
import org.json.JSONObject

class WebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebBinding
    private lateinit var credentialStore: CredentialStore

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = CredentialStore(this)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: ""

        binding.titleText.text = title
        binding.backButton.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }
        binding.closeButton.setOnClickListener { finish() }

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // 内部服务使用内部 CA 证书，手机默认不信任，忽略 SSL 证书校验
                    handler?.proceed()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    request?.url?.let { view?.loadUrl(it.toString()) }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // AD 域统一账号：自动填充登录表单
                    autoFillCredentials()
                }
            }
            loadUrl(url)
        }
    }

    private fun autoFillCredentials() {
        val username = credentialStore.username() ?: return
        val password = credentialStore.password() ?: return
        val js = buildAutoFillScript(username, password)
        binding.webView.evaluateJavascript(js, null)
    }

    private fun buildAutoFillScript(username: String, password: String): String {
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        return """
            (function(){
                var u = $u, p = $p;
                var inputs = document.querySelectorAll('input');
                var userField = null, passField = null;
                for (var i = 0; i < inputs.length; i++) {
                    var el = inputs[i];
                    var t = (el.type || '').toLowerCase();
                    var n = (el.name || '').toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var ph = (el.placeholder || '').toLowerCase();
                    if (t === 'password') {
                        if (!passField) passField = el;
                    } else if (!userField && (t === 'text' || t === 'email' || t === 'tel' ||
                               n.indexOf('user') >= 0 || n.indexOf('account') >= 0 || n.indexOf('login') >= 0 || n.indexOf('email') >= 0 ||
                               id.indexOf('user') >= 0 || id.indexOf('account') >= 0 || id.indexOf('login') >= 0 || id.indexOf('email') >= 0 ||
                               ph.indexOf('user') >= 0 || ph.indexOf('account') >= 0 || ph.indexOf('账号') >= 0 || ph.indexOf('用户') >= 0)) {
                        userField = el;
                    }
                }
                function setVal(el, v) {
                    if (!el) return;
                    el.value = v;
                    el.dispatchEvent(new Event('input', {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                }
                setVal(userField, u);
                setVal(passField, p);
            })();
        """.trimIndent()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack() else super.onBackPressed()
    }
}
