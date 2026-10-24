package id.dst.googleunlimited.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.security.MessageDigest

/** Same 41-format whitelist as the desktop watcher, files >= 10 KB. */
val MEDIA_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "tiff", "tif",
    "svg", "ico", "jfif", "avif",
    "dng", "raw", "cr2", "cr3", "nef", "arw", "orf", "rw2", "pef", "srw", "raf",
    "mp4", "mov", "mkv", "avi", "m4v", "3gp", "3g2", "wmv", "webm",
    "mpg", "mpeg", "mts", "m2ts", "ts", "flv", "vob"
)

data class MediaItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    /** Real filesystem path when MediaStore exposes one, else null (then we copy to cache). */
    val path: String?,
    val modifiedEpochSec: Long
)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

/** Streaming SHA-256 so a 4 GB video never loads into RAM. */
fun sha256Of(context: Context, item: MediaItem): String? {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = when {
            item.path != null && File(item.path).exists() ->
                File(item.path).inputStream()
            else -> context.contentResolver.openInputStream(item.uri)
        } ?: return null
        stream.use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }
}

/** Scan Camera / Pictures / Downloads / Movies via MediaStore (scoped storage, no broad access). */
fun scanMedia(context: Context): List<MediaItem> {
    val out = mutableListOf<MediaItem>()
    val collections = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )
    for (collection in collections) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )
        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in MEDIA_EXTENSIONS) continue
                    val size = c.getLong(sizeCol)
                    if (size < 10 * 1024) continue
                    val path = if (dataCol >= 0) c.getString(dataCol) else null
                    out += MediaItem(
                        uri = Uri.withAppendedPath(collection, c.getLong(idCol).toString()),
                        name = name, size = size, path = path,
                        modifiedEpochSec = c.getLong(modCol)
                    )
                }
            }
        } catch (_: Exception) {
            // One collection failing must not kill the whole scan.
        }
    }
    // Newest first, de-duplicated by content Uri.
    return out.distinctBy { it.uri }.sortedByDescending { it.modifiedEpochSec }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
