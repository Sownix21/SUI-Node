package com.sonix21.suinode.core

import com.sonix21.suinode.data.Panel
import com.sonix21.suinode.data.SuiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import java.time.Instant

class SaveSafetyAndBillingTest {
    @Test fun panelMetadataRoundTripsAndOldProfilesDefaultToOptOut(){
        val p=Panel("p","Panel","https://example.com/",token="test-token",group="Provider",favorite=true,memo="Quarterly invoice",readOnly=true,offlineOverview=true)
        assertEquals(p,Panel.fromJson(p.toJson()))
        val old=Panel.fromJson(jo("id" to "old","name" to "Old","url" to "https://example.com/","token" to "test"))
        assertFalse(old.readOnly);assertFalse(old.offlineOverview);assertFalse(old.favorite);assertEquals("",old.memo)
        assertFalse(p.toString().contains("Provider"))
    }
    @Test fun readOnlyStopsWritesAndProbesBeforeAnyNetworkCall()=runBlocking{
        val api=SuiClient(Panel("p","Test","http://127.0.0.1:1/",token="test",readOnly=true))
        assertTrue(runCatching{api.postForm("save",emptyMap())}.exceptionOrNull()?.message.orEmpty().contains("Read-only"))
        assertTrue(runCatching{api.get("checkOutbound")}.exceptionOrNull()?.message.orEmpty().contains("Read-only"))
        assertTrue(runCatching{api.uploadDb(java.io.File("nonexistent-test.db"))}.exceptionOrNull()?.message.orEmpty().contains("Read-only"))
    }
    @Test fun saveReceiptConfirmsDesiredStateButNotPartialOrDifferentChanges(){
        val desired=jo("id" to 2,"name" to "client","enable" to true,"volume" to 900,"up" to 100,"config" to jo("password" to "secret"))
        val receipt=SaveEvidence.build("clients","edit",desired,jarr(listOf(desired)))
        assertTrue(SaveEvidence.matches(receipt,jarr(listOf(desired.deepCopy().put("up",999)))))
        assertFalse(SaveEvidence.matches(receipt,jarr(listOf(desired.deepCopy().put("volume",800)))))
        assertFalse(SaveEvidence.matches(receipt,JSONArray()))
        assertFalse(receipt.toString().contains("secret"))
    }
    @Test fun newSaveUsesStableNameAndRejectsExistingDuplicates(){
        val desired=jo("id" to 0,"name" to "new","enable" to true)
        val receipt=SaveEvidence.build("clients","new",desired,JSONArray())
        assertTrue(SaveEvidence.matches(receipt,jarr(listOf(desired.deepCopy().put("id",12)))))
        assertTrue(runCatching{SaveEvidence.build("clients","new",desired,jarr(listOf(desired)))}.isFailure)
    }
    @Test fun deletionReceiptsRespectNumericTagsAsStrings(){
        val receipt=SaveEvidence.build("outbounds","del","123",jarr(listOf(jo("tag" to "123"))))
        assertFalse(SaveEvidence.matches(receipt,jarr(listOf(jo("tag" to "123")))))
        assertTrue(SaveEvidence.matches(receipt,JSONArray()))
    }
    @Test fun configurationChecksRemainStrictAndReviewMasksSecrets(){
        val config=jo("route" to jo("final" to "direct"),"password" to "do-not-show","customAddress" to "https://secret@example.com")
        val receipt=SaveEvidence.build("config","set",config,jo())
        assertTrue(SaveEvidence.matches(receipt,config))
        assertFalse(SaveEvidence.matches(receipt,jo()))
        val text=SaveEvidence.diff(null,config).joinToString()
        assertFalse(text.contains("do-not-show"));assertFalse(text.contains("secret@example.com"));assertTrue(text.contains("hidden"))
    }
    @Test fun monthlyRenewalKeepsEndOfMonthAnchor(){
        val jan=VpsRenewal(enabled=true,due="2026-01-31")
        assertEquals("2026-02-28",jan.next().due)
        assertEquals("2026-03-31",jan.next().next().due)
        assertEquals("2026-04-30",jan.copy(schedule="quarterly").next().due)
    }
    @Test fun renewalReminderIsOptionalUsesWarningWindowAndKeepsPriceLocal(){
        val cfg=VpsRenewal(enabled=true,due="2026-09-20",price="12.50",currency="USD",warningDays=7)
        assertNull(cfg.alert("p","Panel",Instant.parse("2026-09-12T00:00:00Z").epochSecond))
        assertEquals(1,cfg.alert("p","Panel",Instant.parse("2026-09-13T00:00:00Z").epochSecond)?.severity)
        assertEquals(2,cfg.alert("p","Panel",Instant.parse("2026-09-21T00:00:00Z").epochSecond)?.severity)
        assertNull(cfg.copy(enabled=false).alert("p","Panel",Instant.parse("2026-09-21T00:00:00Z").epochSecond))
        assertEquals(cfg,VpsRenewal.fromJson(cfg.toJson()))
        assertEquals("2026-10-04",cfg.copy(schedule="custom",customDays=14).next().due)
    }
}
