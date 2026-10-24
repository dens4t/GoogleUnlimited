package id.dst.googleunlimited.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mirrors the upstream desktop schema (gphotos.db): uploads / retry_queue / logs. */
data class UploadRow(
    val path: String,
    val mediaKey: String?,
    val uploadedAt: String,
    val fileName: String,
    val fileSize: Long,
    val contentHash: String?
)

data class LogRow(val timestamp: String, val level: String, val message: String)

class GuDb(context: Context) : SQLiteOpenHelper(context, "gphotos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA journal_mode=WAL")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS uploads (
                path TEXT PRIMARY KEY, media_key TEXT,
                uploaded_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                file_name TEXT, file_size INTEGER, content_hash TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS retry_queue (
                path TEXT PRIMARY KEY, attempts INTEGER NOT NULL DEFAULT 0,
                last_attempt TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                level TEXT NOT NULL, message TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_uploads_file ON uploads(file_name, file_size)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_uploads_hash ON uploads(content_hash)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun log(level: String, message: String) {
        writableDatabase.insert(
            "logs", null,
            ContentValues().apply {
                put("timestamp", now()); put("level", level); put("message", message)
            }
        )
        // Keep the log table small on a phone: same 30-day spirit as upstream, capped at 500 rows.
        writableDatabase.execSQL(
            "DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT 500)"
        )
    }

    fun logs(limit: Int = 60): List<LogRow> {
        val out = mutableListOf<LogRow>()
        readableDatabase.query(
            "logs", arrayOf("timestamp", "level", "message"),
            null, null, null, null, "id DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) out += LogRow(c.getString(0), c.getString(1), c.getString(2))
        }
        return out
    }

    fun isUploaded(path: String, fileName: String, size: Long, hash: String?): Boolean {
        readableDatabase.query(
            "uploads", arrayOf("file_size", "content_hash"), "path = ?",
            arrayOf(path), null, null, null, "1"
        ).use { c ->
            if (c.moveToFirst()) {
                val oldSize = c.getLong(0)
                val oldHash = if (c.isNull(1)) null else c.getString(1)
                if (oldSize == size && (oldHash == null || hash == null || oldHash == hash)) return true
            }
        }
        // Cross-path dedup, like upstream: same name + size + hash = already backed up.
        if (hash != null) {
            readableDatabase.query(
                "uploads", arrayOf("path"), "file_name = ? AND file_size = ? AND content_hash = ?",
                arrayOf(fileName, size.toString(), hash), null, null, null, "1"
            ).use { if (it.moveToFirst()) return true }
        }
        return false
    }

    fun saveUpload(path: String, mediaKey: String?, fileName: String, size: Long, hash: String?) {
        writableDatabase.insertWithOnConflict(
            "uploads", null,
            ContentValues().apply {
                put("path", path); put("media_key", mediaKey); put("uploaded_at", now())
                put("file_name", fileName); put("file_size", size); put("content_hash", hash)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        writableDatabase.delete("retry_queue", "path = ?", arrayOf(path))
    }

    fun retryCount(path: String): Int {
        readableDatabase.query(
            "retry_queue", arrayOf("attempts"), "path = ?",
            arrayOf(path), null, null, null, "1"
        ).use { if (it.moveToFirst()) return it.getInt(0) }
        return 0
    }

    /** Returns true when the item gave up (3 attempts, same as upstream). */
    fun saveRetry(path: String): Boolean {
        val attempts = retryCount(path) + 1
        writableDatabase.insertWithOnConflict(
            "retry_queue", null,
            ContentValues().apply {
                put("path", path); put("attempts", attempts); put("last_attempt", now())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
        if (attempts >= 3) {
            writableDatabase.delete("retry_queue", "path = ?", arrayOf(path))
            return true
        }
        return false
    }

    fun stats(): Triple<Int, Int, Int> {
        fun count(table: String): Int {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                return if (it.moveToFirst()) it.getInt(0) else 0
            }
        }
        return Triple(count("uploads"), count("retry_queue"), count("logs"))
    }

    fun recentUploads(limit: Int = 20): List<UploadRow> {
        val out = mutableListOf<UploadRow>()
        readableDatabase.query(
            "uploads",
            arrayOf("path", "media_key", "uploaded_at", "file_name", "file_size", "content_hash"),
            null, null, null, null, "uploaded_at DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) out += UploadRow(
                c.getString(0), if (c.isNull(1)) null else c.getString(1), c.getString(2),
                c.getString(3) ?: "", c.getLong(4), if (c.isNull(5)) null else c.getString(5)
            )
        }
        return out
    }
}
