package dev.persistentrings

/** No notification text is stored. The listener supplies a complete authoritative snapshot. */
data class Notice(val key: String, val pkg: String, val summary: Boolean, val ongoing: Boolean, val postTime: Long = 0)
class NotificationIndex {
    private val entries = linkedMapOf<String, Notice>()
    fun replace(snapshot: List<Notice>) { entries.clear(); snapshot.forEach { entries[it.key] = it } }
    fun post(n: Notice) { entries[n.key] = n }
    fun remove(key: String) { entries.remove(key) }
    fun eligible(packages: Set<String>): List<Notice> = entries.values.filter {
        it.pkg in packages && !it.summary && !it.ongoing
    }.sortedBy { it.key }
    fun count(packages: Set<String>): Int = eligible(packages).size
}
