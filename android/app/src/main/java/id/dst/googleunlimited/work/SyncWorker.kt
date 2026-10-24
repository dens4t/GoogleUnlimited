package id.dst.googleunlimited.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.dst.googleunlimited.data.GuDb
import id.dst.googleunlimited.data.scanMedia
import id.dst.googleunlimited.data.sha256Of
import id.dst.googleunlimited.engine.GotohpEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background sync, same rules as the desktop watcher:
 * scan -> skip files modified < 60s ago (still being written) -> skip already
 * uploaded (name+size+SHA-256) -> upload via gotohp-cli -> retry queue max 3x.
 * Runs periodically (15 menit, butuh jaringan) when auto-sync is on.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = GuDb(applicationContext)
        if (!GotohpEngine.isInstalled(applicationContext)) {
            db.log("WARN", "Sync latar dilewati: engine gotohp-cli belum terpasang")
            return@withContext Result.success()
        }
        var uploaded = 0
        var failed = 0
        val nowSec = System.currentTimeMillis() / 1000
        for (item in scanMedia(applicationContext)) {
            if (nowSec - item.modifiedEpochSec < 60) continue // file masih ditulis
            val key = item.uri.toString()
            val hash = sha256Of(applicationContext, item)
            if (db.isUploaded(key, item.name, item.size, hash)) continue
            val file = resolveFile(item) ?: continue
            val outcome = GotohpEngine.upload(applicationContext, file)
            if (outcome.success) {
                db.saveUpload(key, outcome.mediaKey, item.name, item.size, hash)
                db.log("OK", "Terupload: ${item.name}")
                uploaded++
            } else {
                val gaveUp = db.saveRetry(key)
                db.log(
                    if (gaveUp) "ERROR" else "WARN",
                    if (gaveUp) "Menyerah setelah 3x: ${item.name} (${outcome.error})"
                    else "Gagal, masuk antrean retry: ${item.name} (${outcome.error})"
                )
                failed++
            }
            if (file.parentFile?.name == "gu_upload_cache") file.delete()
        }
        db.log("INFO", "Sync latar selesai: $uploaded terupload, $failed gagal")
        Result.success()
    }

    private fun resolveFile(item: id.dst.googleunlimited.data.MediaItem): File? {
        item.path?.let { p ->
            val f = File(p)
            if (f.exists() && f.canRead()) return f
        }
        // Scoped-storage fallback: copy to a private cache file for the CLI.
        return try {
            val dir = File(applicationContext.cacheDir, "gu_upload_cache").apply { mkdirs() }
            val out = File(dir, item.name)
            applicationContext.contentResolver.openInputStream(item.uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.exists() && out.length() > 0) out else null
        } catch (_: Exception) {
            null
        }
    }
}
