package dev.persistentrings

import org.junit.Assert.*
import org.junit.Test

class NotificationIndexTest {
    @Test fun tracksArbitraryPackagesBeforeTheyAreSelected() {
        val index = NotificationIndex()
        index.replace(listOf(
            Notice("calendar", "dev.example.calendar", false, false),
            Notice("mail", "dev.example.mail", false, false)
        ))
        assertEquals(0, index.count(emptySet()))
        assertEquals(1, index.count(setOf("dev.example.mail")))
        assertEquals(2, index.count(setOf("dev.example.calendar", "dev.example.mail")))
        index.remove("mail")
        assertEquals(0, index.count(setOf("dev.example.mail")))
    }
    private val apps = setOf("com.whatsapp", "org.telegram.messenger")
    private fun notice(key: String, pkg: String = "com.whatsapp", summary: Boolean = false, ongoing: Boolean = false) = Notice(key,pkg,summary,ongoing)
    @Test fun updatesAndDuplicatesUseNotificationKey() {
        val index = NotificationIndex()
        repeat(10) { index.post(notice("one")) }
        index.post(notice("two", "org.telegram.messenger"))
        assertEquals(2, index.count(apps))
        index.remove("one")
        assertEquals(1, index.count(apps))
        index.remove("one")
        assertEquals(1, index.count(apps))
        index.remove("two")
        assertEquals(0, index.count(apps))
    }
    @Test fun groupSummaryAndOngoingDoNotKeepReminderAlive() {
        val index = NotificationIndex()
        index.replace(listOf(notice("summary",summary=true),notice("child"),notice("call",ongoing=true),notice("other","other.app")))
        assertEquals(1,index.count(apps))
        index.remove("child")
        assertEquals(0,index.count(apps))
    }
    @Test fun reconnectReplacesStaleStateAndFiltersDisabledApps() {
        val index = NotificationIndex()
        index.post(notice("stale"))
        index.replace(listOf(notice("restored","org.telegram.messenger")))
        assertEquals(0,index.count(setOf("com.whatsapp")))
        assertEquals(1,index.count(apps))
        index.replace(emptyList())
        assertEquals(0,index.count(apps))
    }
}
