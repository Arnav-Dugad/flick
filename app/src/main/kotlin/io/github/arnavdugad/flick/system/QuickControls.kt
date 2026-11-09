package io.github.arnavdugad.flick.system

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.arnavdugad.flick.MainActivity
import io.github.arnavdugad.flick.R
import io.github.arnavdugad.flick.container
import io.github.arnavdugad.flick.engine.EngineState
import io.github.arnavdugad.flick.update.UpdateNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Quick Settings tile: one tap turns Flick on or off (or opens setup if the service is off). */
class FlickTileService : TileService() {

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = container.appScope.launch { container.engine.state.collect(::render) }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val st = container.engine.state.value
        if (!st.serviceConnected) {
            openApp()
            return
        }
        container.engine.setMasterEnabled(!st.masterEnabled)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(st: EngineState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = if (st.serviceConnected && st.masterEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    !st.serviceConnected -> R.string.tile_off
                    st.masterEnabled -> R.string.tile_on
                    else -> R.string.tile_paused
                },
            )
        }
        tile.updateTile()
    }
}

/**
 * Optional ongoing notification with Pause/Resume and Auto-scroll buttons. Lives while the
 * accessibility service is connected and the user has it switched on.
 */
class StatusNotifier(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val c = context.container

    fun start() {
        scope.launch {
            combine(c.settings.settings, c.engine.state) { s, st ->
                if (!s.statusNotification) null else Triple(st.masterEnabled, st.activeProfile?.name, st.serviceConnected)
            }.distinctUntilChanged().collect { snapshot ->
                if (snapshot == null || !UpdateNotifications.canPost(context)) cancel() else post(snapshot.first, snapshot.second)
            }
        }
    }

    fun stop() {
        scope.cancel()
        cancel()
    }

    private fun post(on: Boolean, profile: String?) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (on) R.string.notif_on else R.string.notif_paused))
            .setContentText(
                if (profile != null) context.getString(R.string.notif_in_profile, profile) else context.getString(R.string.notif_waiting),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, context.getString(if (on) R.string.notif_pause else R.string.notif_resume), action(ACTION_TOGGLE))
            .addAction(0, context.getString(R.string.notif_auto_scroll), action(ACTION_AUTO_SCROLL))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
            // Notification permission revoked; nothing to show.
        }
    }

    private fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    private fun action(name: String) = PendingIntent.getBroadcast(
        context,
        name.hashCode(),
        Intent(context, StatusActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL = "status"
        private const val ID = 4202
        const val ACTION_TOGGLE = "io.github.arnavdugad.flick.TOGGLE"
        const val ACTION_AUTO_SCROLL = "io.github.arnavdugad.flick.AUTO_SCROLL"
    }
}

class StatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        when (intent.action) {
            StatusNotifier.ACTION_TOGGLE -> c.engine.setMasterEnabled(!c.engine.state.value.masterEnabled)
            StatusNotifier.ACTION_AUTO_SCROLL -> c.autoScroller.toggle()
        }
    }
}
