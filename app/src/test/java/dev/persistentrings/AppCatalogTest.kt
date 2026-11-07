package dev.persistentrings

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppCatalogTest {
    @Test fun mapsFallbackLabelsSortsLocallyAndExcludesDisabledAndSelf() {
        val apps = listOf(
            InstalledApp("a.z", "Zebra"), InstalledApp("b.e", "Éclair"),
            InstalledApp("c.a", "Alpha"), InstalledApp("d.blank", ""),
            InstalledApp("self"), InstalledApp("disabled", enabled = false)
        )
        val catalog = appCatalog(apps, setOf("observed", "disabled", "self"), "self", Locale.ENGLISH)
        assertEquals(listOf("c.a", "d.blank", "b.e", "observed", "a.z"), catalog.map { it.packageName })
        assertEquals("d.blank", catalog[1].label)
        assertEquals("observed", catalog[3].label)
    }

    @Test fun searchAndSystemFilterKeepObservedHeadlessApps() {
        val apps = listOf(
            InstalledApp("pkg.client", "Client"),
            InstalledApp("system.headless", "Hidden", system = true),
            InstalledApp("system.launcher", "Launcher", system = true, launchable = true),
            InstalledApp("system.observed", "Observed", system = true)
        )
        val observed = setOf("system.observed")
        assertEquals(listOf("pkg.client", "system.launcher", "system.observed"),
            apps.visibleApps("", false, observed, emptySet()).map { it.packageName })
        assertEquals(listOf("system.headless"), apps.visibleApps("  HEADLESS ", true, observed, emptySet())
            .map { it.packageName })
        assertEquals(listOf("pkg.client"), apps.visibleApps("client", false, observed, emptySet())
            .map { it.packageName })
    }

    @Test fun selectAllAddsShownAppsAndClearAllClearsHiddenSelections() {
        val selected = RingSettings(packages = setOf("other.saved"))
            .selectApps(listOf(InstalledApp("arbitrary.app"), InstalledApp("another.app")))
        assertEquals(setOf("other.saved", "arbitrary.app", "another.app"), selected.packages)
        assertTrue(selected.clearApps().packages.isEmpty())
    }
}
