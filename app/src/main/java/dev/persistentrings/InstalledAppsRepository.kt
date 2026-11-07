package dev.persistentrings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InstalledAppsRepository(context: Context) {
    private val manager = context.packageManager
    private val icons = LruCache<String, Bitmap>(96)

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = manager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            .mapTo(hashSetOf()) { it.activityInfo.packageName }
        manager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(manager).toString() }.getOrDefault(info.packageName),
                    enabled = info.enabled,
                    system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    launchable = info.packageName in launchable
                )
            }
    }

    suspend fun icon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        icons.get(packageName) ?: runCatching {
            manager.getApplicationIcon(packageName).toBitmap(96, 96)
        }.getOrNull()?.also { icons.put(packageName, it) }
    }
}
