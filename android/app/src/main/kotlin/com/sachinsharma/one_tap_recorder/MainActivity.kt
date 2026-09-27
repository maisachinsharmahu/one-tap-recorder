package com.sachinsharma.one_tap_recorder

import android.content.Intent
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.quicksettings.TileService
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "one_tap_recorder/control"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
            when (call.method) {
                "start" -> {
                    startActivity(Intent(this, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    result.success(null)
                }
                "stop" -> {
                    startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
                    result.success(null)
                }
                "isRecording" -> result.success(RecordingService.isRecording)
                "getSettings" -> result.success(mapOf(
                    "quality" to prefs.getString("quality", "4K"),
                    "fps" to prefs.getInt("fps", 60),
                    "deviceAudio" to prefs.getBoolean("deviceAudio", true),
                    "micAudio" to prefs.getBoolean("micAudio", true)
                ))
                "saveSettings" -> {
                    @Suppress("UNCHECKED_CAST") val values = call.arguments as Map<String, Any>
                    prefs.edit()
                        .putString("quality", values["quality"] as String)
                        .putInt("fps", (values["fps"] as Number).toInt())
                        .putBoolean("deviceAudio", values["deviceAudio"] as Boolean)
                        .putBoolean("micAudio", values["micAudio"] as Boolean)
                        .apply()
                    RecorderWidgetProvider.updateAll(this)
                    result.success(null)
                }
                "requestTile" -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        val component = android.content.ComponentName(this, RecorderTileService::class.java)
                        getSystemService(StatusBarManager::class.java).requestAddTileService(
                            component,
                            "Screen record",
                            Icon.createWithResource(this, R.drawable.ic_record),
                            mainExecutor
                        ) { }
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
