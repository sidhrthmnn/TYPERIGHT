package com.example

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Native Android JobService executed periodically by JobScheduler to update
 * the local Small Language Model's dictionary with trending words & user vocabulary.
 */
class DictionaryUpdateJobService : JobService() {

    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "Starting periodic DictionaryUpdateJobService (Job ID: ${params?.jobId})...")
        
        jobScope.launch {
            try {
                val result = DictionaryUpdateService.syncDictionary(applicationContext)
                Log.i(TAG, "Dictionary update job finished successfully: ${result.message}")
                jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Dictionary update job failed with error", e)
                jobFinished(params, true) // reschedule if failed
            }
        }
        
        // Return true to indicate background work is ongoing in jobScope
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.w(TAG, "DictionaryUpdateJobService stopped early by system")
        jobScope.cancel()
        return true // Reschedule job
    }

    companion object {
        private const val TAG = "DictionaryUpdateJob"
        const val JOB_ID = 40402
    }
}
