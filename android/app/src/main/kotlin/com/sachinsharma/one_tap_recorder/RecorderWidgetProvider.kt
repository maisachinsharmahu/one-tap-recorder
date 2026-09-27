package com.sachinsharma.one_tap_recorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

class RecorderWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, manager, it)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        manager.updateAppWidget(appWidgetId, views(context, manager, appWidgetId))
    }

    companion object {
        private fun views(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews {
            val recording = RecordingService.isRecording
            val prefs = context.getSharedPreferences("recorder", Context.MODE_PRIVATE)
            val quality = prefs.getString("quality", "4K")
            val fps = prefs.getInt("fps", 60)
            val audio = when {
                prefs.getBoolean("deviceAudio", true) && prefs.getBoolean("micAudio", true) -> "DEVICE + MIC"
                prefs.getBoolean("deviceAudio", true) -> "DEVICE AUDIO"
                prefs.getBoolean("micAudio", true) -> "MIC AUDIO"
                else -> "NO AUDIO"
            }
            val intent = Intent(context, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(context, 70, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 56)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
            val layout = when {
                minWidth < 110 -> R.layout.recorder_widget_compact
                minHeight >= 110 -> R.layout.recorder_widget_expanded
                else -> R.layout.recorder_widget
            }
            return RemoteViews(context.packageName, layout).apply {
                when (layout) {
                    R.layout.recorder_widget_compact -> setOnClickPendingIntent(R.id.widget_root, pending)
                    R.layout.recorder_widget -> {
                        setTextViewText(R.id.widget_title, if (recording) "STOP RECORDING" else "RECORD")
                        setTextViewText(R.id.widget_subtitle, if (recording) "TAP TO SAVE" else "$quality • $audio")
                        setOnClickPendingIntent(R.id.widget_root, pending)
                    }
                    else -> {
                        setTextViewText(R.id.widget_title, if (recording) "STOP RECORDING" else "START RECORDING")
                        setTextViewText(R.id.widget_subtitle, if (recording) "TAP TO SAVE • $quality" else "$quality • $fps FPS")
                        setTextViewText(R.id.widget_audio, "AUDIO  $audio")
                        setOnClickPendingIntent(R.id.widget_record_button, pending)
                        val settings = PendingIntent.getActivity(
                            context, 72,
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_settings, settings)
                    }
                }
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, RecorderWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { manager.updateAppWidget(it, views(context, manager, it)) }
        }
    }
}
