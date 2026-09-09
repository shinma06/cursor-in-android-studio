package com.cursoragent.ui.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BrowserUrlTest {
    @Test
    fun `normalizes HTTP addresses without networking or changing escaped components`() {
        mapOf(
            " https://example.com/a%2Fb?q=a%26b#here " to "https://example.com/a%2Fb?q=a%26b#here",
            "HTTP://localhost:8080/" to "http://localhost:8080/",
            "http://127.0.0.1:1" to "http://127.0.0.1:1",
            "https://[::1]:65535/a" to "https://[::1]:65535/a",
            "https://[2001:db8::1]/" to "https://[2001:db8::1]/",
            "https://例え.テスト/検索?q=日本語" to "https://xn--r8jz45g.xn--zckzah/%E6%A4%9C%E7%B4%A2?q=%E6%97%A5%E6%9C%AC%E8%AA%9E",
            "https://example.com./" to "https://example.com./",
        ).forEach { (input, expected) -> assertEquals(expected, BrowserUrl.normalize(input), input) }
    }

    @Test
    fun `rejects credentials unsafe schemes malformed hosts ports and ambiguous encodings`() {
        listOf(
            "", "example.com", "//example.com", "https:///path", "https://",
            "javascript:alert(1)", "file:///tmp/test.html", "data:text/html,hello", "custom://host",
            "https://user:secret@example.com", "https://user@example.com", "https://@example.com",
            "https://user%40example.com", "https://%65xample.com", "https://example.com\\@evil.test",
            "https://exam\nple.com", "https://exam ple.com", "https://example.com/\u0000",
            "https://example.com/%zz", "https://-host.test", "https://host_.test", "https://a..test",
            "https://[::invalid]", "https://[::1]evil", "https://[::1%25en0]", "https://::1/",
            "https://example.com:", "https://example.com:0", "https://example.com:65536",
            "https://example.com:-1", "https://example.com:+80", "https://example.com:80:90",
            "https://example.com:999999999999", "https://${"a".repeat(64)}.test/",
        ).forEach { assertNull(BrowserUrl.normalize(it), it) }
    }
}
