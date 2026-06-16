package co.terminal.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RadioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as RadioApplication

        when (action) {
            RadioApplication.ACTION_PLAY_PAUSE -> app.togglePlayPause()
            RadioApplication.ACTION_NEXT -> app.playNext()
            RadioApplication.ACTION_PREVIOUS -> app.playPrevious()
        }
    }
}
