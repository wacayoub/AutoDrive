package com.autodrive.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal interface UpdateSource {
    val versionCode: Long
    val versionName: String
    fun latest(): UpdateRelease
    fun download(release: UpdateRelease, progress: (Int) -> Unit): File
    fun verify(file: File, release: UpdateRelease): File
}

internal class GitHubUpdates(private val context: Context) : UpdateSource {
    private val packages = context.packageManager
    val installed get() = packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    override val versionCode get() = installed.longVersionCode
    override val versionName get() = installed.versionName.orEmpty()

    override fun latest(): UpdateRelease {
        File(context.filesDir, "updates").listFiles()?.forEach { file ->
            val code = Regex("AutoDrive-(\\d+)\\.apk").matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            if (code != null && code <= versionCode) file.delete()
        }
        val release = JSONObject(read(UpdatePolicy.LATEST_API, 1024 * 1024))
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Aucune version stable disponible" }
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == "autodrive-update.json" }
            ?: throw IOException("Cette version ne propose pas encore de mise à jour intégrée")
        val manifestUrl = asset.getString("browser_download_url")
        require(UpdatePolicy.releaseAsset(manifestUrl, "autodrive-update.json")) { "Source de mise à jour incorrecte" }
        val manifest = JSONObject(read(manifestUrl, 64 * 1024))
        require(manifest.getInt("schema") == 1) { "Format de mise à jour incompatible" }
        return UpdateRelease(manifest.getString("packageName"), manifest.getLong("versionCode"),
            manifest.getString("versionName"), manifest.getInt("minSdk"), manifest.getString("apkUrl"),
            manifest.getString("sha256"), manifest.getLong("size")).also { it.validate() }
    }

    override fun download(release: UpdateRelease, progress: (Int) -> Unit): File {
        release.validate()
        require(release.versionCode > versionCode) { "Cette version est déjà installée" }
        require(release.minSdk <= Build.VERSION.SDK_INT) { "Cette mise à jour nécessite un Android plus récent" }
        val folder = File(context.filesDir, "updates").apply { check(isDirectory || mkdirs()) }
        require(folder.usableSpace > release.size + 16 * 1024 * 1024) { "Espace de stockage insuffisant" }
        val temporary = File(folder, "AutoDrive-${release.versionCode}.part")
        val target = File(folder, "AutoDrive-${release.versionCode}.apk")
        folder.listFiles()?.filter { it != target && it != temporary && Regex("AutoDrive-\\d+\\.(apk|part)").matches(it.name) }
            ?.forEach { it.delete() }
        try {
            val connection = connect(release.apkUrl)
            try {
                if (connection.contentLengthLong > 0) require(connection.contentLengthLong == release.size) { "Taille téléchargée incorrecte" }
                connection.inputStream.use { source ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var count = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            count += read
                            require(count <= release.size) { "Fichier de mise à jour trop volumineux" }
                            output.write(buffer, 0, read)
                            val value = (count * 100 / release.size).toInt()
                            if (value != lastProgress) { progress(value); lastProgress = value }
                        }
                        require(count == release.size) { "Téléchargement incomplet" }
                    }
                }
            } finally { connection.disconnect() }
            verify(temporary, release)
            if (target.exists()) check(target.delete())
            check(temporary.renameTo(target)) { "Impossible de préparer la mise à jour" }
            return target
        } finally { temporary.delete() }
    }

    override fun verify(file: File, release: UpdateRelease): File {
        release.validate()
        require(release.minSdk <= Build.VERSION.SDK_INT) { "Mise à jour incompatible avec Android" }
        require(file.isFile && file.length() == release.size) { "Fichier de mise à jour incomplet" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        require(hex(digest.digest()).equals(release.sha256, true)) { "Empreinte du téléchargement incorrecte" }
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = packages.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Fichier Android invalide")
        require(archive.packageName == context.packageName && archive.packageName == release.packageName) { "Le fichier appartient à une autre application" }
        require(archive.longVersionCode == release.versionCode && archive.longVersionCode > versionCode) { "Version Android inattendue ou plus ancienne" }
        require(archive.versionName == release.versionName && archive.applicationInfo?.minSdkVersion == release.minSdk) { "Mise à jour incompatible" }
        val current = packages.getPackageInfo(context.packageName, flags)
        val installedSigners = current.signingInfo?.apkContentsSigners?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet()
        val downloadedSigners = archive.signingInfo?.apkContentsSigners?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet()
        require(!installedSigners.isNullOrEmpty() && installedSigners == downloadedSigners) { "La signature de la mise à jour ne correspond pas à AutoDrive" }
        return file
    }

    private fun connect(start: String): HttpURLConnection {
        var address = start
        repeat(6) {
            require(UpdatePolicy.downloadHost(address)) { "Hôte de téléchargement non autorisé" }
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "AutoDrive/$versionName")
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            try {
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val next = connection.getHeaderField("Location") ?: throw IOException("Redirection invalide")
                    address = URL(URL(address), next).toString()
                    connection.disconnect()
                } else {
                    if (code != 200) throw IOException(when (code) {
                        404 -> "Aucune version publiée disponible"
                        403, 429 -> "GitHub limite les requêtes. Réessaie plus tard."
                        else -> "Serveur de mises à jour indisponible ($code)"
                    })
                    return connection
                }
            } catch (error: Exception) { connection.disconnect(); throw error }
        }
        throw IOException("Trop de redirections de téléchargement")
    }

    private fun read(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            require(connection.contentLengthLong <= limit) { "Réponse de mise à jour trop volumineuse" }
            val bytes = connection.inputStream.use { it.readNBytes(limit + 1) }
            require(bytes.size <= limit) { "Réponse de mise à jour trop volumineuse" }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
