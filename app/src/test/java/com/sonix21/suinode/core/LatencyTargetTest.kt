package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class LatencyTargetTest {
    @Test fun disabledOrEmptyUsesCoreDefaultWithoutDiscardingDraft() {
        val draft = LatencyTarget(false, "not a URL")
        assertNull(draft.link())
        assertEquals("not a URL", draft.url)
        assertNull(LatencyTarget(true, "  ").link())
        assertNull(LatencyTarget().link())
    }

    @Test fun customHttpUrlsKeepQueryAndEncoding() {
        for (url in listOf("https://example.test:8443/a?x=a%2Bb&y=two", "http://[::1]:8080/", "HTTPS://example.test/")) {
            assertEquals(url, LatencyTarget(true, " $url ").link())
        }
    }

    @Test fun malformedAndNonHttpTargetsFailWithoutEchoingPrivateUrl() {
        for (url in listOf("example.test", "https://", "https:example.test", "ftp://example.test/", "https://a.test/a b", "https://a.test/\r\nToken: secret")) {
            val error = runCatching { LatencyTarget(true, url).link() }.exceptionOrNull()
            assertTrue(url, error is IllegalArgumentException)
            assertEquals("Enter a valid HTTP or HTTPS test URL.", error?.message)
        }
    }
}
