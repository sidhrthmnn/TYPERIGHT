package com.example

import android.content.Context

/** Curated candidate source and optional local sentence inference, both coordinated with CandidateRanker. */
class NeuralCorrectionEngine private constructor(private val context: Context) {

    companion object {

        @Volatile
        private var instance: NeuralCorrectionEngine? = null

        fun getInstance(context: Context): NeuralCorrectionEngine {
            return instance ?: synchronized(this) {
                instance ?: NeuralCorrectionEngine(context.applicationContext).also { instance = it }
            }
        }

        // Curated typo proposals; these are evidence, never replacement decisions.
        internal val NEURAL_CORRECTION_MAP: Map<String, String> = mapOf(
            "teh" to "the", "recieve" to "receive", "seperate" to "separate",
            "definately" to "definitely", "tommorrow" to "tomorrow", "beleive" to "believe",
            "occured" to "occurred", "untill" to "until", "truely" to "truly",
            "freind" to "friend", "wierd" to "weird", "becuase" to "because",
            "togeather" to "together", "accomodation" to "accommodation",
            "neccessary" to "necessary", "necesary" to "necessary", "writting" to "writing",
            "realy" to "really", "beautifull" to "beautiful", "thier" to "their",
            "shoud" to "should", "whould" to "would", "coud" to "could",
            "pleas" to "please", "plz" to "please", "gud" to "good",
            "thx" to "thanks", "thanx" to "thanks", "alot" to "a lot",
            "noone" to "no one", "everytime" to "every time", "allright" to "all right",
            "lenght" to "length", "heigth" to "height", "goverment" to "government",
            "enviroment" to "environment", "pronounciation" to "pronunciation",
            "calender" to "calendar", "cemetary" to "cemetery", "embarass" to "embarrass",
            "fourty" to "forty", "guarentee" to "guarantee", "harass" to "harass",
            "maintenence" to "maintenance", "millenium" to "millennium", "noticable" to "noticeable",
            "playwrite" to "playwright", "posession" to "possession", "privilege" to "privilege",
            "questionaire" to "questionnaire", "rythm" to "rhythm", "schedule" to "schedule",
            "succesful" to "successful", "suprise" to "surprise", "tomorow" to "tomorrow",
            "unforseen" to "unforeseen", "usefull" to "useful",
            "vaccuum" to "vacuum", "vehical" to "vehicle", "visable" to "visible",
            "wether" to "whether", "wich" to "which", "woh" to "who",
            "woudl" to "would", "yuo" to "you", "yuor" to "your",
            "congradulations" to "congratulations", "dissapoint" to "disappoint",
            "dissappear" to "disappear", "existance" to "existence", "foriegn" to "foreign",
            "greatful" to "grateful", "independant" to "independent", "judgement" to "judgment",
            "liesure" to "leisure", "mispell" to "misspell", "occurence" to "occurrence",
            "persue" to "pursue", "recommand" to "recommend", "religous" to "religious",
            "supercede" to "supersede", "tendancy" to "tendency", "threshhold" to "threshold"
        )

    }

    /** Dictionary correction shares the same ranker as the IME. Execute on a worker. */
    fun correctText(input: String): String {
        if (input.isBlank()) return input
        val dictionary = DictionaryManager.getInstance(context)
        val previous = mutableListOf<String>()
        return Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*").replace(input) { match ->
            val ranked = dictionary.correctionPipeline.rank(match.value, previous)
            val output = ranked.automatic ?: match.value
            previous.add(output.lowercase()); if (previous.size > 5) previous.removeAt(0)
            output
        }
    }

    /** Optional sentence inference. Never called by rank(), predictions, or keystroke candidate generation. */
    suspend fun contextualCorrection(input: String, modelId: String): String? {
        if (input.length !in 8..320 || input.count(Char::isWhitespace) < 2) return null
        val model = GgufModelCatalog.resolve(context, modelId)
        // Keep pause correction on a small model; larger models remain explicit polish choices.
        if (model.id !in setOf("local-qwen3-0.6b", "local-qwen3-1.7b") || !LocalGgufModel.isReady(context, model)) return null
        val output = GgufPolishEngine.polish(context, input, PolishMode.PROOFREAD, preferredModel = model.id)
        return output.takeIf { MinimalContextEdit.isAllowed(input, it, DictionaryManager.getInstance(context)) }
    }

}
