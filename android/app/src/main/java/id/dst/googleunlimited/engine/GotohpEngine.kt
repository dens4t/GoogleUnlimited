package id.dst.googleunlimited.engine

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the upstream gotohp-cli binary, mirroring google-unlimited.sh:
 *   gotohp-cli --config <gotohp.config> upload --no-tui <file>
 *   gotohp-cli --config <gotohp.config> creds list / creds add -
 *
 * The binary is NOT bundled in the APK (keeps it light, ~few MB). On first setup
 * the app downloads the official gotohp v0.11.0 Linux arm64 build straight from
 * github.com/xob0t/gotohp/releases over HTTPS and prints its SHA-256 in the UI,
 * so the user can compare it with the release page. Upstream publishes no
 * arm64 checksum in its own script, so no fake "verified" claim is made here.
 * A Go static binary may still refuse to run on some devices; [probe] reports
 * that honestly instead of pretending sync works.
 */
object GotohpEngine {
    const val VERSION = "v0.11.0"
    const val DOWNLOAD_URL =
        "https://github.com/xob0t/gotohp/releases/download/v0.11.0/gotohp-cli_arm64"

    fun binaryFile(context: Context): File = File(context.filesDir, "gotohp-cli_arm64")
    fun configFile(context: Context): File = File(context.filesDir, "gotohp.config")

    fun isInstalled(context: Context): Boolean =
        binaryFile(context).let { it.exists() && it.length() > 0 && it.canExecute() }

    fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
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

    /** Download (in the calling coroutine, on IO) — returns SHA-256 or throws. */
    fun download(context: Context): String {
        val tmp = File(context.filesDir, "gotohp-cli_arm64.tmp")
        val url = URL(DOWNLOAD_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        val target = binaryFile(context)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        target.setExecutable(true, true)
        return sha256Of(target) ?: throw IllegalStateException("SHA-256 gagal dihitung")
    }

    data class CmdResult(val exitCode: Int, val output: String)

    private fun run(context: Context, args: List<String>, stdin: String? = null): CmdResult {
        val bin = binaryFile(context)
        val cmd = mutableListOf(bin.absolutePath)
        val cfg = configFile(context)
        if (cfg.exists()) {
            cmd += "--config"; cmd += cfg.absolutePath
        }
        cmd += args
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        if (stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(stdin) }
        } else {
            process.outputStream.close()
        }
        val finished = process.waitFor(10, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            return CmdResult(-1, "timeout 10 menit")
        }
        val output = process.inputStream.bufferedReader().readText()
        return CmdResult(process.exitValue(), output)
    }

    /** Returns "OK: <version line>" or an honest error string. */
    fun probe(context: Context): String {
        if (!isInstalled(context)) return "Belum terpasang"
        return try {
            val r = run(context, listOf("version"))
            if (r.exitCode == 0) "OK: ${r.output.trim().lineSequence().firstOrNull() ?: "gotohp-cli"}"
            else "Gagal jalan di HP ini (exit ${r.exitCode}): ${r.output.trim().take(200)}"
        } catch (e: Exception) {
            "Gagal jalan di HP ini: ${e.message}"
        }
    }

    fun credsList(context: Context): CmdResult = run(context, listOf("creds", "list"))

    fun credsAdd(context: Context, token: String): CmdResult =
        run(context, listOf("creds", "add", "-"), stdin = token.trim() + "\n")

    data class UploadOutcome(val success: Boolean, val mediaKey: String?, val error: String?)

    fun upload(context: Context, file: File): UploadOutcome {
        val r = run(context, listOf("upload", "--no-tui", file.absolutePath))
        val mediaKey = Regex(""""mediaKey":\s*"([^"]*)"""").find(r.output)?.groupValues?.get(1)
        val err = Regex(""""error":\s*"([^"]*)"""").find(r.output)?.groupValues?.get(1)
            ?: Regex(""""message":\s*"([^"]*)"""").find(r.output)?.groupValues?.get(1)
        val ok = r.exitCode == 0 && err.isNullOrEmpty() &&
            (r.output.contains("\"success\": true") || !mediaKey.isNullOrEmpty())
        return UploadOutcome(ok, mediaKey, err ?: if (!ok) "exit ${r.exitCode}" else null)
    }
}
