package it.turni.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import it.turni.core.ReleaseUpdate
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateState(
    val release: ReleaseUpdate? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val ready: Boolean = false,
    val showDialog: Boolean = false,
    val message: String? = null,
)

/** Public GitHub releases only. No account, calendar or OCR data is sent. */
class UpdateRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val directory
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    private val apk
        get() = File(directory, "Shiftrail.apk")

    fun automaticCheckDue(now: Long = System.currentTimeMillis()): Boolean {
        val checked = preferences.getLong("last_check", 0)
        return now < checked || now - checked >= 24 * 60 * 60 * 1000L
    }

    suspend fun check(): ReleaseUpdate? =
        withContext(Dispatchers.IO) {
            preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
            val connection = connect("$REPOSITORY/releases/latest/download/update.json")
            try {
                if (connection.responseCode == 404) return@withContext null
                require(connection.responseCode == 200) { "UPDATE_NETWORK" }
                val buffer = ByteArray(64 * 1024 + 1)
                var count = 0
                connection.inputStream.use { input ->
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                }
                require(count < buffer.size) { "UPDATE_MANIFEST" }
                parseManifest(String(buffer, 0, count, Charsets.UTF_8))
            } finally {
                connection.disconnect()
            }
        }

    suspend fun download(release: ReleaseUpdate): File =
        withContext(Dispatchers.IO) {
            val part = File(directory, "Shiftrail.part")
            val connection =
                connect("$REPOSITORY/releases/download/${release.tag}/${release.apkName}")
            try {
                require(connection.responseCode == 200) { "UPDATE_NETWORK" }
                require(connection.contentLengthLong <= MAX_APK_BYTES) { "UPDATE_INTEGRITY" }
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            require(count <= MAX_APK_BYTES) { "UPDATE_INTEGRITY" }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                require(hash == release.sha256) { "UPDATE_INTEGRITY" }
                validateApk(part, release)
                check(!apk.exists() || apk.delete()) { "UPDATE_STORAGE" }
                check(part.renameTo(apk)) { "UPDATE_STORAGE" }
                apk
            } finally {
                part.delete()
                connection.disconnect()
            }
        }

    @Suppress("DEPRECATION")
    fun validateApk(file: File, release: ReleaseUpdate) {
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val incoming =
            requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) {
                "UPDATE_INTEGRITY"
            }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(
            incoming.packageName == context.packageName &&
                versionCode(incoming) == release.versionCode.toLong() &&
                incoming.applicationInfo?.minSdkVersion == release.minSdk
        ) {
            "UPDATE_INTEGRITY"
        }
        fun signatures(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
                else info.signatures)
                .orEmpty()
                .map { it.toCharsString() }
                .toSet()
        require(
            signatures(incoming).isNotEmpty() && signatures(incoming) == signatures(installed)
        ) {
            "UPDATE_SIGNATURE"
        }
    }

    fun installIntent(): Intent {
        if (!context.packageManager.canRequestPackageInstalls())
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        check(apk.isFile) { "UPDATE_STORAGE" }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.updates", apk),
                "application/vnd.android.package-archive",
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun connect(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            require(
                url.protocol == "https" &&
                    url.host in DOWNLOAD_HOSTS &&
                    url.userInfo == null &&
                    url.port in listOf(-1, 443)
            ) {
                "UPDATE_INTEGRITY"
            }
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Shiftrail-Updater")
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                url = URL(url, requireNotNull(connection.getHeaderField("Location")))
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw FileNotFoundException("UPDATE_REDIRECT")
    }

    companion object {
        const val REPOSITORY = "https://github.com/GoryNickel9/app-turni"
        private const val MAX_APK_BYTES = 200 * 1024 * 1024L
        private val DOWNLOAD_HOSTS =
            setOf(
                "github.com",
                "release-assets.githubusercontent.com",
                "objects.githubusercontent.com",
            )

        fun parseManifest(text: String): ReleaseUpdate {
            val json = JSONObject(text)
            fun integer(name: String): Int {
                val value = json.get(name)
                require(value is Int || value is Long) { "UPDATE_MANIFEST" }
                val number = (value as Number).toLong()
                require(number in 0..Int.MAX_VALUE.toLong()) { "UPDATE_MANIFEST" }
                return number.toInt()
            }
            require(integer("schemaVersion") == 1) { "UPDATE_MANIFEST" }
            return ReleaseUpdate(
                integer("versionCode"),
                json.getString("versionName"),
                json.getString("sha256"),
                integer("minSdk"),
            )
        }

        @Suppress("DEPRECATION")
        private fun versionCode(info: PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}
