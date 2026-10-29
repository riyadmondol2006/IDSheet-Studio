package io.github.riyadmondol2006.idsheetstudio.data.update

import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubApi
import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubAsset
import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubRelease

data class UpdateInfo(
    val version: SemanticVersion,
    val tag: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val apk: GitHubAsset,
    val checksum: GitHubAsset?,
)

/**
 * Finds the newest published, non-prerelease GitHub release that is newer than the running app,
 * ships an APK and is compatible with this device's Android version.
 */
class UpdateChecker(
    private val api: GitHubApi,
    private val owner: String,
    private val repo: String,
) {
    suspend fun findUpdate(currentVersion: String, deviceSdk: Int): UpdateInfo? {
        val current = SemanticVersion.parse(currentVersion) ?: return null
        return selectUpdate(api.releases(owner, repo), current, deviceSdk)
    }

    companion object {
        private val MIN_SDK_MARKER = Regex("""<!--\s*min-sdk:\s*(\d+)\s*-->""", RegexOption.IGNORE_CASE)

        fun selectUpdate(releases: List<GitHubRelease>, current: SemanticVersion, deviceSdk: Int): UpdateInfo? =
            releases.asSequence()
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    val version = SemanticVersion.parse(release.tagName) ?: return@mapNotNull null
                    if (version.preRelease != null || version <= current) return@mapNotNull null
                    val minSdk = release.body?.let { MIN_SDK_MARKER.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    if (minSdk != null && minSdk > deviceSdk) return@mapNotNull null
                    val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && !it.name.contains("debug", true) }
                        ?: return@mapNotNull null
                    val checksum = release.assets.firstOrNull { it.name.equals(apk.name + ".sha256", ignoreCase = true) }
                    UpdateInfo(
                        version = version,
                        tag = release.tagName,
                        title = release.name?.takeIf { it.isNotBlank() } ?: "IDSheet Studio ${release.tagName}",
                        notes = cleanNotes(release.body.orEmpty()),
                        htmlUrl = release.htmlUrl,
                        publishedAt = release.publishedAt,
                        apk = apk,
                        checksum = checksum,
                    )
                }
                .maxByOrNull { it.version }

        /**
         * Release notes for the in-app popup: drops the download/verify section the release
         * workflow appends for website readers (everything from its marker on), HTML comments and
         * trailing rules, and joins changelog lines that were hard-wrapped with indentation.
         */
        fun cleanNotes(body: String): String {
            val beforeBuildSection = body.substringBefore("<!-- idsheet-build -->")
            val lines = beforeBuildSection
                .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .replace("\r\n", "\n")
                .lines()
            val joined = mutableListOf<String>()
            for (line in lines) {
                val isContinuation = line.isNotBlank() && line.first().isWhitespace() &&
                    !line.trimStart().startsWith("- ") && !line.trimStart().startsWith("* ") &&
                    joined.isNotEmpty() && joined.last().isNotBlank()
                if (isContinuation) joined[joined.lastIndex] = joined.last().trimEnd() + " " + line.trim() else joined += line.trimEnd()
            }
            return joined.joinToString("\n").trim().removeSuffix("---").trim()
        }
    }
}
