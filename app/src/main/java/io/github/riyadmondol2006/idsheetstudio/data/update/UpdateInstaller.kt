package io.github.riyadmondol2006.idsheetstudio.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.github.riyadmondol2006.idsheetstudio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface InstallEvent {
    data object Success : InstallEvent
    data class Failure(val message: String) : InstallEvent
}

/**
 * The system "Do you want to install this update?" screen. Android 14+ blocks activity launches
 * from background receivers, so the running activity starts it (immediately if visible, or as
 * soon as the user returns to the app).
 */
object PendingInstallConfirmation {
    val intent = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)
}

class UpdateVerificationException(message: String) : Exception(message)

/**
 * Secure in-app update: downloads the release APK over HTTPS, verifies its SHA-256 checksum
 * published with the release, checks that it is the same app, a newer version and signed with the
 * same certificate as the installed app, and only then hands it to the system PackageInstaller,
 * which always asks the user to confirm.
 */
class UpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val dir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        val expectedHash = info.checksum?.let { fetchChecksum(it.downloadUrl) }
        val target = File(dir, "update-${info.version}.apk")
        val request = Request.Builder().url(info.apk.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: info.apk.size
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedHash != null && !actual.equals(expectedHash, ignoreCase = true)) {
                target.delete()
                throw UpdateVerificationException("Checksum mismatch. The download was corrupted or tampered with.")
            }
        }
        verifyArchive(target)
        target
    }

    private fun fetchChecksum(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not download checksum (HTTP ${response.code})")
            val text = response.body.string().trim()
            return text.split(Regex("\\s+")).firstOrNull { it.matches(Regex("[0-9a-fA-F]{64}")) }
                ?: throw UpdateVerificationException("Release checksum file is malformed.")
        }
    }

    /** Verifies package name, version and signing certificate of a downloaded APK. */
    fun verifyArchive(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw UpdateVerificationException("The downloaded file is not a valid APK.")
        if (archive.packageName != context.packageName) {
            throw UpdateVerificationException(
                "This build (${context.packageName}) can't be updated in place. Download the release from GitHub instead.",
            )
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            throw UpdateVerificationException("The downloaded version is not newer than the installed one.")
        }
        val installedCerts = certificateDigests(installed)
        val archiveCerts = certificateDigests(archive)
        if (installedCerts.isEmpty() || archiveCerts.isEmpty() || installedCerts.intersect(archiveCerts).isEmpty()) {
            throw UpdateVerificationException("Signature mismatch. The update is not signed by the IDSheet Studio developer.")
        }
    }

    /** Streams the verified APK into a PackageInstaller session; the system then asks the user. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        verifyArchive(apk)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val sha = MessageDigest.getInstance("SHA-256")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> @Suppress("DEPRECATION") info.signatures
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

    companion object {
        val events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 4)
        val installEvents: SharedFlow<InstallEvent> get() = events
    }
}

/** Receives PackageInstaller results and launches the system confirmation screen. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                PendingInstallConfirmation.intent.value = confirm
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.events.tryEmit(InstallEvent.Success)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation failed ($status)"
                if (BuildConfig.DEBUG) Log.w("IDSheetUpdate", message)
                UpdateInstaller.events.tryEmit(InstallEvent.Failure(message))
            }
        }
    }
}
