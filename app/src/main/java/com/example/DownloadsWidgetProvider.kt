package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.data.BrowserDatabase
import com.example.data.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DownloadsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DownloadsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        fun triggerWidgetUpdate(context: Context) {
            val intent = Intent(context, DownloadsWidgetProvider::class.java).apply {
                action = "com.example.UPDATE_WIDGET"
            }
            context.sendBroadcast(intent)
        }

        private fun updateAllWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = BrowserDatabase.getDatabase(context)
                val downloads = try {
                    db.browserDao().getAllDownloads().first()
                } catch (e: Exception) {
                    emptyList()
                }

                val latestTask = downloads.firstOrNull()
                val totalDownloads = downloads.size

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_downloads)

                    // Setup launch app PendingIntent when widget header raw zone is clicked
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        appIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    // Bind dynamic numbers
                    views.setTextViewText(R.id.widget_status_count, "$totalDownloads Plays Ready")

                    if (latestTask != null) {
                        views.setTextViewText(R.id.widget_movie_title, latestTask.filename)
                        if (latestTask.status == "DOWNLOADING") {
                            val percent = if (latestTask.totalBytes > 0) {
                                ((latestTask.downloadedBytes.toFloat() / latestTask.totalBytes.toFloat()) * 100).toInt()
                            } else {
                                0
                            }
                            views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)
                            views.setProgressBar(R.id.widget_progress_bar, 100, percent, false)
                            views.setTextViewText(R.id.widget_extra_info, "📥 Downloading in App: $percent%")
                        } else {
                            views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)
                            views.setProgressBar(R.id.widget_progress_bar, 100, 100, false)
                            views.setTextViewText(R.id.widget_extra_info, "🍿 100% Complete! Tap to watch offline.")
                        }
                    } else {
                        views.setTextViewText(R.id.widget_movie_title, "No downloaded content yet")
                        views.setViewVisibility(R.id.widget_progress_bar, View.GONE)
                        views.setTextViewText(R.id.widget_extra_info, "Download theatrical plays or films to watch offline.")
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
