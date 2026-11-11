package mx.kompara.overlay

import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.db.entity.OfferPresentationEntity
import org.junit.Assert.*
import org.junit.Test

class PresentationRecorderTest {
    private class Dao : OfferPresentationDao {
        val rows = mutableMapOf<String,OfferPresentationEntity>()
        override fun observeSuppressedCount(from: Long, until: Long) = kotlinx.coroutines.flow.flowOf(rows.values.count { it.outcome == "SUPPRESSED" })
        var writes = 0
        override suspend fun record(presentation: OfferPresentationEntity) { rows[presentation.sessionId]=presentation; writes++ }
        override suspend fun forSession(sessionId: String) = rows[sessionId]
        override suspend fun seenBetween(from: Long, until: Long) = rows.values.filter { it.recordedAt in from until until }
    }
    private val full=OverlayPlacement.Displayed(OverlayPosition(141,417),ChipSize(240,112),PlateFormat.FULL)
    @Test fun `measured no room persists once with session cause and original time`() = runTest {
        val dao=Dao(); val recorder=PresentationRecorder(dao)
        recorder.record("one",OverlayPlacement.Suppressed("NAVIGATION"),true,1000)
        recorder.record("one",OverlayPlacement.Suppressed("NAVIGATION"),true,2000)
        assertEquals(OfferPresentationEntity("one","SUPPRESSED",1000,"NAVIGATION"),dao.rows["one"])
        assertEquals(1,dao.writes)
    }
    @Test fun `unread grey and unvalidated geometry never count as no room`() = runTest {
        val dao=Dao(); val recorder=PresentationRecorder(dao)
        recorder.record("unread",OverlayPlacement.Suppressed("NAVIGATION"),false,1000)
        recorder.record("unknown",OverlayPlacement.Unsupported("UNVALIDATED_GEOMETRY"),true,1000)
        assertEquals(setOf("UNSUPPORTED"),dao.rows.values.map { it.outcome }.toSet())
        recorder.record("grey-shown",full,false,1001)
        assertEquals("FULL",dao.rows["grey-shown"]?.outcome)
    }
    @Test fun `compact and marker fallback are recorded independently of the offer judgement`() = runTest {
        val dao=Dao(); val recorder=PresentationRecorder(dao)
        recorder.record("one",full,true,1000)
        recorder.record("one",full.copy(format=PlateFormat.COMPACT,overMarker=true),true,1100)
        assertEquals(OfferPresentationEntity("one","COMPACT",1100,"MARKER_FALLBACK"),dao.rows["one"])
    }
    @Test fun `shown evidence survives later stale or blocked layout and process recreation`() = runTest {
        val dao=Dao(); PresentationRecorder(dao).record("one",full,true,1000)
        val reopened=PresentationRecorder(dao)
        reopened.record("one",OverlayPlacement.Suppressed("NAVIGATION"),true,1100)
        reopened.record("one",OverlayPlacement.Unsupported("STALE_GEOMETRY"),true,1200)
        assertEquals("FULL",dao.rows["one"]?.outcome)
        assertEquals(1000L,dao.rows["one"]?.recordedAt)
    }
    @Test fun `same offer can return after no room without rewriting another session`() = runTest {
        val dao=Dao(); val recorder=PresentationRecorder(dao)
        recorder.record("one",OverlayPlacement.Suppressed("NAVIGATION"),true,1000)
        recorder.record("two",OverlayPlacement.Suppressed("NO_SAFE_SPACE"),true,1100)
        recorder.record("one",full,true,1200)
        assertEquals("FULL",dao.rows["one"]?.outcome)
        assertEquals("SUPPRESSED",dao.rows["two"]?.outcome)
    }
}
