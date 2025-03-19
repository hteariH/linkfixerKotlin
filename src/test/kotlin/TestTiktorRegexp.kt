import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTiktorRegexp {

    @Test
    fun testTiktokUrlMatching() {
        val validUrls = listOf(
            "https://www.tiktok.com/@username/video/1234567890",
            "http://tiktok.com/@username/video/1234567890",
            "https://vm.tiktok.com/ZMB2dRrFd/",
            "https://www.tiktok.com/@maskedmaniacbeatz/video/7479110451599609134?_r=1&_t=ZM-8uf5Zdtze21",
        )
        validUrls.forEach { url ->
            assertTrue(isTiktokUrl(url), "Valid TikTok URL did not match: $url")
        }
    }

    @Test
    fun testInvalidTiktokUrls() {
        val invalidUrls = listOf(
            "https://www.youtube.com/watch?v=abc123",
            "https://www.instagram.com/p/12345",
            "https://tiktok.com/",
            "randomStringNotAUrl"
        )
        invalidUrls.forEach { url ->
            assertFalse(isTiktokUrl(url), "Invalid TikTok URL matched: $url")
        }
    }

    private fun isTiktokUrl(url: String): Boolean {
        val tiktokRegex = Regex(
            "https?://([a-z]+\\.)?tiktok\\.com/.+"
        )
        return tiktokRegex.matches(url)
    }
}