package com.example.killswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var isVpnActive = false

    // Para arrastre y redimensionamiento
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isResizing = false
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialDistance = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingButton()
    }

    private fun createFloatingButton() {
        // Inflar un layout simple (un Button)
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        val button = floatingView.findViewById<Button>(R.id.floating_button)

        // Configurar parámetros de ventana
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // Restaurar tamaño y posición guardados
        val prefs = getSharedPreferences("floating_prefs", MODE_PRIVATE)
        params.width = prefs.getInt("width", 150) // Tamaño inicial en píxeles
        params.height = prefs.getInt("height", 150)
        params.x = prefs.getInt("x", 100)
        params.y = prefs.getInt("y", 100)

        // Hacer que el botón sea arrastrable y redimensionable
        button.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialWidth = params.width
                        initialHeight = params.height
                        // Detectar si el toque es en la esquina inferior derecha para redimensionar
                        isResizing = event.rawX > (v.right - 50) && event.rawY > (v.bottom - 50)
                        if (isResizing) {
                            initialDistance = getDistance(event)
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isResizing) {
                            // Redimensionar basado en la distancia desde el centro
                            val currentDistance = getDistance(event)
                            val delta = (currentDistance - initialDistance).toInt()
                            val newWidth = (initialWidth + delta).coerceAtLeast(100) // Mínimo 100px
                            val newHeight = (initialHeight + delta).coerceAtLeast(100)
                            params.width = newWidth
                            params.height = newHeight
                        } else {
                            // Arrastrar
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            params.x = initialX + dx
                            params.y = initialY + dy
                        }
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Guardar posición y tamaño
                        val prefs = getSharedPreferences("floating_prefs", MODE_PRIVATE)
                        prefs.edit().apply {
                            putInt("x", params.x)
                            putInt("y", params.y)
                            putInt("width", params.width)
                            putInt("height", params.height)
                            apply()
                        }

                        // Si no fue un arrastre (movimiento < 10px), tratar como clic
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        val displacement = sqrt((dx * dx + dy * dy).toDouble())
                        if (displacement < 10f && !isResizing) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Clic en el botón: alternar VPN
        button.setOnClickListener {
            toggleVpn()
        }

        windowManager.addView(floatingView, params)
    }

    private fun getDistance(event: MotionEvent): Float {
        // Distancia desde el centro de la vista (aproximado)
        val centerX = params.x + params.width / 2f
        val centerY = params.y + params.height / 2f
        val dx = event.rawX - centerX
        val dy = event.rawY - centerY
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun toggleVpn() {
        if (isVpnActive) {
            // Detener la VPN (restaurar conexión)
            stopService(Intent(this, BlockVpnService::class.java))
            isVpnActive = false
            Toast.makeText(this, "VPN desactivada - Red restaurada", Toast.LENGTH_SHORT).show()
        } else {
            // Iniciar la VPN (bloquear todo el tráfico)
            val intent = Intent(this, BlockVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isVpnActive = true
            Toast.makeText(this, "VPN activada - Señal bloqueada", Toast.LENGTH_SHORT).show()
        }
        // Cambiar el texto del botón
        val button = floatingView.findViewById<Button>(R.id.floating_button)
        button.text = if (isVpnActive) "OFF" else "ON"
    }

    private fun createNotification(): Notification {
        val channelId = "floating_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Botón Flotante",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("KillSwitch activo")
            .setContentText("Botón flotante en ejecución")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
