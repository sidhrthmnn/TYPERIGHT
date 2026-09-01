package com.example

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper utility to manage JobScheduler periodic scheduling for DictionaryUpdateJobService.
 */
object DictionaryUpdateScheduler {

    private const val TAG = "DictUpdateScheduler"

    /**
     * Configures and enqueues the periodic dictionary synchronization job with the system JobScheduler.
     */
    fun schedulePeriodicUpdate(context: Context, forceReschedule: Boolean = false) {
        val appContext = context.applicationContext ?: context
        val settings = KeyboardSettings(appContext)

        if (!settings.vocabAutoUpdateEnabled) {
            cancelPeriodicUpdate(appContext)
            return
        }

        val jobScheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        
        // Check if job is already scheduled
        val isAlreadyScheduled = jobScheduler.allPendingJobs.any { it.id == DictionaryUpdateJobService.JOB_ID }
        if (isAlreadyScheduled && !forceReschedule) {
            Log.d(TAG, "Dictionary update job is already scheduled.")
            return
        }

        val intervalHours = settings.vocabUpdateIntervalHours.coerceIn(1, 48)
        val intervalMillis = intervalHours.toLong() * 60L * 60L * 1000L

        val componentName = ComponentName(appContext, DictionaryUpdateJobService::class.java)
        val builder = JobInfo.Builder(DictionaryUpdateJobService.JOB_ID, componentName)
            .setPeriodic(intervalMillis)
            .setPersisted(true) // Persists across reboots
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // Can execute offline
            .setRequiresBatteryNotLow(true) // Battery-friendly

        try {
            val result = jobScheduler.schedule(builder.build())
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Successfully scheduled periodic dictionary update job (Every $intervalHours hours)")
            } else {
                Log.w(TAG, "Failed to schedule dictionary update job, result code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling dictionary update job", e)
        }
    }

    /**
     * Cancels any scheduled periodic dictionary update jobs.
     */
    fun cancelPeriodicUpdate(context: Context) {
        val appContext = context.applicationContext ?: context
        val jobScheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        try {
            jobScheduler.cancel(DictionaryUpdateJobService.JOB_ID)
            Log.i(TAG, "Cancelled periodic dictionary update job")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling dictionary update job", e)
        }
    }
}
