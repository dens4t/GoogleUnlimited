package id.dst.googleunlimited

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash fix 1.0.1: previously an uncaught exception just killed the app with
 * no trace for us to diagnose. Now the stack trace is saved to the app-private
 * crash.log BEFORE the system handler runs, so the next launch can show it in
 * the "Akun & Log" tab and the user can tell us exactly what crashed.
 */
class GuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                File(filesDir, "crash.log").appendText(
                    "\n=== CRASH $stamp thread=${thread.name} ===\n${sw}\n"
                )
            } catch (_: Exception) {
                // Never let crash logging itself cause another crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
