package com.medalert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medalert.worker.RescheduleWorker

class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Handles: TIME_SET, TIMEZONE_CHANGED, DATE_CHANGED
        //
        // Pre-scheduled alarms use absolute epoch timestamps — a timezone or DST change
        // invalidates the entire batch. RescheduleWorker with cancelFirst=true:
        //   Phase 1: cancel ALL existing alarms atomically (before any reschedule)
        //   Phase 2: reschedule all from the new local time
        // This prevents stale alarms firing at wrong times AND duplicate alarms.
        val pending = goAsync()
        try {
            RescheduleWorker.enqueue(context, cancelFirst = true)
        } finally {
            pending.finish()
        }
    }
}
