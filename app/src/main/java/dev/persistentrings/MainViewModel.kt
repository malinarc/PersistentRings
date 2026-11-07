package dev.persistentrings

import android.app.Application
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val runtime = (app as RingsApp).runtime
    val access = runtime.notificationAccess
    val batteryExempt = MutableStateFlow(false)
    private val repository = InstalledAppsRepository(app)
    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val appsLoading = MutableStateFlow(true)
    val appsError = MutableStateFlow(false)
    val observedPackages = runtime.notices.map { snapshot -> snapshot.mapTo(hashSetOf()) { it.pkg } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    internal val apps = combine(installed, observedPackages) { known, observed ->
        appCatalog(known, observed, app.packageName)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var appLoad: Job? = null

    suspend fun appIcon(packageName: String) = repository.icon(packageName)
    internal fun selectApps(apps: List<InstalledApp>) { runtime.change { it.selectApps(apps) } }
    fun clearApps() { runtime.change { it.clearApps() } }
    fun refresh() {
        val app = getApplication<Application>()
        runtime.refreshNotificationAccess()
        batteryExempt.value = app.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(app.packageName) == true
        runtime.refreshBridge()
    }
    fun refreshApps() {
        if (appLoad?.isActive == true) return
        appLoad = viewModelScope.launch {
            appsLoading.value = true
            appsError.value = false
            try {
                installed.value = repository.load()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                appsError.value = true
            } finally {
                appsLoading.value = false
            }
        }
    }
    fun confirmTest() { runtime.change { it.copy(verifiedFirmware = Build.FINGERPRINT) } }
    fun rejectTest() {
        runtime.change { it.copy(verifiedFirmware = "", enabled = false) }
        runtime.testSent.value = false
        runtime.message.value = "Hardware not confirmed. Check HyperOS back-light settings and firmware compatibility; reminders remain disabled."
    }
}
