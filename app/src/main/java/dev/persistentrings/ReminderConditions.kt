package dev.persistentrings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal data class ActiveReminderApp(val packageName: String, val latestPostTime: Long, val resolvedColor: Int)

internal data class SelectedNotifications(val settings: RingSettings, val eligible: List<Notice> = emptyList()) {
    val count get() = eligible.size
    val apps: List<ActiveReminderApp> = eligible.groupingBy { it.pkg }
        .fold(Long.MIN_VALUE) { latest, notice -> maxOf(latest, notice.postTime) }
        .map { (pkg, latest) -> ActiveReminderApp(pkg, latest, settings.colorFor(pkg)) }
        .sortedWith(compareByDescending<ActiveReminderApp> { it.latestPostTime }.thenBy { it.packageName })

    fun needsOrientation(foreground: Boolean) = settings.faceDownOnly && count > 0 && foreground
}

/** Calculate count and configuration together to avoid acting on an earlier package selection. */
internal fun selectedNotifications(settings: Flow<RingSettings>, notices: Flow<List<Notice>>) =
    combine(settings, notices) { current, snapshot ->
        val eligible = NotificationIndex().apply { replace(snapshot) }.eligible(current.packages)
        SelectedNotifications(current, eligible)
    }

internal fun RingSettings.allowsOrientation(orientation: OrientationState): Boolean =
    !faceDownOnly || orientation == OrientationState.FACE_DOWN
