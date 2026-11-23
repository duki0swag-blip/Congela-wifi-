package com.example.killswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class BlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, createNotification())
        establishVpn()
        return START_STICKY
    }

    private fun establishVpn() {
        // Cerrar cualquier interfaz previa
        vpnInterface?.close()

        val builder = Builder()
        builder.setSession("KillSwitch")
        // No añadimos ninguna ruta, por lo que ningún paquete se enrutará
        // Establecer la interfaz en modo bloqueante
        builder.setBlocking(true)
        // Opcional: establecer redes subyacentes en null para no usar ninguna red
        builder.setUnderlyingNetworks(null)

        // Crear la interfaz (esto abre el descriptor de archivo)
        vpnInterface = builder.establish()

        // Si la interfaz se establece, la VPN está activa y bloquea todo el tráfico
        // No es necesario leer/escribir en el descriptor; el sistema ya bloquea.
    }

    override fun onRevoke() {
        // El usuario revocó el permiso de VPN desde ajustes
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun createNotification(): Notification {
        val channelId = "vpn_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN KillSwitch",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VPN KillSwitch activa")
            .setContentText("Todo el tráfico está bloqueado")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }
}q
