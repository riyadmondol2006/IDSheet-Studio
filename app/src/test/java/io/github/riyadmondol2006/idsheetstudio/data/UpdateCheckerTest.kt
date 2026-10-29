package io.github.riyadmondol2006.idsheetstudio.data

import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubAsset
import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubRelease
import io.github.riyadmondol2006.idsheetstudio.data.update.SemanticVersion
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun v(s: String) = SemanticVersion.parse(s)!!

    @Test fun semanticVersionOrdering() {
        assertTrue(v("1.0.1") > v("1.0.0"))
        assertTrue(v("1.10.0") > v("1.9.9"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("1.0.0") > v("1.0.0-rc.1"))
        assertTrue(v("1.0.0-rc.2") > v("1.0.0-rc.1"))
        assertTrue(v("1.0.0-beta") < v("1.0.0-rc"))
        assertEquals(v("v1.2"), v("1.2.0"))
        assertNull(SemanticVersion.parse("latest"))
        assertEquals(10203, v("1.2.3").versionCode)
    }

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, body: String = "", apk: Boolean = true) = GitHubRelease(
        tagName = tag,
        name = "IDSheet Studio $tag",
        body = body,
        draft = draft,
        prerelease = pre,
        htmlUrl = "https://github.com/x/y/releases/tag/$tag",
        assets = if (apk) {
            listOf(
                GitHubAsset("IDSheet-Studio-$tag.apk", "https://example.com/$tag.apk", 1000),
                GitHubAsset("IDSheet-Studio-$tag.apk.sha256", "https://example.com/$tag.apk.sha256", 90),
            )
        } else emptyList(),
    )

    @Test fun picksNewestCompatibleRelease() {
        val releases = listOf(
            release("v1.3.0", draft = true),
            release("v1.2.5", pre = true),
            release("v1.2.0", body = "## Changes\n- Faster export\n<!-- min-sdk: 26 -->"),
            release("v1.1.0"),
            release("v0.9.0"),
        )
        val update = UpdateChecker.selectUpdate(releases, v("1.0.0"), deviceSdk = 30)
        assertNotNull(update)
        assertEquals("1.2.0", update!!.version.toString())
        assertEquals("## Changes\n- Faster export", update.notes)
        assertEquals("IDSheet-Studio-v1.2.0.apk.sha256", update.checksum?.name)
    }

    @Test fun skipsIncompatibleOrAssetlessReleases() {
        val releases = listOf(
            release("v2.0.0", body = "<!-- min-sdk: 35 -->"),
            release("v1.5.0", apk = false),
            release("v1.4.0"),
        )
        assertEquals("1.4.0", UpdateChecker.selectUpdate(releases, v("1.0.0"), deviceSdk = 30)!!.version.toString())
        assertEquals("2.0.0", UpdateChecker.selectUpdate(releases, v("1.0.0"), deviceSdk = 36)!!.version.toString())
    }

    @Test fun noUpdateWhenCurrentIsLatest() {
        assertNull(UpdateChecker.selectUpdate(listOf(release("v1.0.0"), release("v0.5.0")), v("1.0.0"), 34))
    }

    @Test fun popupNotesHideTheVerificationSectionAndUnwrapLines() {
        val body = """### Added
- Cutting lines: dashed lines along every photo edge for straight cuts with a ruler, scissors
  or a trimmer (the new default for new sheets).
- Second item

---
<!-- idsheet-build -->
<!-- min-sdk: 26 -->
### Download & verify

| | |
|---|---|
| APK | `IDSheet-Studio-v1.0.1.apk` |"""
        assertEquals(
            "### Added\n- Cutting lines: dashed lines along every photo edge for straight cuts with a ruler, scissors or a trimmer (the new default for new sheets).\n- Second item",
            UpdateChecker.cleanNotes(body),
        )
        // The min-sdk marker is still read from the raw body before cleaning.
        val update = UpdateChecker.selectUpdate(listOf(release("v1.0.1", body = body)), v("1.0.0"), deviceSdk = 25)
        assertEquals(null, update)
    }
}
