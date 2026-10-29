package com.myapps.wasaver

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class MediaFolderWatcher(
    private val watchPath: String,
    private val backupDir: File
) : FileObserver(watchPath, CREATE or MOVED_TO) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null || path.startsWith(".")) return
        val source = File(watchPath, path)
        if (source.exists() && source.isFile) {
            try {
                val dest = File(backupDir, source.name)
                if (!dest.exists() || dest.length() != source.length()) {
                    source.copyTo(dest, overwrite = true)
                }
            } catch (_: Exception) {}
        }
    }
}

class WhatsAppCatcherService : NotificationListenerService() {

    private val watchers = mutableListOf<MediaFolderWatcher>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        setupMediaWatchers()
    }

    private fun getBackupMediaDir(): File {
        val dir = File(getExternalFilesDir(null), "SavedMedia")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getWhatsAppMediaDirs(): List<File> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val paths = listOf(
            "$root/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
            "$root/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            "$root/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
            "$root/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
            "$root/WhatsApp/Media/WhatsApp Images",
            "$root/WhatsApp/Media/WhatsApp Video",
            "$root/WhatsApp/Media/WhatsApp Audio",
            "$root/WhatsApp/Media/WhatsApp Voice Notes"
        )
        return paths.map { File(it) }.filter { it.exists() }
    }

    private fun setupMediaWatchers() {
        val backupDir = getBackupMediaDir()
        for (dir in getWhatsAppMediaDirs()) {
            val watcher = MediaFolderWatcher(dir.absolutePath, backupDir)
            watcher.startWatching()
            watchers.add(watcher)
        }
    }

    // Recent files (Photos, Videos, Voice Notes subfolders) ka instant backup
    private fun backupRecentWhatsAppFiles() {
        Thread {
            try {
                val backupDir = getBackupMediaDir()
                val now = System.currentTimeMillis()
                val twoMinsAgo = now - (2 * 60 * 1000)

                for (dir in getWhatsAppMediaDirs()) {
                    dir.walkTopDown().maxDepth(2).forEach { file ->
                        if (file.isFile && !file.name.startsWith(".") && file.lastModified() >= twoMinsAgo) {
                            val dest = File(backupDir, file.name)
                            if (!dest.exists() || dest.length() != file.length()) {
                                file.copyTo(dest, overwrite = true)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (sender == "WhatsApp" ||
            text.contains("new messages") ||
            text == "Checking for new messages" ||
            text.contains("Backup in progress")) {
            return
        }

        // Har notification aane par media folder scan karein (1s aur 4s baad bhi)
        backupRecentWhatsAppFiles()
        handler.postDelayed({ backupRecentWhatsAppFiles() }, 1500)
        handler.postDelayed({ backupRecentWhatsAppFiles() }, 4000)

        // Agar samne wale ne Delete for Everyone kiya
        if (text == "This message was deleted" || text == "You deleted this message") {
            markLatestMessageDeleted(sender)
            return
        }

        // Notification ke andar chhupi Photo Bitmap save karein
        var thumbPath = ""
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
        }

        if (bitmap != null) {
            thumbPath = saveNotificationBitmap(bitmap)
        }

        saveMessageLog(sender, text, thumbPath)
    }

    private fun saveNotificationBitmap(bitmap: Bitmap): String {
        return try {
            val dir = getBackupMediaDir()
            val file = File(dir, "NOTIF_IMG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveMessageLog(sender: String, message: String, thumbPath: String) {
        val prefs = getSharedPreferences("WA_SAVER_DB", MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("chat_logs", "[]"))

        if (arr.length() > 0) {
            val top = arr.getJSONObject(0)
            if (top.optString("sender") == sender && top.optString("message") == message) {
                return
            }
        }

        val time = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date())
        val obj = JSONObject().apply {
            put("sender", sender)
            put("message", message)
            put("time", time)
            put("thumb", thumbPath)
            put("deleted", false)
        }

        val updated = JSONArray().put(obj)
        val limit = minOf(arr.length(), 500)
        for (i in 0 until limit) {
            updated.put(arr.getJSONObject(i))
        }

        prefs.edit().putString("chat_logs", updated.toString()).apply()
    }

    private fun markLatestMessageDeleted(sender: String) {
        val prefs = getSharedPreferences("WA_SAVER_DB", MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("chat_logs", "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("sender") == sender && !obj.optBoolean("deleted", false)) {
                obj.put("deleted", true)
                break
            }
        }
        prefs.edit().putString("chat_logs", arr.toString()).apply()
    }
}
