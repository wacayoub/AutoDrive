package com.autodrive.launcher

import java.net.URI
import java.util.Locale

internal data class UpdateRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long
) {
    fun validate() {
        require(packageName == UpdatePolicy.LAUNCHER_PACKAGE || packageName == UpdatePolicy.RADIO_PACKAGE) {
            "Application de mise à jour incorrecte"
        }
        require(versionCode > 0 && versionName.isNotBlank() && versionName.length <= 64) { "Version de mise à jour incorrecte" }
        require(minSdk >= 1 && size in 1..UpdatePolicy.MAX_APK_BYTES) { "Taille ou compatibilité de mise à jour incorrecte" }
        require(Regex("[a-fA-F0-9]{64}").matches(sha256)) { "Empreinte de mise à jour absente ou incorrecte" }
        val validAddress = if (packageName == UpdatePolicy.RADIO_PACKAGE) {
            UpdatePolicy.radioTag("radio-maroc-v$versionName") &&
                apkUrl == "${UpdatePolicy.REPO_URL}/releases/download/radio-maroc-v$versionName/Radio-Maroc-Coran-$versionName.apk"
        } else UpdatePolicy.releaseAsset(apkUrl, "AutoDrive.apk")
        require(validAddress) { "Adresse de mise à jour non autorisée" }
    }
}

internal object UpdatePolicy {
    const val REPOSITORY = "wacayoub/AutoDrive"
    const val REPO_URL = "https://github.com/$REPOSITORY"
    const val LATEST_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases"
    const val LAUNCHER_PACKAGE = "com.autodrive.launcher"
    const val RADIO_PACKAGE = "com.autodrive.radiomaroc"
    const val RADIO_MANIFEST = "radio-maroc-update.json"
    const val MAX_APK_BYTES = 128L * 1024 * 1024

    fun radioTag(tag: String) = Regex("radio-maroc-v[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}").matches(tag)

    fun releaseAsset(url: String, name: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!https(uri) || !uri.host.equals("github.com", true) || uri.query != null || uri.fragment != null) return false
        val pattern = Regex("/${Regex.escape(REPOSITORY)}/releases/download/[A-Za-z0-9][A-Za-z0-9._-]{0,63}/${Regex.escape(name)}")
        return pattern.matches(uri.rawPath.orEmpty())
    }

    fun downloadHost(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!https(uri)) return false
        return uri.host.lowercase(Locale.ROOT) in setOf("api.github.com", "github.com",
            "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    }

    private fun https(uri: URI) = uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.port in setOf(-1, 443)
}
