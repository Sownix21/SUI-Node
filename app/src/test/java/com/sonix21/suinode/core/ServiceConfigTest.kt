package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class ServiceConfigTest {
    @Test fun defaultsCoverLatestServices() {
        assertEquals(7, ServiceConfig.types.size)
        ServiceConfig.types.keys.forEach { assertEquals(it, ServiceConfig.create(it).getString("type")) }
        assertEquals(9090, ServiceConfig.create("api").getInt("listen_port"))
        assertEquals("127.0.0.1", ServiceConfig.create("api").getString("listen"))
        assertTrue(ServiceConfig.create("ssm-api").has("listen_port"))
        assertFalse(ServiceConfig.create("oom-killer").has("tls_id"))
    }
    @Test fun repeatedSsmPathsRemainArraysDuringEdits() {
        val rows = listOf("/ss" to "one", "/ss" to "two", "/other" to "three")
        val json = ServiceConfig.paths(rows)
        assertEquals(listOf("one", "two"), json.getJSONArray("/ss").strList())
        assertEquals(rows.toSet(), ServiceConfig.pathRows(json).toSet())
        assertEquals("two", ServiceConfig.paths(rows.drop(1)).getString("/ss"))
    }
    @Test fun dashboardShortFormsWidenOnlyWhenNeeded() {
        val o = jo("dashboard" to true)
        assertTrue(ServiceConfig.dashboardEnabled(o))
        ServiceConfig.setDashboardPath(o, "/dashboard")
        assertEquals("/dashboard", o.getString("dashboard"))
        ServiceConfig.setDashboardDownload(o, true)
        assertEquals("/dashboard", o.getJSONObject("dashboard").getString("path"))
        assertTrue(o.getJSONObject("dashboard").has("download_url"))
        ServiceConfig.setDashboardDownload(o, false)
        assertEquals("/dashboard", o.getString("dashboard"))
    }
    @Test fun dashboardUnknownFieldsAreNotLostWhenDisablingDownload() {
        val o = jo("dashboard" to jo("enabled" to true, "download_url" to "url", "future" to "keep"))
        ServiceConfig.setDashboardDownload(o, false)
        assertEquals("keep", o.getJSONObject("dashboard").getString("future"))
        assertFalse(o.getJSONObject("dashboard").has("download_url"))
    }
    @Test fun oomPayloadRemovesIncompatibleFieldsWithoutMutatingDraft() {
        val o = jo("type" to "oom-killer", "listen" to "::", "listen_port" to 53, "tls_id" to 1,
            "tcp_fast_open" to true, "memory_limit" to "1gb", "future" to "keep")
        val p = ServiceConfig.payload(o)
        assertFalse(p.has("listen")); assertFalse(p.has("tls_id")); assertFalse(p.has("tcp_fast_open"))
        assertEquals("1gb", p.getString("memory_limit")); assertEquals("keep", p.getString("future"))
        assertTrue(o.has("listen"))
    }
}
