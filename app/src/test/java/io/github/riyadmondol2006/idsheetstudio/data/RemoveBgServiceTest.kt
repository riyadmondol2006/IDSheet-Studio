package io.github.riyadmondol2006.idsheetstudio.data

import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubApi
import io.github.riyadmondol2006.idsheetstudio.data.remote.Network
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgApi
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgException
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgService
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RemoveBgServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: RemoveBgService

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Network.retrofit(server.url("/").toString(), OkHttpClient())
        service = RemoveBgService(retrofit.create(RemoveBgApi::class.java))
    }

    @After fun tearDown() = server.close()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2, 3)

    @Test fun sendsMultipartWithKeyAndReturnsPng() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").body(Buffer().write(png)).build())
        val result = service.removeBackground("test-key", byteArrayOf(1, 2, 3), "preview")
        assertArrayEquals(png, result)
        val request = server.takeRequest()
        assertEquals("/v1.0/removebg", request.url.encodedPath)
        assertEquals("test-key", request.headers["X-Api-Key"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("name=\"image_file\""))
        assertTrue(body.contains("name=\"size\""))
        assertTrue(body.contains("preview"))
        assertTrue(body.contains("name=\"format\""))
    }

    @Test fun mapsErrorCodes() = runTest {
        val cases = listOf(
            403 to RemoveBgException.Kind.INVALID_KEY,
            402 to RemoveBgException.Kind.NO_CREDITS,
            429 to RemoveBgException.Kind.RATE_LIMITED,
            400 to RemoveBgException.Kind.BAD_IMAGE,
            500 to RemoveBgException.Kind.SERVER,
        )
        for ((code, kind) in cases) {
            server.enqueue(MockResponse.Builder().code(code).body("""{"errors":[{"title":"Could not identify foreground in image."}]}""").build())
            try {
                service.removeBackground("k", byteArrayOf(1), "auto")
                fail("expected error for $code")
            } catch (e: RemoveBgException) {
                assertEquals(kind, e.kind)
                if (code == 400) assertEquals("Could not identify foreground in image.", e.message)
            }
        }
    }

    @Test fun parsesAccount() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"data":{"attributes":{"credits":{"total":12.5,"subscription":0,"payg":12.5},"api":{"free_calls":47,"sizes":"all"}}}}""",
            ).build(),
        )
        val account = service.account("k")
        assertEquals(12.5, account.totalCredits, 0.0)
        assertEquals(47, account.freeCalls)
        assertEquals("/v1.0/account", server.takeRequest().url.encodedPath)
    }

    @Test fun networkFailureIsReported() = runTest {
        server.close()
        try {
            service.account("k")
            fail()
        } catch (e: RemoveBgException) {
            assertEquals(RemoveBgException.Kind.NETWORK, e.kind)
        }
    }

    @Test fun githubReleasesParse() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """[{"tag_name":"v1.1.0","name":"IDSheet Studio v1.1.0","body":"- Fix","draft":false,"prerelease":false,
                "html_url":"https://github.com/o/r/releases/tag/v1.1.0","published_at":"2026-10-01T00:00:00Z",
                "assets":[{"name":"IDSheet-Studio-v1.1.0.apk","browser_download_url":"https://x/a.apk","size":5,"content_type":"application/vnd.android.package-archive","extra":1}],
                "unknown_field":true}]""",
            ).build(),
        )
        val api = Network.retrofit(server.url("/").toString(), OkHttpClient()).create(GitHubApi::class.java)
        val releases = api.releases("o", "r")
        assertEquals("v1.1.0", releases.single().tagName)
        assertEquals("IDSheet-Studio-v1.1.0.apk", releases.single().assets.single().name)
        val request = server.takeRequest()
        assertEquals("/repos/o/r/releases", request.url.encodedPath)
        assertEquals("15", request.url.queryParameter("per_page"))
        assertEquals("application/vnd.github+json", request.headers["Accept"])
    }
}
