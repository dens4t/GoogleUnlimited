package id.dst.googleunlimited

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import id.dst.googleunlimited.data.GuDb
import id.dst.googleunlimited.data.MediaItem
import id.dst.googleunlimited.data.UploadRow
import id.dst.googleunlimited.data.LogRow
import id.dst.googleunlimited.data.formatSize
import id.dst.googleunlimited.data.scanMedia
import id.dst.googleunlimited.data.sha256Of
import id.dst.googleunlimited.engine.GotohpEngine
import id.dst.googleunlimited.work.SyncWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val uploadedCount: Int = 0,
    val retryCount: Int = 0,
    val queue: List<MediaItem> = emptyList(),
    val recent: List<UploadRow> = emptyList(),
    val logs: List<LogRow> = emptyList(),
    val engineStatus: String = "Belum dicek",
    val engineSha: String? = null,
    val accountText: String = "",
    val wifiOnly: Boolean = true,
    val autoSync: Boolean = false,
    val isSyncing: Boolean = false,
    val isBusy: Boolean = false,
    val progressText: String = "",
    val syncedThisRun: Int = 0,
    val failedThisRun: Int = 0,
    val message: String = ""
)

class GuViewModel(app: Application) : AndroidViewModel(app) {
    private val db = GuDb(app)
    private val prefs = app.getSharedPreferences("gu_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            wifiOnly = prefs.getBoolean("wifi_only", true),
            autoSync = prefs.getBoolean("auto_sync", false)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var syncJob: Job? = null

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            val status = GotohpEngine.probe(app)
            val sha = if (GotohpEngine.isInstalled(app)) GotohpEngine.sha256Of(GotohpEngine.binaryFile(app)) else null
            _state.update { it.copy(engineStatus = status, engineSha = sha) }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val (up, retry, _) = db.stats()
            val recent = db.recentUploads()
            val logs = db.logs()
            val scanned = scanMedia(getApplication())
            val queue = scanned.filter { item ->
                !db.isUploaded(item.uri.toString(), item.name, item.size, null)
            }
            _state.update {
                it.copy(
                    uploadedCount = up, retryCount = retry,
                    queue = queue, recent = recent, logs = logs
                )
            }
        }
    }

    fun scanOnly() {
        _state.update { it.copy(isBusy = true, message = "Memindai galeri…") }
        refresh()
        viewModelScope.launch {
            _state.update {
                it.copy(isBusy = false, message = "Pindai selesai: ${it.queue.size} file belum terupload.")
            }
        }
    }

    fun startSync() {
        val app = getApplication<Application>()
        if (!GotohpEngine.isInstalled(app)) {
            _state.update { it.copy(message = "Engine belum terpasang. Unduh dulu di bagian Akun & Engine.") }
            return
        }
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSyncing = true, syncedThisRun = 0, failedThisRun = 0, message = "") }
            val queue = _state.value.queue
            val nowSec = System.currentTimeMillis() / 1000
            for (item in queue) {
                if (nowSec - item.modifiedEpochSec < 60) continue
                _state.update { it.copy(progressText = "Mengupload: ${item.name} (${formatSize(item.size)})") }
                val key = item.uri.toString()
                val hash = sha256Of(app, item)
                if (db.isUploaded(key, item.name, item.size, hash)) continue
                val file = resolveFile(item)
                if (file == null) {
                    _state.update { it.copy(failedThisRun = it.failedThisRun + 1) }
                    continue
                }
                val outcome = GotohpEngine.upload(app, file)
                if (outcome.success) {
                    db.saveUpload(key, outcome.mediaKey, item.name, item.size, hash)
                    db.log("OK", "Terupload: ${item.name}")
                    _state.update { it.copy(syncedThisRun = it.syncedThisRun + 1) }
                } else {
                    val gaveUp = db.saveRetry(key)
                    db.log(
                        if (gaveUp) "ERROR" else "WARN",
                        "Gagal: ${item.name} (${outcome.error})"
                    )
                    _state.update { it.copy(failedThisRun = it.failedThisRun + 1) }
                }
                if (file.parentFile?.name == "gu_upload_cache") file.delete()
            }
            _state.update { it.copy(isSyncing = false, progressText = "") }
            refresh()
            _state.update {
                it.copy(message = "Selesai: ${it.syncedThisRun} terupload, ${it.failedThisRun} gagal.")
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        _state.update { it.copy(isSyncing = false, progressText = "", message = "Sync dihentikan.") }
        refresh()
    }

    fun downloadEngine() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, message = "Mengunduh gotohp-cli_arm64 dari rilis resmi…") }
            try {
                val sha = GotohpEngine.download(app)
                val status = GotohpEngine.probe(app)
                db.log("INFO", "Engine diunduh, SHA-256: $sha")
                _state.update { it.copy(engineStatus = status, engineSha = sha, isBusy = false, message = "Engine terpasang. Status: $status") }
            } catch (e: Exception) {
                _state.update { it.copy(isBusy = false, message = "Gagal unduh engine: ${e.message}") }
            }
        }
    }

    fun saveToken(token: String) {
        val app = getApplication<Application>()
        if (!GotohpEngine.isInstalled(app)) {
            _state.update { it.copy(message = "Unduh engine dulu sebelum menyimpan token.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, message = "Menyimpan kredensial…") }
            val r = GotohpEngine.credsAdd(app, token)
            // Token TIDAK pernah disimpan di prefs/log — hanya diteruskan ke gotohp.config privat.
            val list = GotohpEngine.credsList(app)
            db.log("INFO", "creds add exit=${r.exitCode}")
            _state.update {
                it.copy(
                    isBusy = false,
                    accountText = list.output.trim().take(500),
                    message = if (r.exitCode == 0) "Kredensial tersimpan di penyimpanan privat aplikasi."
                    else "creds add gagal (exit ${r.exitCode}). Cek log."
                )
            }
        }
    }

    fun listAccounts() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val list = GotohpEngine.credsList(app)
            _state.update { it.copy(accountText = list.output.trim().take(500)) }
        }
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean("wifi_only", value).apply()
        _state.update { it.copy(wifiOnly = value) }
        if (_state.value.autoSync) schedulePeriodic()
    }

    fun setAutoSync(value: Boolean) {
        prefs.edit().putBoolean("auto_sync", value).apply()
        _state.update { it.copy(autoSync = value) }
        if (value) schedulePeriodic()
        else WorkManager.getInstance(getApplication()).cancelUniqueWork("gu_periodic")
    }

    private fun schedulePeriodic() {
        val net = if (_state.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "gu_periodic", ExistingPeriodicWorkPolicy.REPLACE, req
        )
    }

    private fun resolveFile(item: MediaItem): File? {
        val app = getApplication<Application>()
        item.path?.let { p ->
            val f = File(p)
            if (f.exists() && f.canRead()) return f
        }
        return try {
            val dir = File(app.cacheDir, "gu_upload_cache").apply { mkdirs() }
            val out = File(dir, item.name)
            app.contentResolver.openInputStream(item.uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.exists() && out.length() > 0) out else null
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { db.close() } catch (_: Exception) { }
    }
}
