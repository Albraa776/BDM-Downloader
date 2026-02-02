package com.bdm.downloader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.CookieManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {
    
    private lateinit var urlEditText: EditText
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var qualitySpinner: Spinner
    private lateinit var downloadTypeSpinner: Spinner
    private lateinit var settingsButton: ImageButton
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    companion object {
        const val PREFS_NAME = "BDMSettings"
        const val DOWNLOAD_PATH = "download_path"
        const val DEFAULT_PATH = "BDM"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupUI()
        setupListeners()
        loadSettings()
    }
    
    private fun setupUI() {
        urlEditText = findViewById(R.id.urlEditText)
        downloadButton = findViewById(R.id.downloadButton)
        progressBar = findViewById(R.id.progressBar)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        downloadTypeSpinner = findViewById(R.id.downloadTypeSpinner)
        settingsButton = findViewById(R.id.settingsButton)
        
        downloadButton.isEnabled = false
        downloadButton.alpha = 0.5f
        
        // Setup quality options
        val qualities = arrayOf("Best", "1080p", "720p", "480p", "360p")
        val qualityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, qualities)
        qualitySpinner.adapter = qualityAdapter
        
        // Setup download type options
        val types = arrayOf("Video + Audio", "Audio Only")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        downloadTypeSpinner.adapter = typeAdapter
    }
    
    private fun setupListeners() {
        urlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateUrl(s.toString())
            }
        })
        
        downloadButton.setOnClickListener {
            startDownload()
        }
        
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun validateUrl(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val isValid = isSupportedUrl(url)
            withContext(Dispatchers.Main) {
                if (isValid) {
                    downloadButton.isEnabled = true
                    downloadButton.alpha = 1.0f
                } else {
                    downloadButton.isEnabled = false
                    downloadButton.alpha = 0.5f
                }
            }
        }
    }
    
    private fun isSupportedUrl(url: String): Boolean {
        val patterns = listOf(
            "tiktok.com", "youtube.com", "youtu.be",
            "instagram.com", "facebook.com", "fb.watch"
        )
        return patterns.any { url.contains(it, ignoreCase = true) }
    }
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun startDownload() {
        val url = urlEditText.text.toString()
        val quality = qualitySpinner.selectedItem.toString()
        val downloadType = downloadTypeSpinner.selectedItem.toString()
        val isAudioOnly = downloadType == "Audio Only"
        
        progressBar.visibility = View.VISIBLE
        downloadButton.isEnabled = false
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videoInfo = extractVideoInfo(url, quality, isAudioOnly)
                withContext(Dispatchers.Main) {
                    if (videoInfo != null) {
                        initiateDownload(videoInfo)
                    } else {
                        showToast("Failed to extract video information")
                        resetUI()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleTikTokIssue(e, url, quality, isAudioOnly)
                }
            }
        }
    }
    
    private fun extractVideoInfo(url: String, quality: String, isAudioOnly: Boolean): VideoInfo? {
        // Implementation for different platforms
        return when {
            url.contains("tiktok.com") -> extractTikTokInfo(url)
            url.contains("youtube.com") || url.contains("youtu.be") -> extractYouTubeInfo(url, quality, isAudioOnly)
            url.contains("instagram.com") -> extractInstagramInfo(url)
            url.contains("facebook.com") || url.contains("fb.watch") -> extractFacebookInfo(url)
            else -> null
        }
    }
    
    private fun extractTikTokInfo(url: String): VideoInfo? {
        // TikTok extraction with VPN handling
        val apiUrl = "https://www.tiktok.com/oembed?url=$url"
        
        return try {
            val connection = URL(apiUrl).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            if (connection.responseCode == 403) {
                throw Exception("VPN required for TikTok")
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Extract video URL from TikTok
            VideoInfo(
                url = extractTikTokVideoUrl(json),
                title = json.optString("title", "tiktok_video"),
                platform = "TikTok"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun handleTikTokIssue(e: Exception, url: String, quality: String, isAudioOnly: Boolean) {
        if (e.message?.contains("VPN") == true) {
            AlertDialog.Builder(this)
                .setTitle("VPN Required")
                .setMessage("TikTok requires VPN access in your region. Please enable VPN and try again.")
                .setPositiveButton("Retry") { _, _ ->
                    startDownload()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    resetUI()
                }
                .show()
        } else {
            showToast("Error: ${e.message}")
            resetUI()
        }
    }
    
    private fun initiateDownload(videoInfo: VideoInfo) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val basePath = prefs.getString(DOWNLOAD_PATH, 
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/BDM")
        
        val fileName = "${videoInfo.title}_${System.currentTimeMillis()}.${if(videoInfo.isAudio) "mp3" else "mp4"}"
        val file = File("$basePath/$fileName")
        
        val request = DownloadManager.Request(Uri.parse(videoInfo.url))
            .setTitle(videoInfo.title)
            .setDescription("Downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
        
        // Register download receiver
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    showToast("Download completed!")
                    resetUI()
                }
            }
        }
        
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
    
    private fun resetUI() {
        progressBar.visibility = View.GONE
        downloadButton.isEnabled = true
        urlEditText.text.clear()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun loadSettings() {
        // Ensure directory exists
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(DOWNLOAD_PATH, 
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/BDM")
        
        val dir = File(path!!)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            unregisterReceiver(it)
        }
    }
    
    data class VideoInfo(
        val url: String,
        val title: String,
        val platform: String,
        val isAudio: Boolean = false
    )
}
