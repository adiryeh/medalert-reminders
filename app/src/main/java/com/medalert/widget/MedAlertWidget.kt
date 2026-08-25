package com.medalert.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.medalert.R

class MedAlertWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_medalert)
            views.setTextViewText(R.id.widget_tv_next_med, "MedAlert")
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
