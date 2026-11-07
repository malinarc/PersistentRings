package dev.persistentrings

import android.content.pm.PackageManager
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class BridgeStatus(val running: Boolean = false, val authorized: Boolean = false)
class HyperOsHardware : RingHardware {
    fun status(): BridgeStatus = runCatching {
        val running = Shizuku.pingBinder()
        BridgeStatus(running, running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    }.getOrDefault(BridgeStatus())
    override suspend fun write(command: LightCommand) = withContext(Dispatchers.IO) {
        check(status().authorized) { "Shizuku is stopped or permission is missing. Start Shizuku and grant access." }
        val raw = SystemServiceHelper.getSystemService(SERVICE)
            ?: error("Firmware does not expose $SERVICE. No flashlight fallback is used.")
        val binder = ShizukuBinderWrapper(raw)
        check(binder.interfaceDescriptor == SERVICE) { "Unexpected lights interface; refusing transaction." }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE)
            data.writeInt(command.color and 0xFFFFFF)
            data.writeInt(0) // flashMode: finite pulse
            data.writeInt(command.onMs.coerceIn(100, 5_000))
            data.writeInt(0) // offMs: OFF is sent explicitly by the engine
            data.writeInt(0) // brightness mode is NOT a brightness level
            // Firmware expects this literal, per upstream research. Not our app identity.
            data.writeString("com.android.camera")
            data.writeInt(12) // camera scene
            data.writeInt(0) // owner user only
            check(binder.transact(4, data, reply, 0)) { "setCustomLight transaction unsupported" }
            reply.readException()
        } finally { data.recycle(); reply.recycle() }
        Unit
    }
    companion object { const val SERVICE = "miui.lights.ILightsManager" }
}
