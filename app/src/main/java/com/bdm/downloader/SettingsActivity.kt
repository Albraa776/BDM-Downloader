package com.bdm.downloader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var pathTextView: TextView
    private lateinit var changePathButton: Button
    private lateinit var audioQualitySpinner: Spinner
    private lateinit var maxConcurrentSpinner: Spinner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        pathTextView = findViewById(R.id.pathTextView)
        changePathButton = findViewById(R.id.changePathButton)
        audioQualitySpinner = findViewById(R.id.audioQualitySpinner)
        maxConcurrentSpinner = findViewById(R.id.maxConcurrentSpinner)
        
        setupUI()
        loadCurrentSettings()
    }
    
    private fun setupUI() {
        // Audio quality options
        val audioQualities = arrayOf("High (320kbps)", "Medium (192kbps)", "Low (128kbps)")
        val audioAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, audioQualities)
        audioQualitySpinner.adapter = audioAdapter
        
        // Concurrent downloads
        val concurrentOptions = arrayOf("1", "2", "3", "4", "5")
        val concurrentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, concurrentOptions)
        maxConcurrentSpinner.adapter = concurrentAdapter
        
        changePathButton.setOnClickListener {
            requestStoragePermission()
        }
        
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
            finish()
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 100)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, 100)
            }
        } else {
            // For older versions
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, 101)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 101 && data != null) {
                val uri = data.data
                val path = getPathFromUri(uri)
                updatePath(path)
            }
        }
    }
    
    private fun loadCurrentSettings() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val path = prefs.getString(MainActivity.DOWNLOAD_PATH, 
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/BDM")
        
        pathTextView.text = path
        
        val audioQuality = prefs.getString("audio_quality", "High (320kbps)")
        val audioIndex = (audioQualitySpinner.adapter as ArrayAdapter<String>).getPosition(audioQuality)
        audioQualitySpinner.setSelection(if (audioIndex >= 0) audioIndex else 0)
        
        val maxConcurrent = prefs.getString("max_concurrent", "2")
        val concurrentIndex = (maxConcurrentSpinner.adapter as ArrayAdapter<String>).getPosition(maxConcurrent)
        maxConcurrentSpinner.setSelection(if (concurrentIndex >= 0) concurrentIndex else 1)
    }
    
    private fun saveSettings() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        
        editor.putString("audio_quality", audioQualitySpinner.selectedItem.toString())
        editor.putString("max_concurrent", maxConcurrentSpinner.selectedItem.toString())
        
        editor.apply()
        
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
    
    private fun updatePath(path: String?) {
        if (path != null) {
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putString(MainActivity.DOWNLOAD_PATH, path).apply()
            pathTextView.text = path
            
            // Create directory if it doesn't exist
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
}
