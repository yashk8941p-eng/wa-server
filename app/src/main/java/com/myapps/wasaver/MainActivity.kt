package com.myapps.wasaver

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var contentContainer: LinearLayout
    private lateinit var statusBanner: LinearLayout
    private var currentTab = 0 // 0 = Chats, 1 = Media
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B141A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top App Bar
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#202C33"))
            setPadding(40, 45, 40, 45)
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "🛡️ WA Anti-Delete Saver"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val refreshBtn = Button(this).apply {
            text = "🔄 Refresh"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00A884"))
            setOnClickListener { renderCurrentTab() }
        }

        header.addView(title)
        header.addView(refreshBtn)
        root.addView(header)

        // Permissions Status Area
        statusBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 10)
        }
        root.addView(statusBanner)

        // Navigation Tabs
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#202C33"))
        }

        val tabChats = Button(this).apply {
            text = "💬 Saved Chats"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00A884"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tabMedia = Button(this).apply {
            text = "📸 Photos & Voice"
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.parseColor("#202C33"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        tabChats.setOnClickListener {
            currentTab = 0
            tabChats.setBackgroundColor(Color.parseColor("#00A884"))
            tabMedia.setBackgroundColor(Color.parseColor("#202C33"))
            renderCurrentTab()
        }

        tabMedia.setOnClickListener {
            currentTab = 1
            tabMedia.setBackgroundColor(Color.parseColor("#00A884"))
            tabChats.setBackgroundColor(Color.parseColor("#202C33"))
            renderCurrentTab()
        }

        tabBar.addView(tabChats)
        tabBar.addView(tabMedia)
        root.addView(tabBar)

        // Scrollable Content Area
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 80)
        }

        scrollView.addView(contentContainer)
        root.addView(scrollView)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        renderCurrentTab()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updatePermissionBanner() {
        statusBanner.removeAllViews()

        if (!isNotificationServiceEnabled()) {
            val btn = Button(this).apply {
                text = "⚠️ Step 1: Enable Notification Access (Tap Here)"
                setBackgroundColor(Color.parseColor("#D93025"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            }
            statusBanner.addView(btn)
        }

        if (!hasStorageAccess()) {
            val btn = Button(this).apply {
                text = "⚠️ Step 2: Allow All Files Access for Photos (Tap Here)"
                setBackgroundColor(Color.parseColor("#E37400"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } else {
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ), 101
                        )
                    }
                }
            }
            statusBanner.addView(btn)
        }
    }

    private fun renderCurrentTab() {
        contentContainer.removeAllViews()
        if (currentTab == 0) {
            renderChatsList()
        } else {
            renderMediaList()
        }
    }

    private fun renderChatsList() {
        val prefs = getSharedPreferences("WA_SAVER_DB", MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("chat_logs", "[]"))

        if (arr.length() == 0) {
            val empty = TextView(this).apply {
                text = "Abhi koi message record nahi hua hai.\n\nUpar dono permissions ON karein. Ab jab bhi koi WhatsApp message bhej कर delete karega, wo yahan dikhega!"
                setTextColor(Color.GRAY)
                textSize = 15f
                setPadding(20, 80, 20, 20)
                gravity = Gravity.CENTER
            }
            contentContainer.addView(empty)
            return
        }

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val sender = obj.optString("sender")
            val msg = obj.optString("message")
            val time = obj.optString("time")
            val thumb = obj.optString("thumb")
            val isDeleted = obj.optBoolean("deleted", false)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#202C33"))
                setPadding(35, 30, 35, 30)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 25)
                layoutParams = params
            }

            if (isDeleted) {
                val badge = TextView(this).apply {
                    text = "🗑️ DELETED BY SENDER"
                    setTextColor(Color.parseColor("#FF6B6B"))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                }
                card.addView(badge)
            }

            val senderTv = TextView(this).apply {
                text = sender
                setTextColor(Color.parseColor("#00A884"))
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
            }

            val msgTv = TextView(this).apply {
                text = msg
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(0, 10, 0, 10)
            }

            val timeTv = TextView(this).apply {
                text = time
                setTextColor(Color.GRAY)
                textSize = 12f
                gravity = Gravity.END
            }

            card.addView(senderTv)
            card.addView(msgTv)

            if (thumb.isNotEmpty() && File(thumb).exists()) {
                val imgView = ImageView(this).apply {
                    val bmp = BitmapFactory.decodeFile(thumb)
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    maxHeight = 450
                    setPadding(0, 15, 0, 15)
                    setOnClickListener { showFullImageDialog(File(thumb)) }
                }
                card.addView(imgView)
            }

            card.addView(timeTv)
            contentContainer.addView(card)
        }
    }

    private fun renderMediaList() {
        val dir = File(getExternalFilesDir(null), "SavedMedia")
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Abhi koi Photo, Video ya Voice Note backup nahi hua hai.\n\nWhatsApp Settings > Storage and Data > Media Auto-Download ON rakhein taaki aane wali har photo/audio turant save ho sake."
                setTextColor(Color.GRAY)
                textSize = 15f
                setPadding(20, 80, 20, 20)
                gravity = Gravity.CENTER
            }
            contentContainer.addView(empty)
            return
        }

        for (file in files) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#202C33"))
                setPadding(35, 30, 35, 30)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 25)
                layoutParams = params
            }

            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                .format(Date(file.lastModified()))

            val nameTv = TextView(this).apply {
                text = "${file.name}  ($dateStr)"
                setTextColor(Color.LTGRAY)
                textSize = 13f
            }
            card.addView(nameTv)

            val lower = file.name.lowercase()
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                val img = ImageView(this).apply {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
                    adjustViewBounds = true
                    maxHeight = 550
                    setPadding(0, 20, 0, 10)
                    setOnClickListener { showFullImageDialog(file) }
                }
                card.addView(img)
            } else if (lower.endsWith(".opus") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                val playBtn = Button(this).apply {
                    text = "▶️ Play Voice Note / Audio"
                    setBackgroundColor(Color.parseColor("#00A884"))
                    setTextColor(Color.WHITE)
                    setOnClickListener { playAudioFile(file) }
                }
                card.addView(playBtn)
            } else {
                val info = TextView(this).apply {
                    text = "🎬 Video Saved (${file.length() / 1024} KB)\nPath: ${file.absolutePath}"
                    setTextColor(Color.WHITE)
                    setPadding(0, 15, 0, 10)
                }
                card.addView(info)
            }

            contentContainer.addView(card)
        }
    }

    private fun showFullImageDialog(file: File) {
        val img = ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            adjustViewBounds = true
        }
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setView(img)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
