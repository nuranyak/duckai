package com.github.nuranyak.duckai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONTokener
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date


@SuppressLint("SetJavaScriptEnabled")
class MainActivity : AppCompatActivity() {

    private var currentUiMode = 0

    private var contentToSave: ByteArray? = null

    private val contentSaveActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        it.data?.data?.let { uri ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                        FileOutputStream(descriptor.fileDescriptor).use { stream ->
                            if (contentToSave != null) {
                                stream.write(contentToSave)
                                contentToSave = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        currentUiMode = resources.configuration.uiMode

        val context: Context = this

        setContentView(WebView(context).apply {
            loadUrl(CHAT_URL)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = WebSettings.getDefaultUserAgent(context) + " like DuckDuckGo/5"
            }

            setDownloadListener { url, _, _, mimetype, _ ->
                if (mimetype == "text/plain") {
                    downloadPlainText(url)
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun downloadPlainText(url: String) {
        // HACK: to get the content of the "blob:" url, we have to create a second WebView
        WebView(this).apply {
            settings.javaScriptEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    evaluateJavascript("document.documentElement.textContent") {
                        val content = try {
                            JSONTokener(it).nextValue() as String
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            it
                        }
                        saveText(content)
                    }
                }
            }

            loadUrl(url)
        }
    }

    private fun saveText(content: String) {
        val text = URLDecoder.decode(content, "UTF-8")

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"

            val date = SimpleDateFormat.getDateTimeInstance().format(Date())

            putExtra(Intent.EXTRA_TITLE, "DDG Chat Export $date.txt")
        }

        contentToSave = text.toByteArray()
        contentSaveActivityLauncher.launch(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode == currentUiMode) return

        currentUiMode = newConfig.uiMode

        updateWindowBackground()
        enableEdgeToEdge() // to fix system bars colors
    }

    private fun updateWindowBackground() {
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))

        typedArray.getDrawable(0).let {
            window.setBackgroundDrawable(it)
        }

        typedArray.close()
    }

    companion object {
        // The current url was extracted from
        // https://github.com/duckduckgo/Android/blob/5.232.0/duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/RealDuckChat.kt#L367
        //
        // "q=DuckDuckGo+AI+Chat" is needed to display search results instead of the home page
        // "ia=chat" is needed to open the chat
        // The "duckai" parameter defines the source for the correct appearance:
        // 1 = https://duck.ai, 2 = DDG Mac Browser, 3 = DDG Windows Browser, 4 = DDG iOS Browser,
        // 5 = DDG Android Browser, no parameter (or other value) = the "Chat" button on the web results
        private const val CHAT_URL = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    }
}