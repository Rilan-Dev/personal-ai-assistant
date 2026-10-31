package dev.rilandev.personalaiassistant

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Owner-added Quick Settings launcher. This service NEVER activates the
 * microphone or runs a tool. It only brings the foreground chat app forward.
 * The user must tap "Tap to talk" to request a recording session.
 */
class AssistantTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.assistant_tile_label)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val openChat = Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val request = PendingIntent.getActivity(
                    this, 101, launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(request)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launch)
            }
        }
        // Never expose the assistant chat or microphone controls over the lockscreen.
        if (isLocked) unlockAndRun(openChat) else openChat.run()
    }
}
