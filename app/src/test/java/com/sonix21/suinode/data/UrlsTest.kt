package com.sonix21.suinode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlsTest {
    @Test fun normalizesPanelRootAndApiSuffixes() {
        assertEquals("https://vpn.example.com/app/", Urls.normalize("https://vpn.example.com/app/"))
        assertEquals("https://vpn.example.com/app/", Urls.normalize("https://vpn.example.com/app/apiv2/"))
        assertEquals("http://10.0.0.2:2095/app/", Urls.normalize("10.0.0.2:2095/app/api"))
    }

    @Test fun buildsCorrectAuthenticationBases() {
        assertEquals("https://vpn.example.com/app/apiv2/", Urls.apiBase("https://vpn.example.com/app"))
    }

    @Test fun rejectsUnsafeOrMalformedPanelUrls() {
        assertNull(Urls.normalize("ftp://vpn.example.com"))
        assertNull(Urls.normalize("https://user:pass@vpn.example.com/app"))
        assertNull(Urls.normalize("https://vpn.example.com/app?token=secret"))
        assertNull(Urls.normalize("not a host"))
    }
}
