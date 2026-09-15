package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Background Service that serves as an offline local proofreading engine.
 * Integrates Room database-backed dynamic grammar rules and on-device spell check predictor.
 */
class LocalProofreadEngineService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()
    private lateinit var db: AppDatabase
    private lateinit var grammarRuleDao: GrammarRuleDao
    private lateinit var localPredictor: LocalGrammarSpellPredictor

    inner class LocalBinder : Binder() {
        fun getService(): LocalProofreadEngineService = this@LocalProofreadEngineService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing LocalProofreadEngineService background proofreading engine...")
        db = AppDatabase.getDatabase(applicationContext)
        grammarRuleDao = db.grammarRuleDao()
        localPredictor = LocalGrammarSpellPredictor(applicationContext)

        // Seed default grammar rules into Room database if empty
        serviceScope.launch {
            seedDefaultRulesIfEmpty()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.action == ACTION_PROOFREAD) {
                val input = it.getStringExtra(EXTRA_INPUT_TEXT) ?: ""
                serviceScope.launch {
                    val corrected = proofread(input)
                    Log.d(TAG, "Background proofread complete. Result: $corrected")
                }
            }
        }
        return START_STICKY
    }

    /**
     * Executes offline proofreading on the given input text by querying Room database rules
     * and on-device proofreading engine.
     */
    suspend fun proofread(inputText: String): String = withContext(Dispatchers.IO) {
        if (inputText.isBlank()) return@withContext inputText

        val engine = OnDeviceProofreadEngine.getInstance(applicationContext)
        var text = engine.proofread(inputText)

        // Format list structures if present
        if (text.contains(Regex("(?m)^\\s*\\d+\\."))) {
            text = text.replace(Regex("(?m)^\\s*(\\d+\\.)\\s*"), "$1 ")
        }

        return@withContext text
    }

    /**
     * Pre-seeds Room database with essential offline grammar rules if empty.
     */
    private suspend fun seedDefaultRulesIfEmpty() {
        try {
            if (grammarRuleDao.getRuleCount() == 0) {
                Log.i(TAG, "Seeding default cached grammar rules into Room database...")
                val defaultRules = listOf(
                    GrammarRuleEntity(category = "Spelling", pattern = "teh", replacement = "the", description = "Common typo fix", priority = 10),
                    GrammarRuleEntity(category = "Spelling", pattern = "recieve", replacement = "receive", description = "Spelling correction", priority = 10),
                    GrammarRuleEntity(category = "Spelling", pattern = "adress", replacement = "address", description = "Spelling correction", priority = 10),
                    GrammarRuleEntity(category = "Spelling", pattern = "seperate", replacement = "separate", description = "Spelling correction", priority = 10),
                    GrammarRuleEntity(category = "Contraction", pattern = "dont", replacement = "don't", description = "Missing apostrophe in contraction", priority = 8),
                    GrammarRuleEntity(category = "Contraction", pattern = "cant", replacement = "can't", description = "Missing apostrophe in contraction", priority = 8),
                    GrammarRuleEntity(category = "Contraction", pattern = "wont", replacement = "won't", description = "Missing apostrophe in contraction", priority = 8),
                    GrammarRuleEntity(category = "Contraction", pattern = "im", replacement = "I'm", description = "Capitalization & apostrophe fix", priority = 9),
                    GrammarRuleEntity(category = "Grammar", pattern = "alot", replacement = "a lot", description = "Space separation fix", priority = 8),
                    GrammarRuleEntity(category = "Grammar", pattern = "infront", replacement = "in front", description = "Space separation fix", priority = 8)
                )
                grammarRuleDao.insertRules(defaultRules)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding default grammar rules", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "LocalProofreadEngineService destroyed")
    }

    companion object {
        private const val TAG = "LocalProofreadEngine"
        const val ACTION_PROOFREAD = "com.example.action.PROOFREAD"
        const val EXTRA_INPUT_TEXT = "extra_input_text"

        /**
         * Convenience helper method to run background proofreading directly from any Context.
         */
        suspend fun proofreadLocally(context: Context, input: String): String = withContext(Dispatchers.IO) {
            val engine = OnDeviceProofreadEngine.getInstance(context.applicationContext)
            return@withContext engine.proofread(input)
        }
    }
}
