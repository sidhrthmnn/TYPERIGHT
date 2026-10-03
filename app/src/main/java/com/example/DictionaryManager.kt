package com.example

import android.content.Context
import android.graphics.PointF
import android.provider.UserDictionary
import android.view.textservice.TextServicesManager
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

class DictionaryManager(private val context: Context) {
    internal val appContext get() = context.applicationContext

    val correctionPipeline by lazy { CandidateRanker(context.applicationContext, this) }
    @Volatile var vocabularyVersion: Int = 1
        private set
    private val contactWords = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun isRecognizedInAnyLanguage(word: String): Boolean = isWordInDictionary(word) || isContactWord(word) ||
        (RomanizedMalayalamLexicon.get(context).contains(word) &&
            (RomanizedMalayalamLexicon.get(context).entry(word)?.conversational == true || !gboardEngine.isKnownTypo(word))) ||
        MultilingualLexicon.get(context).contains(word)
    fun phoneticCandidates(word: String): List<String> = corpus.phoneticCandidates(word)
    /** Reset every adaptive store; explicit custom and Android dictionary entries survive. */
    @Volatile var adaptiveEpoch: Long = 0
        private set
    @Volatile var resettingLearning = false
        private set
    fun resetAdaptiveLearning(): kotlinx.coroutines.Job {
        adaptiveEpoch++; resettingLearning = true
        personalProfile.clear()
        correctionPipeline.learner.clear()
        correctionPipeline.clearCache()
        RomanizedMalayalamLexicon.get(context).clearProposalCache()
        synchronized(spellingCache) { spellingCache.clear() }
        vocabularyVersion++
        return scope.launch {
            try {
                ready.await()
                typingAssetsReady.await()
                val repository = UserDictionaryRepository.getInstance(context)
                val explicit = repository.explicitWords()
                repository.clearAdaptiveStores()
                mlPredictor.clearAllLearnedData().join()
                synchronized(suppressedCorrections) { suppressedCorrections.clear() }
                candidateLearnFrequency.clear(); recentlyAcceptedWords.clear(); personalizedBigrams.clear(); personalBlocklist.clear()
                userWords.clear(); userWords.addAll(explicit)
                prefs.edit().remove("personalized_bigrams").remove("personal_blocklist").putStringSet("user_words", explicit).apply()
                val nextTrie = TrieDictionary(); val nextWordTrie = WordTrie()
                commonWordsFreqMap.forEach { (word, frequency) -> nextTrie.insert(word,frequency); nextWordTrie.insert(word,frequency) }
                explicit.forEach { nextTrie.insert(it,120); nextWordTrie.insert(it,120) }
                trie = nextTrie; wordTrie = nextWordTrie
                gboardEngine.symSpellEngine.clear()
                commonWordsFreqMap.forEach { (word,frequency) -> gboardEngine.symSpellEngine.insertWord(word,frequency) }
                explicit.forEach { gboardEngine.symSpellEngine.insertWord(it,120) }
                synchronized(swipeWordIndex) { swipeWordIndex.clear() }
                commonWordsFreqMap.forEach { (word, frequency) -> indexWordForSwipe(word,frequency) }
                explicit.forEach { indexWordForSwipe(it,120) }
                nGramModel = NGramLanguageModel().also {
                    it.seedUnigramFrequencies(corpus.frequencies)
                    it.seedContextBaseline(manglish.contexts)
                }
                manglish.entries.forEach { (word, entry) -> indexWordForSwipe(word,entry.frequency) }
                loadSystemUserDictionary()
                vocabularyVersion++; correctionPipeline.clearCache()
            } finally { resettingLearning = false }
        }
    }
    fun isContactWord(word: String) = word.lowercase(java.util.Locale.ROOT) in contactWords
    fun refreshContactWords() {
        CoroutineScope(Dispatchers.IO).launch {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                contactWords.clear(); return@launch
            }
            runCatching {
                context.contentResolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { cursor ->
                    val fresh = mutableSetOf<String>()
                    while (cursor.moveToNext()) cursor.getString(0).orEmpty().split(Regex("\\s+")).forEach {
                        if (it.length in 2..32 && it.any(Char::isLetter) && it.all { char -> TypingPolicy.isWordCharacter(char) && !char.isDigit() }) fresh.add(it.lowercase(java.util.Locale.ROOT))
                    }
                    contactWords.clear(); contactWords.addAll(fresh)
                }
            }
        }
    }
    val personalProfile = PersonalTypingProfile.get(context)
    private val profileSettings by lazy { KeyboardSettings(context) }
    fun personalBoost(word: String, contextWords: List<String>): Float =
        if (profileSettings.personalizedLearningEnabled) personalProfile.boost(word, contextWords) else 0f
    fun personalCandidates(prefix: String, contextWords: List<String>): List<String> =
        if (profileSettings.personalizedLearningEnabled) personalProfile.candidates(prefix, contextWords).filter { !isBlocked(it) } else emptyList()
    fun learnedCorrection(word: String, contextWords: List<String>): String? {
        if (!profileSettings.personalizedLearningEnabled || isBlocked(word)) return null
        return personalProfile.correction(word, contextWords)?.takeIf { !isBlocked(it) && !isCorrectionSuppressed(word, it) }
    }

    val mlPredictor = PatternLearningPredictor.getInstance(context)
    private val corpus = EnglishFrequencyLexicon.get(context)
    private val manglish = RomanizedMalayalamLexicon.get(context)
    @Volatile var nGramModel = NGramLanguageModel()
        private set
    val localGrammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    val gboardEngine by lazy { GboardPredictionEngine(context) }

    fun getGboardPredictions(
        rawTyped: String,
        contextWords: List<String>,
        tapCoords: List<PointF>?,
        isSensitiveField: Boolean = false
    ): GboardSuggestionResult {
        return gboardEngine.getGboardPredictionsAndCorrections(
            rawTyped = rawTyped,
            contextWords = contextWords,
            tapCoords = tapCoords,
            dictionaryManager = this,
            isSensitiveField = isSensitiveField
        )
    }

    fun isWordInUserDictionary(word: String): Boolean {
        val w = word.lowercase().trim()
        return synchronized(userWords) { userWords.contains(w) }
    }

    fun learnSwipePattern(word: String, path: List<PointF>) {
        mlPredictor.learnSwipePattern(word, path)
    }

    fun findWordsWithPrefix(prefix: String, maxResults: Int = 3): List<String> {
        return (wordTrie.findByPrefix(prefix, maxResults) + corpus.prefix(prefix, maxResults))
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }
            .filter { isWordInDictionary(it) && !isBlocked(it) }
            .sortedWith(compareByDescending<String> { getWordFrequency(it) }.thenBy { it })
            .take(maxResults)
    }

    data class WordFrequency(val word: String, val frequency: Int)

    private val settings = KeyboardSettings(context)

    // A comprehensive, high-frequency local dictionary of English words with frequencies
    // Includes everyday vocabulary, plurals, inflected verb forms, adjectives, contractions, and multilingual words
    private val commonWords = listOf(
        // --- TOP 100 CORE WORDS ---
        WordFrequency("the", 1000), WordFrequency("be", 850), WordFrequency("to", 800),
        WordFrequency("of", 750), WordFrequency("and", 700), WordFrequency("a", 650),
        WordFrequency("in", 600), WordFrequency("that", 550), WordFrequency("have", 500),
        WordFrequency("is", 490), WordFrequency("i", 480), WordFrequency("are", 470),
        WordFrequency("it", 460), WordFrequency("for", 440), WordFrequency("not", 420),
        WordFrequency("on", 400), WordFrequency("with", 380), WordFrequency("was", 370),
        WordFrequency("he", 360), WordFrequency("as", 350), WordFrequency("you", 340),
        WordFrequency("do", 330), WordFrequency("at", 320), WordFrequency("this", 310),
        WordFrequency("but", 300), WordFrequency("his", 290), WordFrequency("by", 280),
        WordFrequency("from", 270), WordFrequency("they", 260), WordFrequency("we", 250),
        WordFrequency("say", 240), WordFrequency("her", 230), WordFrequency("she", 220),
        WordFrequency("or", 210), WordFrequency("an", 200), WordFrequency("will", 195),
        WordFrequency("my", 190), WordFrequency("one", 185), WordFrequency("all", 180),
        WordFrequency("would", 175), WordFrequency("should", 160), WordFrequency("could", 150),
        WordFrequency("must", 120), WordFrequency("might", 110), WordFrequency("shall", 90),
        WordFrequency("there", 170), WordFrequency("their", 165), WordFrequency("here", 165), WordFrequency("where", 150),
        WordFrequency("what", 160), WordFrequency("so", 155), WordFrequency("up", 150),
        WordFrequency("out", 145), WordFrequency("if", 140), WordFrequency("about", 135),
        WordFrequency("who", 130), WordFrequency("get", 125), WordFrequency("which", 120),
        WordFrequency("go", 115), WordFrequency("me", 110), WordFrequency("when", 105),
        WordFrequency("make", 100), WordFrequency("can", 98), WordFrequency("like", 96),
        WordFrequency("time", 94), WordFrequency("no", 92), WordFrequency("just", 90),
        WordFrequency("him", 88), WordFrequency("know", 86), WordFrequency("take", 84),
        WordFrequency("people", 82), WordFrequency("into", 80), WordFrequency("year", 78),
        WordFrequency("your", 76), WordFrequency("good", 74), WordFrequency("some", 72),
        WordFrequency("them", 68), WordFrequency("see", 66), WordFrequency("cat", 65),
        WordFrequency("other", 64), WordFrequency("than", 62), WordFrequency("then", 60),
        WordFrequency("now", 58), WordFrequency("look", 56), WordFrequency("only", 54),
        WordFrequency("come", 52), WordFrequency("its", 50), WordFrequency("over", 48),
        WordFrequency("think", 46), WordFrequency("also", 44), WordFrequency("back", 42),
        WordFrequency("after", 40), WordFrequency("use", 38), WordFrequency("two", 36),
        WordFrequency("how", 34), WordFrequency("our", 32), WordFrequency("work", 30),
        WordFrequency("first", 28), WordFrequency("well", 26), WordFrequency("way", 24),
        WordFrequency("even", 22), WordFrequency("new", 20), WordFrequency("want", 18),
        WordFrequency("because", 16), WordFrequency("any", 14), WordFrequency("these", 12),
        WordFrequency("give", 10), WordFrequency("day", 9), WordFrequency("most", 8),
        WordFrequency("us", 7), WordFrequency("gym", 60), WordFrequency("store", 80),

        // --- VERBS & CONJUGATIONS (Present, Past, Participle, Gerund) ---
        WordFrequency("let", 120), WordFrequency("lets", 90), WordFrequency("letting", 80),
        WordFrequency("hope", 110), WordFrequency("hopes", 80), WordFrequency("hoped", 80), WordFrequency("hoping", 80),
        WordFrequency("bring", 80), WordFrequency("confirm", 70), WordFrequency("wash", 60),
        WordFrequency("polish", 50), WordFrequency("buy", 80), WordFrequency("meet", 90),
        WordFrequency("hear", 80), WordFrequency("play", 80), WordFrequency("run", 90),
        WordFrequency("move", 70), WordFrequency("live", 80), WordFrequency("believe", 80),
        WordFrequency("happen", 70), WordFrequency("write", 90), WordFrequency("provide", 70),
        WordFrequency("stand", 70), WordFrequency("lose", 70), WordFrequency("pay", 80),
        WordFrequency("include", 70), WordFrequency("continue", 60), WordFrequency("set", 80),
        WordFrequency("learn", 80), WordFrequency("change", 80), WordFrequency("lead", 60),
        WordFrequency("understand", 70), WordFrequency("watch", 70), WordFrequency("follow", 70),
        WordFrequency("stop", 80), WordFrequency("create", 70), WordFrequency("speak", 70),
        WordFrequency("read", 80), WordFrequency("allow", 60), WordFrequency("add", 70),
        WordFrequency("spend", 70), WordFrequency("grow", 60), WordFrequency("open", 70),
        WordFrequency("walk", 70), WordFrequency("win", 70), WordFrequency("offer", 60),
        WordFrequency("remember", 70), WordFrequency("love", 90), WordFrequency("consider", 60),
        WordFrequency("appear", 50), WordFrequency("wait", 80), WordFrequency("serve", 60),
        WordFrequency("die", 60), WordFrequency("send", 90), WordFrequency("expect", 60),
        WordFrequency("build", 70), WordFrequency("stay", 70), WordFrequency("fall", 60),
        WordFrequency("cut", 60), WordFrequency("reach", 60), WordFrequency("kill", 50),
        WordFrequency("remain", 50), WordFrequency("suggest", 60), WordFrequency("raise", 50),
        WordFrequency("pass", 60), WordFrequency("sell", 70), WordFrequency("require", 60),
        WordFrequency("report", 60), WordFrequency("decide", 60), WordFrequency("pull", 60),
        WordFrequency("break", 60), WordFrequency("receive", 80), WordFrequency("agree", 60),
        WordFrequency("support", 60), WordFrequency("hit", 60), WordFrequency("produce", 60),
        WordFrequency("eat", 70), WordFrequency("cover", 60), WordFrequency("catch", 60),
        WordFrequency("draw", 60), WordFrequency("choose", 60), WordFrequency("type", 80),
        WordFrequency("am", 350), WordFrequency("has", 300), WordFrequency("had", 290),
        WordFrequency("been", 230), WordFrequency("were", 260), WordFrequency("did", 180),
        WordFrequency("doing", 120), WordFrequency("does", 140), WordFrequency("done", 110),
        WordFrequency("goes", 90), WordFrequency("went", 130), WordFrequency("gone", 80),
        WordFrequency("going", 150), WordFrequency("having", 120), WordFrequency("makes", 90),
        WordFrequency("made", 140), WordFrequency("making", 120), WordFrequency("knows", 80),
        WordFrequency("knew", 90), WordFrequency("known", 80), WordFrequency("knowing", 70),
        WordFrequency("takes", 70), WordFrequency("took", 90), WordFrequency("taken", 80),
        WordFrequency("taking", 90), WordFrequency("sees", 70), WordFrequency("saw", 100),
        WordFrequency("seen", 90), WordFrequency("seeing", 80), WordFrequency("comes", 80),
        WordFrequency("came", 110), WordFrequency("coming", 130), WordFrequency("thinks", 70),
        WordFrequency("thought", 110), WordFrequency("thinking", 90), WordFrequency("looks", 80),
        WordFrequency("looked", 90), WordFrequency("looking", 130), WordFrequency("wants", 80),
        WordFrequency("wanted", 100), WordFrequency("wanting", 60), WordFrequency("gives", 70),
        WordFrequency("gave", 80), WordFrequency("given", 70), WordFrequency("giving", 70),
        WordFrequency("uses", 60), WordFrequency("used", 90), WordFrequency("using", 80),
        WordFrequency("finds", 60), WordFrequency("found", 100), WordFrequency("finding", 70),
        WordFrequency("tells", 60), WordFrequency("told", 90), WordFrequency("telling", 70),
        WordFrequency("asks", 60), WordFrequency("asked", 90), WordFrequency("asking", 80),
        WordFrequency("works", 70), WordFrequency("worked", 80), WordFrequency("working", 110),
        WordFrequency("seems", 80), WordFrequency("seemed", 70), WordFrequency("seeming", 50),
        WordFrequency("feels", 70), WordFrequency("felt", 80), WordFrequency("feeling", 90),
        WordFrequency("tries", 60), WordFrequency("tried", 80), WordFrequency("trying", 100),
        WordFrequency("leaves", 60), WordFrequency("left", 90), WordFrequency("leaving", 70),
        WordFrequency("calls", 60), WordFrequency("called", 90), WordFrequency("calling", 80),
        WordFrequency("says", 120), WordFrequency("said", 170), WordFrequency("saying", 80),
        WordFrequency("gets", 90), WordFrequency("got", 140), WordFrequency("gotten", 70),
        WordFrequency("getting", 110), WordFrequency("helps", 60), WordFrequency("helped", 70),
        WordFrequency("helping", 70), WordFrequency("needs", 80), WordFrequency("needed", 80),
        WordFrequency("needing", 60), WordFrequency("shows", 60), WordFrequency("showed", 70),
        WordFrequency("shown", 70), WordFrequency("showing", 70), WordFrequency("hears", 60),
        WordFrequency("heard", 90), WordFrequency("hearing", 80), WordFrequency("plays", 60),
        WordFrequency("played", 70), WordFrequency("playing", 80), WordFrequency("runs", 60),
        WordFrequency("ran", 80), WordFrequency("running", 90), WordFrequency("moves", 50),
        WordFrequency("moved", 60), WordFrequency("moving", 70), WordFrequency("lives", 60),
        WordFrequency("lived", 70), WordFrequency("living", 70), WordFrequency("believes", 60),
        WordFrequency("believed", 70), WordFrequency("believing", 60), WordFrequency("brings", 60),
        WordFrequency("brought", 80), WordFrequency("bringing", 70), WordFrequency("happens", 70),
        WordFrequency("happened", 80), WordFrequency("happening", 80), WordFrequency("writes", 60),
        WordFrequency("wrote", 80), WordFrequency("written", 80), WordFrequency("writing", 90),
        WordFrequency("provides", 60), WordFrequency("provided", 70), WordFrequency("providing", 70),
        WordFrequency("sit", 70), WordFrequency("sits", 50), WordFrequency("sat", 70), WordFrequency("sitting", 70),
        WordFrequency("stands", 50), WordFrequency("stood", 70), WordFrequency("standing", 70),
        WordFrequency("loses", 50), WordFrequency("lost", 80), WordFrequency("losing", 60),
        WordFrequency("pays", 50), WordFrequency("paid", 70), WordFrequency("paying", 60),
        WordFrequency("meets", 50), WordFrequency("met", 80), WordFrequency("meeting", 90),
        WordFrequency("includes", 60), WordFrequency("included", 70), WordFrequency("including", 80),
        WordFrequency("continues", 50), WordFrequency("continued", 60), WordFrequency("continuing", 50),
        WordFrequency("sets", 60), WordFrequency("setting", 70), WordFrequency("learns", 50),
        WordFrequency("learned", 70), WordFrequency("learning", 80), WordFrequency("changes", 70),
        WordFrequency("changed", 70), WordFrequency("changing", 70), WordFrequency("leads", 50),
        WordFrequency("led", 60), WordFrequency("leading", 60), WordFrequency("understands", 50),
        WordFrequency("understood", 60), WordFrequency("understanding", 70), WordFrequency("watches", 50),
        WordFrequency("watched", 60), WordFrequency("watching", 70), WordFrequency("follows", 50),
        WordFrequency("followed", 60), WordFrequency("following", 70), WordFrequency("stops", 50),
        WordFrequency("stopped", 70), WordFrequency("stopping", 60), WordFrequency("creates", 50),
        WordFrequency("created", 70), WordFrequency("creating", 70), WordFrequency("speaks", 50),
        WordFrequency("spoke", 60), WordFrequency("spoken", 60), WordFrequency("speaking", 70),
        WordFrequency("reads", 50), WordFrequency("reading", 80), WordFrequency("allows", 50),
        WordFrequency("allowed", 60), WordFrequency("allowing", 60), WordFrequency("adds", 50),
        WordFrequency("added", 60), WordFrequency("adding", 60), WordFrequency("spends", 50),
        WordFrequency("spent", 60), WordFrequency("spending", 60), WordFrequency("grows", 50),
        WordFrequency("grew", 60), WordFrequency("grown", 60), WordFrequency("growing", 60),
        WordFrequency("opens", 50), WordFrequency("opened", 60), WordFrequency("opening", 60),
        WordFrequency("walks", 50), WordFrequency("walked", 60), WordFrequency("walking", 60),
        WordFrequency("wins", 50), WordFrequency("won", 70), WordFrequency("winning", 60),
        WordFrequency("offers", 50), WordFrequency("offered", 60), WordFrequency("offering", 60),
        WordFrequency("remembers", 50), WordFrequency("remembered", 60), WordFrequency("remembering", 60),
        WordFrequency("loves", 70), WordFrequency("loved", 70), WordFrequency("loving", 60),
        WordFrequency("considers", 40), WordFrequency("considered", 50), WordFrequency("considering", 50),
        WordFrequency("appears", 40), WordFrequency("appeared", 50), WordFrequency("appearing", 40),
        WordFrequency("buys", 50), WordFrequency("bought", 70), WordFrequency("buying", 60),
        WordFrequency("waits", 50), WordFrequency("waited", 60), WordFrequency("waiting", 70),
        WordFrequency("serves", 40), WordFrequency("served", 50), WordFrequency("serving", 50),
        WordFrequency("dies", 40), WordFrequency("died", 60), WordFrequency("dying", 50),
        WordFrequency("sends", 50), WordFrequency("sent", 80), WordFrequency("sending", 70),
        WordFrequency("expects", 40), WordFrequency("expected", 50), WordFrequency("expecting", 50),
        WordFrequency("builds", 50), WordFrequency("built", 70), WordFrequency("building", 70),
        WordFrequency("stays", 50), WordFrequency("stayed", 60), WordFrequency("staying", 60),
        WordFrequency("falls", 40), WordFrequency("fell", 50), WordFrequency("fallen", 50),
        WordFrequency("falling", 50), WordFrequency("cuts", 40), WordFrequency("cutting", 50),
        WordFrequency("reaches", 40), WordFrequency("reached", 50), WordFrequency("reaching", 50),
        WordFrequency("kills", 40), WordFrequency("killed", 50), WordFrequency("killing", 40),
        WordFrequency("remains", 40), WordFrequency("remained", 50), WordFrequency("remaining", 40),
        WordFrequency("suggests", 40), WordFrequency("suggested", 50), WordFrequency("suggesting", 50),
        WordFrequency("raises", 40), WordFrequency("raised", 50), WordFrequency("raising", 40),
        WordFrequency("passes", 40), WordFrequency("passed", 50), WordFrequency("passing", 40),
        WordFrequency("sells", 40), WordFrequency("sold", 60), WordFrequency("selling", 50),
        WordFrequency("requires", 50), WordFrequency("required", 60), WordFrequency("requiring", 50),
        WordFrequency("reports", 40), WordFrequency("reported", 50), WordFrequency("reporting", 40),
        WordFrequency("decides", 40), WordFrequency("decided", 60), WordFrequency("deciding", 50),
        WordFrequency("pulls", 40), WordFrequency("pulled", 50), WordFrequency("pulling", 40),
        WordFrequency("breaks", 40), WordFrequency("broke", 50), WordFrequency("broken", 50),
        WordFrequency("breaking", 50), WordFrequency("receives", 60), WordFrequency("received", 80),
        WordFrequency("receiving", 70), WordFrequency("agrees", 40), WordFrequency("agreed", 50),
        WordFrequency("agreeing", 40), WordFrequency("supports", 40), WordFrequency("supported", 50),
        WordFrequency("supporting", 50), WordFrequency("hits", 40), WordFrequency("hitting", 40),
        WordFrequency("produces", 40), WordFrequency("produced", 50), WordFrequency("producing", 40),
        WordFrequency("eats", 40), WordFrequency("ate", 50), WordFrequency("eaten", 40),
        WordFrequency("eating", 50), WordFrequency("covers", 40), WordFrequency("covered", 40),
        WordFrequency("covering", 40), WordFrequency("catches", 40), WordFrequency("caught", 50),
        WordFrequency("catching", 40), WordFrequency("draws", 40), WordFrequency("drew", 40),
        WordFrequency("drawn", 40), WordFrequency("drawing", 40), WordFrequency("chooses", 40),
        WordFrequency("chose", 50), WordFrequency("chosen", 50), WordFrequency("choosing", 40),
        WordFrequency("washes", 30), WordFrequency("washed", 40), WordFrequency("washing", 40),
        WordFrequency("confirms", 40), WordFrequency("confirmed", 50), WordFrequency("confirming", 50),
        WordFrequency("polishes", 30), WordFrequency("polished", 40), WordFrequency("polishing", 40),
        WordFrequency("types", 50), WordFrequency("typed", 50), WordFrequency("typing", 70),

        // --- NOUNS & PLURALS (Common everyday items, places, people, objects) ---
        WordFrequency("apple", 50), WordFrequency("apples", 50), WordFrequency("milk", 60),
        WordFrequency("car", 90), WordFrequency("cars", 80), WordFrequency("laptop", 60),
        WordFrequency("laptops", 50), WordFrequency("venue", 50), WordFrequency("venues", 40),
        WordFrequency("point", 80), WordFrequency("points", 70), WordFrequency("mail", 70),
        WordFrequency("email", 90), WordFrequency("emails", 80), WordFrequency("letter", 60),
        WordFrequency("letters", 50), WordFrequency("house", 90), WordFrequency("houses", 60),
        WordFrequency("home", 100), WordFrequency("homes", 60), WordFrequency("school", 90),
        WordFrequency("schools", 60), WordFrequency("water", 90), WordFrequency("food", 90),
        WordFrequency("phone", 100), WordFrequency("phones", 80), WordFrequency("screen", 90),
        WordFrequency("screens", 60), WordFrequency("keyboard", 110), WordFrequency("keyboards", 60),
        WordFrequency("number", 90), WordFrequency("numbers", 70), WordFrequency("message", 100),
        WordFrequency("messages", 80), WordFrequency("friend", 100), WordFrequency("friends", 90),
        WordFrequency("family", 100), WordFrequency("families", 50), WordFrequency("person", 90),
        WordFrequency("child", 80), WordFrequency("children", 80), WordFrequency("kid", 70),
        WordFrequency("kids", 70), WordFrequency("man", 90), WordFrequency("men", 80),
        WordFrequency("woman", 90), WordFrequency("women", 80), WordFrequency("boy", 70),
        WordFrequency("boys", 60), WordFrequency("girl", 70), WordFrequency("girls", 60),
        WordFrequency("dog", 80), WordFrequency("dogs", 70), WordFrequency("cat", 80),
        WordFrequency("cats", 70), WordFrequency("table", 70), WordFrequency("tables", 50),
        WordFrequency("chair", 60), WordFrequency("chairs", 50), WordFrequency("room", 80),
        WordFrequency("rooms", 60), WordFrequency("door", 70), WordFrequency("doors", 50),
        WordFrequency("window", 70), WordFrequency("windows", 50), WordFrequency("office", 80),
        WordFrequency("offices", 50), WordFrequency("business", 80), WordFrequency("businesses", 50),
        WordFrequency("company", 90), WordFrequency("companies", 60), WordFrequency("system", 80),
        WordFrequency("systems", 60), WordFrequency("program", 70), WordFrequency("programs", 50),
        WordFrequency("question", 80), WordFrequency("questions", 80), WordFrequency("problem", 80),
        WordFrequency("problems", 70), WordFrequency("answer", 70), WordFrequency("answers", 60),
        WordFrequency("story", 70), WordFrequency("stories", 60), WordFrequency("movie", 70),
        WordFrequency("movies", 60), WordFrequency("book", 80), WordFrequency("books", 70),
        WordFrequency("music", 80), WordFrequency("song", 70), WordFrequency("songs", 60),
        WordFrequency("picture", 70), WordFrequency("pictures", 60), WordFrequency("photo", 70),
        WordFrequency("photos", 60), WordFrequency("video", 80), WordFrequency("videos", 70),
        WordFrequency("city", 80), WordFrequency("cities", 60), WordFrequency("street", 70),
        WordFrequency("streets", 50), WordFrequency("country", 80), WordFrequency("countries", 60),
        WordFrequency("world", 100), WordFrequency("state", 80), WordFrequency("states", 70),
        WordFrequency("place", 90), WordFrequency("places", 70), WordFrequency("area", 80),
        WordFrequency("areas", 60), WordFrequency("money", 90), WordFrequency("dollar", 70),
        WordFrequency("dollars", 60), WordFrequency("price", 70), WordFrequency("prices", 50),
        WordFrequency("cost", 70), WordFrequency("costs", 50), WordFrequency("order", 70),
        WordFrequency("orders", 60), WordFrequency("product", 70), WordFrequency("products", 60),
        WordFrequency("service", 80), WordFrequency("services", 70), WordFrequency("job", 80),
        WordFrequency("jobs", 70), WordFrequency("team", 80), WordFrequency("teams", 60),
        WordFrequency("group", 80), WordFrequency("groups", 60), WordFrequency("party", 70),
        WordFrequency("parties", 50), WordFrequency("night", 90), WordFrequency("nights", 60),
        WordFrequency("morning", 90), WordFrequency("mornings", 50), WordFrequency("evening", 80),
        WordFrequency("evenings", 50), WordFrequency("afternoon", 80), WordFrequency("afternoons", 40),
        WordFrequency("week", 90), WordFrequency("weeks", 80), WordFrequency("month", 80),
        WordFrequency("months", 70), WordFrequency("year", 100), WordFrequency("years", 90),
        WordFrequency("hour", 80), WordFrequency("hours", 70), WordFrequency("minute", 80),
        WordFrequency("minutes", 80), WordFrequency("second", 70), WordFrequency("seconds", 60),
        WordFrequency("life", 90), WordFrequency("lives", 70), WordFrequency("hand", 80),
        WordFrequency("hands", 70), WordFrequency("eye", 80), WordFrequency("eyes", 80),
        WordFrequency("face", 80), WordFrequency("faces", 50), WordFrequency("head", 80),
        WordFrequency("heads", 50), WordFrequency("body", 70), WordFrequency("bodies", 40),
        WordFrequency("heart", 80), WordFrequency("hearts", 50), WordFrequency("mind", 80),
        WordFrequency("minds", 50), WordFrequency("idea", 80), WordFrequency("ideas", 70),
        WordFrequency("word", 80), WordFrequency("words", 80), WordFrequency("name", 90),
        WordFrequency("names", 70), WordFrequency("game", 80), WordFrequency("games", 70),
        WordFrequency("line", 70), WordFrequency("lines", 60), WordFrequency("side", 70),
        WordFrequency("sides", 50), WordFrequency("end", 80), WordFrequency("ends", 50),
        WordFrequency("reason", 70), WordFrequency("reasons", 60), WordFrequency("result", 70),
        WordFrequency("results", 60), WordFrequency("fact", 70), WordFrequency("facts", 50),
        WordFrequency("power", 70), WordFrequency("powers", 40), WordFrequency("law", 70),
        WordFrequency("laws", 50), WordFrequency("art", 70), WordFrequency("arts", 40),
        WordFrequency("war", 70), WordFrequency("wars", 40), WordFrequency("peace", 60),
        WordFrequency("information", 80), WordFrequency("news", 80), WordFrequency("report", 70),
        WordFrequency("reports", 60), WordFrequency("voice", 80), WordFrequency("voices", 50),
        WordFrequency("sound", 70), WordFrequency("sounds", 60), WordFrequency("coffee", 80),
        WordFrequency("tea", 60), WordFrequency("lunch", 70), WordFrequency("dinner", 70),
        WordFrequency("breakfast", 70), WordFrequency("bread", 60), WordFrequency("butter", 50),
        WordFrequency("cheese", 50), WordFrequency("meat", 50), WordFrequency("fruit", 60),
        WordFrequency("fruits", 50), WordFrequency("tree", 60), WordFrequency("trees", 50),
        WordFrequency("road", 70), WordFrequency("roads", 50), WordFrequency("park", 60),
        WordFrequency("parks", 40), WordFrequency("star", 60), WordFrequency("stars", 50),
        WordFrequency("sun", 70), WordFrequency("moon", 60), WordFrequency("sky", 70),
        WordFrequency("sea", 60), WordFrequency("ocean", 50), WordFrequency("river", 50),
        WordFrequency("rivers", 40), WordFrequency("earth", 70), WordFrequency("symbol", 50),
        WordFrequency("symbols", 40), WordFrequency("arrow", 50), WordFrequency("arrows", 40),
        WordFrequency("smiley", 40), WordFrequency("smileys", 30), WordFrequency("emoji", 60),
        WordFrequency("emojis", 50),

        // --- ADJECTIVES & ADVERBS (Positive, Comparative, Superlative) ---
        WordFrequency("great", 120), WordFrequency("greater", 70), WordFrequency("greatest", 60),
        WordFrequency("good", 130), WordFrequency("better", 110), WordFrequency("best", 120),
        WordFrequency("bad", 80), WordFrequency("worse", 70), WordFrequency("worst", 60),
        WordFrequency("big", 90), WordFrequency("bigger", 70), WordFrequency("biggest", 60),
        WordFrequency("small", 90), WordFrequency("smaller", 70), WordFrequency("smallest", 60),
        WordFrequency("large", 80), WordFrequency("larger", 60), WordFrequency("largest", 50),
        WordFrequency("little", 90), WordFrequency("less", 80), WordFrequency("least", 70),
        WordFrequency("more", 150), WordFrequency("most", 110), WordFrequency("many", 100),
        WordFrequency("much", 100), WordFrequency("long", 90), WordFrequency("longer", 70),
        WordFrequency("longest", 50), WordFrequency("short", 80), WordFrequency("shorter", 60),
        WordFrequency("shortest", 50), WordFrequency("high", 80), WordFrequency("higher", 70),
        WordFrequency("highest", 60), WordFrequency("low", 70), WordFrequency("lower", 60),
        WordFrequency("lowest", 50), WordFrequency("old", 90), WordFrequency("older", 70),
        WordFrequency("oldest", 50), WordFrequency("young", 80), WordFrequency("younger", 60),
        WordFrequency("youngest", 50), WordFrequency("fast", 80), WordFrequency("faster", 70),
        WordFrequency("fastest", 60), WordFrequency("slow", 70), WordFrequency("slower", 60),
        WordFrequency("slowest", 50), WordFrequency("early", 80), WordFrequency("earlier", 70),
        WordFrequency("earliest", 50), WordFrequency("late", 80), WordFrequency("later", 80),
        WordFrequency("latest", 70), WordFrequency("hard", 80), WordFrequency("harder", 60),
        WordFrequency("hardest", 50), WordFrequency("easy", 80), WordFrequency("easier", 70),
        WordFrequency("easiest", 60), WordFrequency("clear", 80), WordFrequency("clearer", 50),
        WordFrequency("clearly", 70), WordFrequency("clean", 70), WordFrequency("cleaner", 50),
        WordFrequency("close", 80), WordFrequency("closer", 60), WordFrequency("closest", 50),
        WordFrequency("far", 70), WordFrequency("further", 70), WordFrequency("furthest", 40),
        WordFrequency("simple", 80), WordFrequency("simpler", 60), WordFrequency("simplest", 50),
        WordFrequency("simply", 70), WordFrequency("strong", 70), WordFrequency("stronger", 60),
        WordFrequency("strongest", 50), WordFrequency("strongly", 50), WordFrequency("true", 80),
        WordFrequency("truly", 70), WordFrequency("false", 60), WordFrequency("real", 80),
        WordFrequency("really", 130), WordFrequency("happy", 90), WordFrequency("happier", 50),
        WordFrequency("happiest", 40), WordFrequency("happily", 50), WordFrequency("sad", 60),
        WordFrequency("safe", 70), WordFrequency("safer", 50), WordFrequency("safest", 40),
        WordFrequency("safely", 50), WordFrequency("fine", 80), WordFrequency("cool", 80),
        WordFrequency("warm", 70), WordFrequency("hot", 70), WordFrequency("cold", 80),
        WordFrequency("sweet", 60), WordFrequency("kind", 70), WordFrequency("kindly", 50),
        WordFrequency("nice", 90), WordFrequency("nicer", 50), WordFrequency("nicest", 40),
        WordFrequency("nicely", 50), WordFrequency("smart", 80), WordFrequency("smarter", 60),
        WordFrequency("smartest", 50), WordFrequency("awesome", 80), WordFrequency("fantastic", 60),
        WordFrequency("wonderful", 70), WordFrequency("beautiful", 80), WordFrequency("beautifully", 50),
        WordFrequency("perfect", 80), WordFrequency("perfectly", 70), WordFrequency("quick", 80),
        WordFrequency("quickly", 80), WordFrequency("ready", 90), WordFrequency("busy", 70),
        WordFrequency("free", 80), WordFrequency("full", 80), WordFrequency("empty", 60),
        WordFrequency("important", 90), WordFrequency("special", 80), WordFrequency("different", 90),
        WordFrequency("possible", 90), WordFrequency("impossible", 60), WordFrequency("likely", 70),
        WordFrequency("unlikely", 50), WordFrequency("main", 70), WordFrequency("major", 70),
        WordFrequency("minor", 50), WordFrequency("common", 70), WordFrequency("rare", 50),
        WordFrequency("entire", 60), WordFrequency("whole", 80), WordFrequency("complete", 70),
        WordFrequency("completely", 70), WordFrequency("correct", 80), WordFrequency("correctly", 60),
        WordFrequency("wrong", 70), WordFrequency("accurate", 60), WordFrequency("accurately", 50),
        WordFrequency("sure", 90), WordFrequency("surely", 50), WordFrequency("certain", 70),
        WordFrequency("certainly", 70), WordFrequency("probably", 80), WordFrequency("maybe", 90),
        WordFrequency("perhaps", 60), WordFrequency("definitely", 80), WordFrequency("absolutely", 70),
        WordFrequency("especially", 70), WordFrequency("particularly", 60), WordFrequency("actually", 90),
        WordFrequency("almost", 80), WordFrequency("already", 90), WordFrequency("always", 100),
        WordFrequency("never", 100), WordFrequency("sometimes", 80), WordFrequency("often", 80),
        WordFrequency("usually", 80), WordFrequency("again", 100), WordFrequency("soon", 90),
        WordFrequency("today", 100), WordFrequency("tomorrow", 90), WordFrequency("yesterday", 80),
        WordFrequency("tonight", 80), WordFrequency("together", 80), WordFrequency("alone", 60),
        WordFrequency("very", 130), WordFrequency("too", 100), WordFrequency("quite", 70),
        WordFrequency("pretty", 80), WordFrequency("fairly", 50), WordFrequency("enough", 80),
        WordFrequency("anyway", 80), WordFrequency("meanwhile", 50), WordFrequency("instead", 60),
        WordFrequency("however", 80), WordFrequency("therefore", 60), WordFrequency("furthermore", 50),

        // --- NUMBERS (Spelled out) ---
        WordFrequency("zero", 50), WordFrequency("one", 120), WordFrequency("two", 110),
        WordFrequency("three", 100), WordFrequency("four", 90), WordFrequency("five", 90),
        WordFrequency("six", 80), WordFrequency("seven", 80), WordFrequency("eight", 80),
        WordFrequency("nine", 70), WordFrequency("ten", 80), WordFrequency("eleven", 50),
        WordFrequency("twelve", 50), WordFrequency("thirteen", 40), WordFrequency("fourteen", 40),
        WordFrequency("fifteen", 50), WordFrequency("sixteen", 40), WordFrequency("seventeen", 40),
        WordFrequency("eighteen", 40), WordFrequency("nineteen", 40), WordFrequency("twenty", 60),
        WordFrequency("thirty", 50), WordFrequency("forty", 50), WordFrequency("fifty", 50),
        WordFrequency("hundred", 70), WordFrequency("thousand", 70), WordFrequency("million", 70),
        WordFrequency("first", 110), WordFrequency("second", 100), WordFrequency("third", 80),
        WordFrequency("fourth", 60), WordFrequency("fifth", 50),

        // --- PRONOUNS, PREPOSITIONS & CONJUNCTIONS ---
        WordFrequency("someone", 80), WordFrequency("everyone", 80), WordFrequency("anyone", 70),
        WordFrequency("no one", 60), WordFrequency("nobody", 60), WordFrequency("somebody", 60),
        WordFrequency("everybody", 70), WordFrequency("anybody", 60), WordFrequency("something", 100),
        WordFrequency("everything", 90), WordFrequency("anything", 80), WordFrequency("nothing", 80),
        WordFrequency("somewhere", 60), WordFrequency("everywhere", 50), WordFrequency("anywhere", 50),
        WordFrequency("nowhere", 40), WordFrequency("myself", 60), WordFrequency("yourself", 60),
        WordFrequency("himself", 60), WordFrequency("herself", 60), WordFrequency("itself", 60),
        WordFrequency("ourselves", 50), WordFrequency("themselves", 60), WordFrequency("whoever", 40),
        WordFrequency("whatever", 60), WordFrequency("whichever", 40), WordFrequency("whenever", 50),
        WordFrequency("wherever", 50), WordFrequency("without", 80), WordFrequency("within", 70),
        WordFrequency("through", 90), WordFrequency("during", 70), WordFrequency("before", 90),
        WordFrequency("under", 80), WordFrequency("around", 80), WordFrequency("among", 60),
        WordFrequency("across", 70), WordFrequency("behind", 70), WordFrequency("beyond", 50),
        WordFrequency("against", 70), WordFrequency("toward", 60), WordFrequency("towards", 60),
        WordFrequency("upon", 60), WordFrequency("above", 70), WordFrequency("below", 60),
        WordFrequency("between", 80), WordFrequency("since", 80), WordFrequency("until", 80),
        WordFrequency("till", 50), WordFrequency("while", 80), WordFrequency("although", 70),
        WordFrequency("though", 80), WordFrequency("even though", 60), WordFrequency("unless", 60),
        WordFrequency("whether", 60), WordFrequency("nor", 50), WordFrequency("yet", 70),

        // --- GREETINGS, SIGN-OFFS & INTERPERSONAL ---
        WordFrequency("hello", 120), WordFrequency("hey", 110), WordFrequency("hi", 120),
        WordFrequency("dear", 80), WordFrequency("greetings", 50), WordFrequency("welcome", 80),
        WordFrequency("please", 110), WordFrequency("thanks", 120), WordFrequency("thank", 110),
        WordFrequency("sorry", 90), WordFrequency("pardon", 40), WordFrequency("excuse", 50),
        WordFrequency("regards", 70), WordFrequency("sincerely", 60), WordFrequency("cheers", 70),
        WordFrequency("warmly", 50), WordFrequency("congratulations", 50), WordFrequency("congrats", 60),
        WordFrequency("bye", 80), WordFrequency("goodbye", 70), WordFrequency("john", 70),
        WordFrequency("sally", 60), WordFrequency("alex", 60), WordFrequency("david", 60),
        WordFrequency("mary", 60), WordFrequency("sarah", 60), WordFrequency("mike", 60),
        WordFrequency("chris", 60), WordFrequency("james", 60), WordFrequency("emma", 60),

        // --- CONTRACTIONS WITH HIGH PRIORITY ---
        WordFrequency("don't", 400), WordFrequency("can't", 350), WordFrequency("won't", 300),
        WordFrequency("I'm", 450), WordFrequency("I've", 350), WordFrequency("I'll", 350),
        WordFrequency("I'd", 300), WordFrequency("you're", 380), WordFrequency("you've", 250),
        WordFrequency("you'll", 250), WordFrequency("you'd", 220), WordFrequency("they're", 320),
        WordFrequency("they've", 220), WordFrequency("they'll", 220), WordFrequency("they'd", 200),
        WordFrequency("we're", 320), WordFrequency("we've", 250), WordFrequency("we'll", 250),
        WordFrequency("we'd", 200), WordFrequency("it's", 450), WordFrequency("that's", 380),
        WordFrequency("what's", 320), WordFrequency("there's", 300), WordFrequency("here's", 280),
        WordFrequency("where's", 250), WordFrequency("how's", 220), WordFrequency("who's", 220),
        WordFrequency("he's", 300), WordFrequency("she's", 300), WordFrequency("isn't", 250),
        WordFrequency("aren't", 220), WordFrequency("wasn't", 250), WordFrequency("weren't", 220),
        WordFrequency("hasn't", 220), WordFrequency("haven't", 250), WordFrequency("hadn't", 200),
        WordFrequency("couldn't", 260), WordFrequency("shouldn't", 260), WordFrequency("wouldn't", 260),
        WordFrequency("doesn't", 300), WordFrequency("didn't", 320), WordFrequency("let's", 300),
        WordFrequency("mustn't", 150),

        // --- APP & TECH VOCABULARY ---
        WordFrequency("typeright", 90), WordFrequency("flow", 70), WordFrequency("wispr", 60),
        WordFrequency("ai", 80), WordFrequency("smart", 80), WordFrequency("device", 80),
        WordFrequency("touch", 70), WordFrequency("swipe", 70), WordFrequency("gesture", 60),
        WordFrequency("cursor", 60), WordFrequency("space", 80), WordFrequency("delete", 70),
        WordFrequency("backspace", 70), WordFrequency("shift", 60), WordFrequency("enter", 70),
        WordFrequency("setting", 70), WordFrequency("settings", 80), WordFrequency("theme", 70),
        WordFrequency("themes", 60), WordFrequency("dark", 70), WordFrequency("light", 70),
        WordFrequency("sound", 70), WordFrequency("haptic", 60), WordFrequency("vibrate", 50),
        WordFrequency("vibration", 50), WordFrequency("google", 90), WordFrequency("android", 90),
        WordFrequency("gboard", 80), WordFrequency("clipboard", 70), WordFrequency("paste", 70),
        WordFrequency("copy", 70), WordFrequency("cut", 60), WordFrequency("predict", 70),
        WordFrequency("prediction", 70), WordFrequency("correct", 80), WordFrequency("correction", 80),
        WordFrequency("autocorrect", 80), WordFrequency("grammar", 80), WordFrequency("spell", 70),
        WordFrequency("spelling", 80), WordFrequency("dictate", 60), WordFrequency("dictation", 60),
        WordFrequency("mic", 60), WordFrequency("microphone", 60),

        // --- PROPER NOUNS & CALENDAR ---
        WordFrequency("Monday", 60), WordFrequency("Tuesday", 55), WordFrequency("Wednesday", 55),
        WordFrequency("Thursday", 55), WordFrequency("Friday", 60), WordFrequency("Saturday", 60),
        WordFrequency("Sunday", 60), WordFrequency("January", 50), WordFrequency("February", 50),
        WordFrequency("March", 50), WordFrequency("April", 50), WordFrequency("May", 60),
        WordFrequency("June", 50), WordFrequency("July", 50), WordFrequency("August", 50),
        WordFrequency("September", 50), WordFrequency("October", 50), WordFrequency("November", 50),
        WordFrequency("December", 50), WordFrequency("English", 70), WordFrequency("Spanish", 60),
        WordFrequency("French", 60), WordFrequency("German", 55), WordFrequency("London", 50),
        WordFrequency("Paris", 50), WordFrequency("America", 60), WordFrequency("Canada", 50),

        // --- MULTI-LINGUAL SUPPORT WORDS (Spanish, French, German) ---
        WordFrequency("hola", 50), WordFrequency("gracias", 45), WordFrequency("amigo", 35),
        WordFrequency("casa", 40), WordFrequency("tiempo", 35), WordFrequency("por", 50),
        WordFrequency("favor", 45), WordFrequency("bueno", 40), WordFrequency("bien", 45),
        WordFrequency("bonjour", 45), WordFrequency("merci", 45), WordFrequency("oui", 50),
        WordFrequency("amour", 30), WordFrequency("maison", 30), WordFrequency("hallo", 45),
        WordFrequency("danke", 45), WordFrequency("ja", 50), WordFrequency("nein", 40),
        WordFrequency("freund", 30), WordFrequency("gut", 40)
    ).sortedByDescending { it.frequency }

    // Next-word prediction bigrams map
    private val bigrams = mapOf(
        "the" to listOf("first", "same", "best", "next", "one", "people", "way", "world"),
        "to" to listOf("be", "go", "do", "have", "make", "say", "get", "take", "your"),
        "i" to listOf("have", "think", "want", "know", "see", "go", "get", "like", "will", "am"),
        "you" to listOf("can", "are", "have", "will", "do", "know", "want", "like", "get"),
        "we" to listOf("have", "can", "are", "will", "do", "go", "want", "think"),
        "it" to listOf("is", "was", "will", "has", "seems", "feels", "looks", "works"),
        "he" to listOf("is", "was", "has", "said", "will", "says", "wants", "knows"),
        "she" to listOf("is", "was", "has", "said", "will", "says", "wants", "knows"),
        "this" to listOf("is", "was", "will", "has", "keyboard", "device", "app"),
        "my" to listOf("keyboard", "name", "voice", "device", "phone", "work", "friend"),
        "hello" to listOf("world", "there", "everyone", "my", "friend"),
        "type" to listOf("right", "here", "something", "your", "text"),
        "voice" to listOf("typing", "recognition", "input", "control"),
        "ai" to listOf("polish", "keyboard", "engine", "model", "smart"),
        "smart" to listOf("keyboard", "typing", "suggestion", "device"),
        "good" to listOf("morning", "day", "afternoon", "night", "job", "idea", "luck"),
        "are" to listOf("you", "they", "we", "the", "not", "going", "doing", "here"),
        "is" to listOf("the", "a", "not", "it", "he", "she", "good", "great", "this"),
        "was" to listOf("the", "a", "not", "good", "great", "it", "he", "she", "there"),
        "were" to listOf("you", "they", "we", "not", "going", "there", "here"),
        "have" to listOf("a", "to", "been", "the", "not", "no", "some", "any"),
        "has" to listOf("been", "a", "the", "not", "no", "to"),
        "had" to listOf("been", "a", "the", "not", "no", "to"),
        "do" to listOf("you", "not", "it", "the", "this", "we", "they"),
        "does" to listOf("not", "it", "he", "she", "this", "the"),
        "did" to listOf("you", "not", "it", "he", "she", "they", "we"),
        "can" to listOf("be", "do", "have", "go", "make", "get", "you", "we"),
        "will" to listOf("be", "have", "do", "go", "get", "make", "not"),
        "would" to listOf("be", "have", "like", "do", "go", "get", "not"),
        "could" to listOf("be", "have", "do", "go", "get", "not"),
        "should" to listOf("be", "have", "do", "go", "get", "not"),
        "how" to listOf("are you", "do you", "is it") // Phrase prediction hook
    ) + ComprehensiveLexicon.EXTENDED_BIGRAMS

    // Key positions on a normalized 1.0 x 1.0 coordinate grid for proximity calculations
    private val keyCoordinates = mapOf(
        'q' to PointF(0.05f, 0.16f), 'w' to PointF(0.15f, 0.16f), 'e' to PointF(0.25f, 0.16f),
        'r' to PointF(0.35f, 0.16f), 't' to PointF(0.45f, 0.16f), 'y' to PointF(0.55f, 0.16f),
        'u' to PointF(0.65f, 0.16f), 'i' to PointF(0.75f, 0.16f), 'o' to PointF(0.85f, 0.16f),
        'p' to PointF(0.95f, 0.16f),

        'a' to PointF(0.10f, 0.50f), 's' to PointF(0.20f, 0.50f), 'd' to PointF(0.30f, 0.50f),
        'f' to PointF(0.40f, 0.50f), 'g' to PointF(0.50f, 0.50f), 'h' to PointF(0.60f, 0.50f),
        'j' to PointF(0.70f, 0.50f), 'k' to PointF(0.80f, 0.50f), 'l' to PointF(0.90f, 0.50f),

        'z' to PointF(0.20f, 0.83f), 'x' to PointF(0.30f, 0.83f), 'c' to PointF(0.40f, 0.83f),
        'v' to PointF(0.50f, 0.83f), 'b' to PointF(0.60f, 0.83f), 'n' to PointF(0.70f, 0.83f),
        'm' to PointF(0.80f, 0.83f)
    )

    // Slang expansion map (Abbreviations)
    private val slangExpansions = mapOf(
        "omw" to "on my way",
        "brb" to "be right back",
        "lol" to "laughing out loud",
        "g2g" to "got to go",
        "tbh" to "to be honest",
        "idk" to "I don't know",
        "imo" to "in my opinion",
        "imho" to "in my humble opinion",
        "btw" to "by the way",
        "fyi" to "for your information",
        "rn" to "right now",
        "asap" to "as soon as possible",
        "ttyl" to "talk to you later",
        "np" to "no problem",
        "yw" to "you're welcome",
        "ty" to "thank you",
        "pls" to "please",
        "plz" to "please",
        "ikr" to "I know, right",
        "smh" to "shaking my head",
        "fwiw" to "for what it's worth",
        "tldr" to "too long; didn't read",
        "afaik" to "as far as I know",
        "nvm" to "never mind",
        "ofc" to "of course",
        "fr" to "for real",
        "frfr" to "for real for real",
        "ngl" to "not gonna lie",
        "hmu" to "hit me up",
        "lmk" to "let me know",
        "goat" to "greatest of all time"
    )

    // Emoji Prediction dictionary
    private val emojiPredictions = mapOf(
        "love" to "❤️", "thanks" to "🙏", "thank" to "🙏", "smile" to "😊",
        "happy" to "😊", "fire" to "🔥", "cool" to "😎", "cat" to "🐱",
        "dog" to "🐶", "laugh" to "😂", "sad" to "😢", "angry" to "😡",
        "celebrate" to "🎉", "ok" to "👌", "yes" to "👍", "no" to "👎",
        "idea" to "💡", "money" to "💰", "car" to "🚗", "star" to "⭐",
        "sun" to "☀️", "clock" to "⏰", "heart" to "❤️"
    )

    // Profanity list for filtering
    private val profaneWords = setOf(
        "damn", "hell", "crap", "shit", "fuck", "bitch", "asshole"
    )

    // Dynamic User Dictionary, Personal Blocklist, Blocked Suggestions, Learned Bigrams, and Suppressed Corrections
    private val prefs = context.getSharedPreferences("typeright_dictionary", Context.MODE_PRIVATE)
    private val userWords = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val personalBlocklist = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val blockedSuggestions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val personalizedBigrams = mutableMapOf<String, MutableList<String>>()
    private val suppressedCorrections = mutableMapOf<String, MutableSet<String>>()
    private val recentlyAcceptedWords = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    fun recordAcceptedWord(word: String) {
        val clean = word.lowercase().trim()
        if (clean.isNotEmpty()) {
            synchronized(recentlyAcceptedWords) {
                recentlyAcceptedWords.add(clean)
                if (recentlyAcceptedWords.size > 50) {
                    val iterator = recentlyAcceptedWords.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun isCorrectionSuppressed(originalWord: String, correctedWord: String): Boolean {
        val original = originalWord.lowercase(java.util.Locale.ROOT).trim()
        return original in personalBlocklist || personalProfile.rejectionPenalty(originalWord, correctedWord) > 0f || synchronized(suppressedCorrections) {
            suppressedCorrections[original]?.contains(correctedWord.lowercase(java.util.Locale.ROOT).trim()) == true
        }
    }

    fun suppressCorrection(originalWord: String, correctedWord: String, persistFeedback: Boolean = profileSettings.personalizedLearningEnabled) {
        if (persistFeedback) correctionPipeline.recordRejection(originalWord, correctedWord)
        if (persistFeedback) personalProfile.reject(originalWord, correctedWord)
        val orig = originalWord.lowercase().trim()
        val corr = correctedWord.lowercase().trim()
        if (!persistFeedback && orig.isNotEmpty() && corr.isNotEmpty()) {
            synchronized(suppressedCorrections) { suppressedCorrections.getOrPut(orig) { mutableSetOf() }.add(corr) }
        }
    }
    fun clearSessionRejections() { synchronized(suppressedCorrections) { suppressedCorrections.clear() }; correctionPipeline.clearCache() }

    private val commonTechnicalAndAbbreviations = setOf(
        "json", "api", "http", "https", "sql", "html", "css", "xml", "rest", "sdk",
        "ai", "ime", "ui", "ux", "id", "url", "ip", "jwt", "uri", "uuid", "apk", "aab",
        "cpu", "gpu", "ram", "rom", "db", "vm", "os", "io", "cli", "gui", "ssh", "ssl",
        "tls", "ftp", "dns", "tcp", "udp", "csv", "svg", "png", "jpg", "jpeg", "gif",
        "pdf", "doc", "docx", "zip", "tar", "gz", "tflite", "llm", "nlp", "ocr", "stt", "tts"
    )

    fun isCodeOrSpecialToken(word: String): Boolean {
        val clean = word.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()

        // Technical terms & common abbreviations
        if (commonTechnicalAndAbbreviations.contains(lower)) return true

        // URLs and emails
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") || lower.contains("@")) return true

        // Snake_case or camelCase or kebab-case
        if (clean.contains("_") || clean.contains("-")) return true
        val hasLower = clean.any { it.isLowerCase() }
        val hasUpper = clean.any { it.isUpperCase() }
        // camelCase or mixed case inside word
        if (hasLower && hasUpper && clean.drop(1).any { it.isUpperCase() }) return true

        // Alphanumeric tokens (e.g. utf8, h264, mp3)
        val hasLetters = clean.any { it.isLetter() }
        val hasDigits = clean.any { it.isDigit() }
        if (hasLetters && hasDigits) return true

        // Special symbols or code syntax
        if (clean.any { it in "@#/$%^&*+=\\/[]{}<>" }) return true

        return false
    }

    // High-performance Trie Data Structure for O(k) prefix matching & fast fuzzy autocorrection
    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord = false
        var frequency = 0
        var word: String? = null
    }

    class TrieDictionary {
        val root = TrieNode()

        fun insert(word: String, frequency: Int) {
            val clean = word.lowercase().trim()
            if (clean.isEmpty()) return
            var curr = root
            for (ch in clean) {
                curr = curr.children.getOrPut(ch) { TrieNode() }
            }
            curr.isWord = true
            curr.frequency = maxOf(curr.frequency, frequency)
            curr.word = word
        }

        fun searchPrefix(prefix: String, maxResults: Int = 15): List<Pair<String, Int>> {
            val clean = prefix.lowercase().trim()
            if (clean.isEmpty()) return emptyList()
            var curr = root
            for (ch in clean) {
                curr = curr.children[ch] ?: return emptyList()
            }
            val results = mutableListOf<Pair<String, Int>>()
            collectWords(curr, results)
            return results.sortedByDescending { it.second }.take(maxResults)
        }

        private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>) {
            if (node.isWord && node.word != null) {
                results.add(Pair(node.word!!, node.frequency))
            }
            for (child in node.children.values) {
                collectWords(child, results)
            }
        }

        fun searchFuzzy(target: String, maxDistance: Float = 2.0f, maxResults: Int = 5): List<Pair<String, Float>> {
            val clean = target.lowercase().trim()
            if (clean.isEmpty()) return emptyList()
            val results = mutableListOf<Pair<String, Float>>()
            val currentRow = FloatArray(clean.length + 1) { it.toFloat() }

            for ((ch, childNode) in root.children) {
                searchFuzzyRecursive(childNode, ch, clean, currentRow, results, maxDistance)
            }
            return results.sortedBy { it.second }.take(maxResults)
        }

        private fun searchFuzzyRecursive(
            node: TrieNode,
            char: Char,
            target: String,
            prevRow: FloatArray,
            results: MutableList<Pair<String, Float>>,
            maxDistance: Float
        ) {
            val cols = target.length + 1
            val currentRow = FloatArray(cols)
            currentRow[0] = prevRow[0] + 1.0f

            var minInRow = currentRow[0]
            for (i in 1 until cols) {
                val subCost = if (target[i - 1] == char) 0.0f else 1.0f
                val insertCost = currentRow[i - 1] + 1.0f
                val deleteCost = prevRow[i] + 1.0f
                val replaceCost = prevRow[i - 1] + subCost
                currentRow[i] = minOf(insertCost, deleteCost, replaceCost)
                if (currentRow[i] < minInRow) {
                    minInRow = currentRow[i]
                }
            }

            if (currentRow.last() <= maxDistance && node.isWord && node.word != null) {
                results.add(Pair(node.word!!, currentRow.last()))
            }

            if (minInRow <= maxDistance) {
                for ((ch, childNode) in node.children) {
                    searchFuzzyRecursive(childNode, ch, target, currentRow, results, maxDistance)
                }
            }
        }
    }

    @Volatile private var trie = TrieDictionary()
    @Volatile var wordTrie = WordTrie()
        private set
    private val commonWordsSet = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val commonWordsFreqMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val swipeWordIndex = HashMap<Pair<Char, Char>, MutableMap<String, Int>>(700)

    val neuralEngine by lazy { NeuralCorrectionEngine.getInstance(context) }
    private val database = AppDatabase.getDatabase(context)
    private val learnedWordDao = database.learnedWordDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val textServicesManager: TextServicesManager? = try {
        context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
    } catch (e: Throwable) {
        null
    }

    private val candidateLearnFrequency = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun indexWordForSwipe(word: String, freq: Int) {
        val clean = word.lowercase().trim().filter { it.isLetter() }
        if (clean.length >= 2) {
            val key = Pair(clean.first(), clean.last())
            synchronized(swipeWordIndex) {
                val bucket = swipeWordIndex.getOrPut(key) { LinkedHashMap() }
                val normalized=word.lowercase(java.util.Locale.ROOT)
                bucket[normalized]=maxOf(freq,bucket[normalized] ?: 0)
            }
        }
    }

    val ready = scope.async(Dispatchers.Default) {
        // Startup index work never runs in an IME callback.
        commonWords.forEach {
            val lower = it.word.lowercase()
            commonWordsSet.add(lower)
            commonWordsFreqMap[lower] = it.frequency
            indexWordForSwipe(it.word, it.frequency)
            trie.insert(it.word, it.frequency)
            wordTrie.insert(it.word, it.frequency)
            gboardEngine.symSpellEngine.insertWord(it.word, it.frequency)
        }
        // Build supplemental vocabulary for comprehensive English coverage
        SUPPLEMENTAL_WORDS.forEach { word ->
            val lower = word.lowercase()
            if (!commonWordsSet.contains(lower)) {
                commonWordsSet.add(lower)
                commonWordsFreqMap[lower] = 60
                indexWordForSwipe(lower, 60)
                trie.insert(lower, 60)
                wordTrie.insert(lower, 60)
                gboardEngine.symSpellEngine.insertWord(lower, 60)
            }
        }
        // Ingest comprehensive lexicon across technology, conversation, and modern mobile domains
        ComprehensiveLexicon.WORDS.forEach { item ->
            val lower = item.word.lowercase()
            if (!commonWordsSet.contains(lower)) {
                commonWordsSet.add(lower)
                commonWordsFreqMap[lower] = item.frequency
                indexWordForSwipe(item.word, item.frequency)
                trie.insert(item.word, item.frequency)
                wordTrie.insert(item.word, item.frequency)
                gboardEngine.symSpellEngine.insertWord(item.word, item.frequency)
            }
        }
        loadUserDictionary()
        loadWordsFromDatabase()
        loadSystemUserDictionary()
    }

    val typingAssetsReady = scope.async(Dispatchers.Default) {
        ready.await(); corpus.ready.await(); manglish.ready.await()
        nGramModel.seedUnigramFrequencies(corpus.frequencies)
        nGramModel.seedContextBaseline(manglish.contexts)
        manglish.entries.forEach { (word, entry) -> indexWordForSwipe(word,entry.frequency) }
        vocabularyVersion++
    }

    private fun loadSystemUserDictionary() {
        try {
            val cursor = context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                null, null, null
            )
            cursor?.use {
                val wordIndex = it.getColumnIndex(UserDictionary.Words.WORD)
                while (it.moveToNext()) {
                    if (wordIndex >= 0) {
                        val word = it.getString(wordIndex)
                        if (!word.isNullOrBlank()) {
                            val clean = word.lowercase().trim()
                            synchronized(userWords) {
                                userWords.add(clean)
                            }
                            indexWordForSwipe(clean, 40)
                            trie.insert(clean, 40)
                            wordTrie.insert(clean, 40)
                            gboardEngine.symSpellEngine.insertWord(clean, 40)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Content provider unavailable
        }
    }

    fun reloadFromDatabase() {
        scope.launch {
            try {
                val dbWords = learnedWordDao.getAllWords()
                synchronized(userWords) {
                    dbWords.forEach {
                        val clean = it.word.lowercase().trim()
                        if (clean.isNotEmpty()) {
                            userWords.add(clean)
                            val freq = maxOf(35, it.frequency)
                            indexWordForSwipe(clean, freq)
                            trie.insert(clean, freq)
                            wordTrie.insert(clean, freq)
                            gboardEngine.symSpellEngine.insertWord(clean, freq)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Synchronizes trending words and user-specific vocabulary into memory caches,
     * Prefix Tries, SymSpell correction index, N-Gram Language Model, and ML Markov models.
     */
    fun bulkInsertTrendingAndUserVocab(
        words: List<LearnedWord>,
        bigrams: Map<String, List<String>> = emptyMap()
    ) {
        synchronized(userWords) {
            words.forEach { item ->
                val clean = item.word.lowercase().trim()
                if (clean.isNotEmpty() && clean.length >= 2 && !isProfane(clean)) {
                    userWords.add(clean)
                    val freq = maxOf(35, item.frequency)
                    indexWordForSwipe(clean, freq)
                    trie.insert(clean, freq)
                    wordTrie.insert(clean, freq)
                    gboardEngine.symSpellEngine.insertWord(clean, freq)
                }
            }
        }

        // Register trending & learned bigrams
        bigrams.forEach { (prev, nextList) ->
            val p = prev.lowercase().trim()
            val targetList = personalizedBigrams.getOrPut(p) { mutableListOf() }
            nextList.forEach { next ->
                val n = next.lowercase().trim()
                if (n.isNotEmpty() && !targetList.contains(n)) {
                    targetList.add(0, n)
                    if (targetList.size > 10) targetList.removeAt(targetList.size - 1)
                }
                nGramModel.addBigram(p, n, 2)
            }
        }

        saveUserDictionary()
    }

    private fun loadWordsFromDatabase() {
        val epoch = adaptiveEpoch
        scope.launch {
            try {
                val dbWords = learnedWordDao.getAllWords()
                if (epoch != adaptiveEpoch || resettingLearning) return@launch
                synchronized(userWords) {
                    dbWords.forEach {
                        userWords.add(it.word)
                        val freq = maxOf(30, it.frequency)
                        indexWordForSwipe(it.word, freq)
                        trie.insert(it.word, freq)
                        wordTrie.insert(it.word, freq)
                        gboardEngine.symSpellEngine.insertWord(it.word, freq)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun loadUserDictionary() {
        val loaded = prefs.getStringSet("user_words", emptySet()) ?: emptySet()
        // Filter out corrupted typos from historical sessions
        val validWords = loaded.filter { word ->
            val clean = word.lowercase().trim()
            !gboardEngine.commonTypoLookup.containsKey(clean) &&
            !gboardEngine.contractionLookup.containsKey(clean)
        }.toSet()

        userWords.clear()
        userWords.addAll(validWords)
        if (validWords.size != loaded.size) {
            prefs.edit().putStringSet("user_words", validWords).apply()
        }
        validWords.forEach {
            indexWordForSwipe(it, 35)
            trie.insert(it, 35)
            wordTrie.insert(it, 35)
            gboardEngine.symSpellEngine.insertWord(it, 35)
        }
        personalBlocklist.addAll(prefs.getStringSet("personal_blocklist", emptySet()) ?: emptySet())
        blockedSuggestions.addAll(prefs.getStringSet("blocked_suggestions", emptySet()) ?: emptySet())
        val bigramStr = prefs.getString("personalized_bigrams", "") ?: ""
        if (bigramStr.isNotEmpty()) {
            try {
                bigramStr.split(";").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val key = parts[0]
                        val values = parts[1].split(",")
                        personalizedBigrams[key] = values.toMutableList()
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }

    private fun saveUserDictionary() {
        prefs.edit().apply {
            putStringSet("user_words", userWords)
            putStringSet("personal_blocklist", personalBlocklist)
            putStringSet("blocked_suggestions", blockedSuggestions)
            val bigramStr = personalizedBigrams.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }
            putString("personalized_bigrams", bigramStr)
            apply()
        }
    }

    fun learnWord(word: String, explicit: Boolean = false) {
        vocabularyVersion++
        val clean = MultilingualLexicon.normalize(word).trim().trim { !TypingPolicy.isWordCharacter(it) }
        if (clean.isEmpty() || clean.length < 2 || isProfane(clean)) return
        if (commonWordsSet.contains(clean)) return

        // Guard against learning accidental typos unless explicitly tapped by user
        if (!explicit) {
            if (gboardEngine.commonTypoLookup.containsKey(clean) ||
                gboardEngine.contractionLookup.containsKey(clean)
            ) {
                return
            }
            val count = (candidateLearnFrequency[clean] ?: 0) + 1
            candidateLearnFrequency[clean] = count
            if (count < 3) return
        }

        val isBaseWord = commonWords.any { it.word.lowercase() == clean }
        if (!isBaseWord) {
            val alreadyLearned = synchronized(userWords) {
                if (!userWords.contains(clean)) {
                    userWords.add(clean)
                    indexWordForSwipe(clean, 35)
                    trie.insert(clean, 35)
                    wordTrie.insert(clean, 35)
                    gboardEngine.symSpellEngine.insertWord(clean, 35)
                    false
                } else {
                    true
                }
            }
            if (!alreadyLearned) {
                saveUserDictionary()
            }
            // Save to Room database asynchronously
            scope.launch {
                try {
                    val existing = learnedWordDao.getWord(clean)
                    if (existing != null) {
                        learnedWordDao.insertWord(existing.copy(frequency = existing.frequency + 1, timestamp = System.currentTimeMillis()))
                    } else {
                        learnedWordDao.insertWord(LearnedWord(word = clean, frequency = 1))
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun syncBlockedWords(words: Set<String>) {
        blockedSuggestions.addAll(words.map { it.lowercase().trim() })
    }

    /**
     * Blocks a word from ever being suggested across all predictive engines.
     * Synchronously removes from in-memory dictionary, personalized models,
     * and asynchronously removes from Room database tables.
     */
    fun blockSuggestion(word: String) {
        val clean = word.lowercase(java.util.Locale.ROOT).trim().trim { !it.isLetterOrDigit() && it != '\'' }
        if (clean.isNotEmpty()) {
            blockedSuggestions.add(clean)
            synchronized(userWords) {
                userWords.remove(clean)
            }
            candidateLearnFrequency.remove(clean)
            personalizedBigrams.remove(clean)
            personalizedBigrams.values.forEach { it.remove(clean) }
            saveUserDictionary()
            scope.launch {
                try {
                    UserDictionaryRepository.getInstance(context).blockSuggestion(clean, word.trim())
                } catch (e: Exception) {
                    // Ignore Room sync failures
                }
            }
        }
    }

    /**
     * Unblocks a previously blocked suggestion.
     */
    fun unblockSuggestion(word: String) {
        val clean = word.lowercase(java.util.Locale.ROOT).trim().trim { !it.isLetterOrDigit() && it != '\'' }
        if (clean.isNotEmpty()) {
            blockedSuggestions.remove(clean)
            saveUserDictionary()
            scope.launch {
                try {
                    UserDictionaryRepository.getInstance(context).unblockSuggestion(clean)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Checks if a word is blocked from suggestions.
     */
    fun isBlocked(word: String): Boolean {
        val clean = word.lowercase(java.util.Locale.ROOT).trim().trim { !it.isLetterOrDigit() && it != '\'' }
        return clean in blockedSuggestions || (settings.profanityFilterEnabled && isProfane(clean))
    }

    fun isProfane(word: String): Boolean {
        return profaneWords.contains(word.lowercase().trim())
    }

    /**
     * Get 3 word suggestions for the given raw typing prefix.
     * Incorporates next-word bigram prediction, keyboard-proximity-weighted Levenshtein spelling correction,
     * slang expansion, and emoji prediction.
     */
    fun getSuggestionsForPrefix(
        prefix: String,
        prevWord: String? = null,
        isUrlField: Boolean = false,
        isEmailField: Boolean = false,
        isSensitiveField: Boolean = false,
        prevWord2: String? = null,
        tapCoords: List<PointF>? = null,
        previousWords: List<String> = emptyList()
    ): List<String> {
        if (isSensitiveField) return emptyList()
        val normalizedPrefix = prefix.lowercase(java.util.Locale.ROOT).trim()

        val isMalayalam = settings.isMalayalamScriptMode
        if (isMalayalam && normalizedPrefix.isNotEmpty()) {
            val candidates = ManglishTransliterationEngine.getInstance(context).getTransliterationCandidates(prefix)
            if (candidates.isNotEmpty()) {
                return candidates.take(3)
            }
        }

        // Code or special token check: no auto-completion if code-like
        if (isCodeOrSpecialToken(normalizedPrefix)) {
            return listOf(prefix)
        }

        // Domain-aware helper
        if (isUrlField || normalizedPrefix.startsWith("http") || normalizedPrefix.startsWith("www.")) {
            val tlds = listOf(".com", ".org", ".net", ".io", ".edu", ".gov", ".co", ".app")
            if (normalizedPrefix.isEmpty()) {
                return listOf("www.", "https://", ".com")
            }

            val urlSuggestions = mutableListOf<String>()
            if (normalizedPrefix.startsWith(".") || normalizedPrefix.startsWith("http") || normalizedPrefix.startsWith("www")) {
                val matched = tlds.filter { it.startsWith(normalizedPrefix) || normalizedPrefix.contains(it) }
                urlSuggestions.addAll(matched)
            } else {
                val popularDomains = listOf("google.com", "youtube.com", "facebook.com", "instagram.com", "wikipedia.org", "github.com", "reddit.com", "amazon.com", "twitter.com")
                val matchedPopular = popularDomains.filter { it.startsWith(normalizedPrefix) }
                urlSuggestions.addAll(matchedPopular)

                if (urlSuggestions.isEmpty()) {
                    urlSuggestions.add("$normalizedPrefix.com")
                    urlSuggestions.add("$normalizedPrefix.org")
                    urlSuggestions.add("$normalizedPrefix.net")
                }
            }
            return urlSuggestions.distinct().take(3)
        }

        if (isEmailField) {
            val domains = listOf("@gmail.com", "@yahoo.com", "@outlook.com", "@hotmail.com")
            if (normalizedPrefix.isEmpty()) {
                return domains.take(3)
            }
            if (normalizedPrefix.contains("@")) {
                val matched = domains.filter { it.startsWith("@" + normalizedPrefix.substringAfter("@")) }
                if (matched.isNotEmpty()) return matched.take(3)
            }
            return listOf("$normalizedPrefix@gmail.com", "$normalizedPrefix@yahoo.com", "$normalizedPrefix@outlook.com")
        }

        val contextList = if (previousWords.isNotEmpty()) {
            previousWords
        } else {
            listOfNotNull(prevWord2, prevWord)
        }

        // Compatibility presentation adapter. All language/spelling/ranking decisions belong to CandidateRanker.
        val correction = if(prefix.isNotBlank()) correctionPipeline.rank(prefix,contextList,tapCoords) else null
        val words = if(prefix.isBlank()) correctionPipeline.nextWords(contextList,6) else {
            val completion = correctionPipeline.prefix(prefix,contextList,tapCoords).candidates.map { it.word }
            val spelling = correction?.candidates.orEmpty().map { it.word }
            if(correction?.tier != ConfidenceTier.LOW) spelling+completion else completion+spelling
        }.distinctBy(MultilingualLexicon::normalize)
        val center = correction?.takeIf { it.tier != ConfidenceTier.LOW }?.suggestion ?: words.firstOrNull().orEmpty()
        val left = if(prefix.isNotBlank() && !prefix.equals(center,true)) prefix else words.firstOrNull { !it.equals(center,true) }.orEmpty()
        val right = words.firstOrNull { !it.equals(center,true) && !it.equals(left,true) }.orEmpty()
        return listOf(left,center,right)
    }

    data class ResampledSwipe(
        val points: List<PointF>,
        val arcLength: Float,
        val corners: List<PointF>,
        val inflectionPoints: List<PointF> = emptyList()
    )

    private fun preprocessSwipePath(rawPath: List<PointF>): ResampledSwipe? {
        if (rawPath.size < 2) return null

        // 1. Deduplicate sequential points that are too close
        val deduped = ArrayList<PointF>(rawPath.size)
        deduped.add(rawPath.first())
        for (i in 1 until rawPath.size) {
            val curr = rawPath[i]
            if (distance(deduped.last(), curr) >= 0.004f) {
                deduped.add(curr)
            }
        }
        if (deduped.size < 2) return null

        // 2. Arc length computation & step intervals
        val arcLengths = FloatArray(deduped.size)
        var totalLength = 0f
        for (i in 1 until deduped.size) {
            totalLength += distance(deduped[i - 1], deduped[i])
            arcLengths[i] = totalLength
        }
        if (totalLength < 0.04f) return null // Too short to be a deliberate swipe

        // 3. Resample into exactly 32 points equidistant along arc length
        val sampleCount = 32
        val resampled = ArrayList<PointF>(sampleCount)
        resampled.add(deduped.first())
        val step = totalLength / (sampleCount - 1)
        var srcIdx = 0

        for (i in 1 until sampleCount - 1) {
            val targetDist = i * step
            while (srcIdx < deduped.size - 2 && arcLengths[srcIdx + 1] < targetDist) {
                srcIdx++
            }
            val segStart = arcLengths[srcIdx]
            val segEnd = arcLengths[srcIdx + 1]
            val segLen = segEnd - segStart
            val ratio = if (segLen > 0.00001f) ((targetDist - segStart) / segLen).coerceIn(0f, 1f) else 0f
            val p1 = deduped[srcIdx]
            val p2 = deduped[srcIdx + 1]
            resampled.add(PointF(p1.x + ratio * (p2.x - p1.x), p1.y + ratio * (p2.y - p1.y)))
        }
        resampled.add(deduped.last())

        // 4. Detect inflection and dwell pause points along the gesture
        // Natural curved swiping features directional bends (>=35 deg) and velocity deceleration dips
        val inflectionPoints = mutableListOf<PointF>()
        for (i in 2 until sampleCount - 2) {
            val pPrev = resampled[i - 2]
            val pCurr = resampled[i]
            val pNext = resampled[i + 2]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val mag1 = sqrt(v1x * v1x + v1y * v1y)
            val mag2 = sqrt(v2x * v2x + v2y * v2y)

            if (mag1 > 0.004f && mag2 > 0.004f) {
                val dot = (v1x * v2x + v1y * v2y) / (mag1 * mag2)
                // Turn of >= 35 degrees (dot <= 0.82f) indicates an intentional inflection at a letter
                if (dot <= 0.82f) {
                    if (inflectionPoints.none { distance(it, pCurr) < 0.09f }) {
                        inflectionPoints.add(pCurr)
                    }
                }
            }
        }

        // Also check raw points for pause/dwell clusters where finger lingered
        for (i in 1 until deduped.size - 1) {
            val p1 = deduped[i - 1]
            val p2 = deduped[i]
            val p3 = deduped[i + 1]
            val d1 = distance(p1, p2)
            val d2 = distance(p2, p3)
            // Low local displacement indicating a dwell pause
            if (d1 < 0.008f && d2 < 0.008f) {
                if (inflectionPoints.none { distance(it, p2) < 0.09f }) {
                    inflectionPoints.add(p2)
                }
            }
        }

        return ResampledSwipe(resampled, totalLength, inflectionPoints, inflectionPoints)
    }

    private fun resampleWordTrajectory(keyPositions: List<PointF>, count: Int = 32): List<PointF> {
        if (keyPositions.isEmpty()) return emptyList()
        if (keyPositions.size == 1) return List(count) { keyPositions[0] }
        val arcLengths = FloatArray(keyPositions.size)
        var total = 0f
        for (i in 1 until keyPositions.size) {
            total += distance(keyPositions[i - 1], keyPositions[i])
            arcLengths[i] = total
        }
        if (total <= 0.0001f) return List(count) { keyPositions[0] }

        val result = ArrayList<PointF>(count)
        result.add(keyPositions.first())
        val step = total / (count - 1)
        var seg = 0

        for (i in 1 until count - 1) {
            val target = i * step
            while (seg < keyPositions.size - 2 && arcLengths[seg + 1] < target) {
                seg++
            }
            val sStart = arcLengths[seg]
            val sEnd = arcLengths[seg + 1]
            val sLen = sEnd - sStart
            val r = if (sLen > 0.00001f) ((target - sStart) / sLen).coerceIn(0f, 1f) else 0f
            val p1 = keyPositions[seg]
            val p2 = keyPositions[seg + 1]
            result.add(PointF(p1.x + r * (p2.x - p1.x), p1.y + r * (p2.y - p1.y)))
        }
        result.add(keyPositions.last())
        return result
    }

    private fun calculateDtwDistance(p1: List<PointF>, p2: List<PointF>, band: Int = 10): Float {
        val n = p1.size
        val m = p2.size
        if (n == 0 || m == 0) return Float.MAX_VALUE
        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            val jStart = maxOf(1, i - band)
            val jEnd = minOf(m, i + band)
            val pt1 = p1[i - 1]
            for (j in jStart..jEnd) {
                val pt2 = p2[j - 1]
                val cost = distance(pt1, pt2)
                val minPrev = minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
                if (minPrev != Float.MAX_VALUE) {
                    dtw[i][j] = cost + minPrev
                }
            }
        }
        val finalDist = dtw[n][m]
        return if (finalDist == Float.MAX_VALUE) 2.0f else finalDist / maxOf(n, m)
    }

    private fun calculateSwipeMatchCost(
        word: String,
        userSwipe: ResampledSwipe
    ): Float {
        val cleanWord = word.lowercase().trim().filter { it.isLetter() }
        if (cleanWord.length < 2) return Float.MAX_VALUE

        // Collapse adjacent duplicates (e.g. "look" -> "lok", "good" -> "god", "hello" -> "helo")
        val skeleton = StringBuilder()
        for (c in cleanWord) {
            if (skeleton.isEmpty() || skeleton.last() != c) {
                skeleton.append(c)
            }
        }

        val keyPositions = skeleton.mapNotNull { keyCoordinates[it] }
        if (keyPositions.size < 2) return Float.MAX_VALUE

        val userPoints = userSwipe.points
        val numPoints = userPoints.size

        // 1. Start & End key proximity
        val dStart = distance(userPoints.first(), keyPositions.first())
        val dEnd = distance(userPoints.last(), keyPositions.last())
        if (dStart > 0.30f || dEnd > 0.30f) return Float.MAX_VALUE
        val startEndCost = (dStart * 2.2f) + (dEnd * 2.5f)

        // 2. Ideal word trajectory length
        var idealArcLength = 0f
        for (i in 1 until keyPositions.size) {
            idealArcLength += distance(keyPositions[i - 1], keyPositions[i])
        }
        idealArcLength = maxOf(0.04f, idealArcLength)

        // 3. Length ratio check
        val lengthRatio = userSwipe.arcLength / idealArcLength
        var lengthPenalty = 0f
        if (lengthRatio < 0.38f) {
            lengthPenalty = (0.38f - lengthRatio) * 2.0f
        } else if (lengthRatio > 3.0f) {
            lengthPenalty = (lengthRatio - 3.0f) * 0.3f
        }

        // 4. Ideal trajectory resampling
        val idealPoints = resampleWordTrajectory(keyPositions, numPoints)

        // 5. Continuous Dynamic Time Warping (DTW) shape alignment with generous search band
        val shapeDtw = calculateDtwDistance(userPoints, idealPoints, band = 12)

        // 6. Ordered key traversal & coverage (robust against natural curved swiping)
        var coveragePenalty = 0f
        var orderPenalty = 0f
        var keyBonus = 0f
        var prevBestIdx = 0

        for (pos in keyPositions) {
            var minD = Float.MAX_VALUE
            var bestIdx = 0
            for (k in 0 until numPoints) {
                val d = distance(userPoints[k], pos)
                if (d < minD) {
                    minD = d
                    bestIdx = k
                }
            }
            if (minD > 0.16f) {
                // Key was not closely traversed by the swipe trajectory
                coveragePenalty += (minD - 0.16f) * 2.4f
            } else if (minD < 0.08f) {
                // Key was traversed directly
                keyBonus += 0.04f
            }

            // Check temporal ordering: key should occur after previous key
            if (bestIdx < prevBestIdx - 2) {
                orderPenalty += ((prevBestIdx - bestIdx).toFloat() / numPoints.toFloat()) * 0.45f
            }
            prevBestIdx = bestIdx
        }
        val avgCoveragePenalty = coveragePenalty / keyPositions.size

        // 7. Inflection / Dwell Pause Alignment
        // Reward words when user's pauses or directional curves align with intermediate letters
        var inflectionScore = 0f
        if (userSwipe.inflectionPoints.isNotEmpty()) {
            for (inflection in userSwipe.inflectionPoints) {
                val minDistToKeys = keyPositions.minOf { distance(it, inflection) }
                if (minDistToKeys <= 0.12f) {
                    // Turn or deceleration pause closely matches an intended letter
                    inflectionScore -= 0.10f
                } else if (minDistToKeys > 0.24f) {
                    // Turn or pause far from any letter in this word
                    inflectionScore += 0.05f
                }
            }
        }

        // 8. Raw cost calculation
        val baseCost = (shapeDtw * 1.8f) + startEndCost + avgCoveragePenalty + orderPenalty + lengthPenalty
        val bonusMultiplier = (1.0f - minOf(0.20f, keyBonus)) * (1.0f - minOf(0.15f, maxOf(0f, -inflectionScore)))
        val rawCost = baseCost * bonusMultiplier

        return maxOf(0.001f,rawCost)
    }

    /**
     * Decodes a list of normalized touch points (0.0 to 1.0) into the most likely matching words
     * using continuous trajectory resampling, true DTW shape comparison, sequential key verification,
     * inflection & dwell pause analysis, and language model context priors.
     */
    fun decodeSwipePath(path: List<PointF>, prevWord: String? = null, previousWords: List<String> = emptyList(), learningAllowed: Boolean = true): List<String> {
        if (path.size < 2) return emptyList()

        val userSwipe = preprocessSwipePath(path) ?: return emptyList()
        val pStart = userSwipe.points.first()
        val pEnd = userSwipe.points.last()

        // 1. Identify candidate start & end keys from keyboard grid with soft proximity
        val startCandidates = keyCoordinates.entries
            .map { it.key to distance(pStart, it.value) }
            .filter { it.second <= 0.26f }
            .sortedBy { it.second }
            .map { it.first }
            .take(5)
            .ifEmpty {
                keyCoordinates.entries
                    .map { it.key to distance(pStart, it.value) }
                    .sortedBy { it.second }
                    .map { it.first }
                    .take(2)
            }

        val endCandidates = keyCoordinates.entries
            .map { it.key to distance(pEnd, it.value) }
            .filter { it.second <= 0.26f }
            .sortedBy { it.second }
            .map { it.first }
            .take(5)
            .ifEmpty {
                keyCoordinates.entries
                    .map { it.key to distance(pEnd, it.value) }
                    .sortedBy { it.second }
                    .map { it.first }
                    .take(2)
            }

        // 2. Bucketed lookup of candidate words from gesture index
        val candidateMap = LinkedHashMap<String, Int>()
        synchronized(swipeWordIndex) {
            for (s in startCandidates) {
                for (e in endCandidates) {
                    val bucket = swipeWordIndex[Pair(s, e)] ?: continue
                    for ((word,frequency) in bucket) {
                        val existing = candidateMap[word] ?: 0
                        if (frequency > existing) {
                            candidateMap[word] = frequency
                        }
                    }
                }
            }
        }

        // 3. Candidate expansion: always include common high-frequency words starting near pStart
        val maxWordLen = maxOf(6, (userSwipe.arcLength * 16).toInt() + 2)
        commonWords.forEach { item ->
            val clean = item.word.lowercase().trim()
            if (clean.length in 2..maxWordLen && clean.first() in startCandidates) {
                if (!candidateMap.containsKey(item.word)) {
                    candidateMap[item.word] = item.frequency
                }
            }
        }

        // Also check personalized learned words
        synchronized(candidateLearnFrequency) {
            candidateLearnFrequency.forEach { (word, freq) ->
                val clean = word.lowercase().trim()
                if (clean.length in 2..maxWordLen && clean.first() in startCandidates) {
                    if (!candidateMap.containsKey(word)) {
                        candidateMap[word] = freq
                    }
                }
            }
        }

        // 4. Query strictly confident ML template predictions if available
        val mlSwipePredictions = if(learningAllowed && settings.personalizedLearningEnabled) mlPredictor.predictFromSwipePatterns(path,0.03f) else emptyList()

        // 5. Score all candidate words
        val scoredList = mutableListOf<SwipeProposal>()
        // Bounded geometric beam, independent of word/context priors. Expensive
        // trajectory matching only runs for plausible start/end/length proposals.
        val probes=userSwipe.points.filterIndexed { index,_ -> index%4==0 }
        val beam=candidateMap.keys.asSequence().filter { it.length in 2..maxWordLen && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' } }
            .sortedBy { word ->
                val start=keyCoordinates[word.first().lowercaseChar()]; val end=keyCoordinates[word.last().lowercaseChar()]
                if(start==null || end==null) Float.MAX_VALUE else {
                    var coverage=0f; var lastIndex=0
                    for(char in word) {
                        val key=keyCoordinates[char.lowercaseChar()] ?: continue
                        var best=Float.MAX_VALUE; var bestIndex=0
                        probes.forEachIndexed { index,point -> val d=distance(key,point); if(d<best) { best=d; bestIndex=index } }
                        coverage+=best+if(bestIndex<lastIndex-1) .15f else 0f
                        lastIndex=bestIndex
                    }
                    distance(pStart,start)*2.2f+distance(pEnd,end)*2.5f+coverage/word.length*2f
                }
            }.take(192).toList()
        for (word in beam) {
            val clean = word.lowercase().trim()
            if (clean.length < 2) continue

            val mlMatch = mlSwipePredictions.firstOrNull { it.first.equals(clean, ignoreCase = true) }
            val mlSim = mlMatch?.second ?: 0f

            val score = calculateSwipeMatchCost(
                word = word,
                userSwipe = userSwipe
            )

            if (score < 10.0f) {
                scoredList.add(SwipeProposal(word,score,mlSim))
            }
        }

        val context=previousWords.ifEmpty { listOfNotNull(prevWord) }
        return correctionPipeline.rankSwipe(scoredList.sortedBy { it.geometry }.take(40),context,learningAllowed).map { it.word }
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Helper to verify if correctionCandidate is a valid spelling correction for typedWord.
     */
    fun isSpellingCorrection(
        typedWord: String,
        correctionCandidate: String,
        prevWord: String? = null,
        prevWord2: String? = null,
        tapCoords: List<PointF>? = null
    ): Boolean {
        val w1 = typedWord.lowercase().trim()
        val w2 = correctionCandidate.lowercase().trim()
        if (w1.isEmpty() || w2.isEmpty()) return false
        if (w1 == w2) return false
        
        if (isCodeOrSpecialToken(w1) || isCodeOrSpecialToken(w2)) return false
        if (slangExpansions.containsKey(w1)) return false
        if (personalBlocklist.contains(w1)) return false
        if (suppressedCorrections[w1]?.contains(w2) == true) return false
        if (isWordInDictionary(w1) || recentlyAcceptedWords.contains(w1)) return false

        if (w2.contains(" ")) {
            val parts = w2.split(" ")
            val allValid = parts.all { it == "a" || it == "i" || isWordInDictionary(it) }
            if (!allValid) return false
        } else {
            if (!isWordInDictionary(w2)) return false
        }
        
        val confidence = calculateCorrectionConfidence(w1, w2, prevWord, prevWord2, tapCoords)
        return confidence >= SUGGESTION_THRESHOLD
    }

    /**
     * Compute a unified confidence score combining 5 core signals:
     * score = w1*edit_dist + w2*tap_geometry + w3*lm_prob + w4*user_freq + w5*unigram_freq
     */
    fun calculateCorrectionConfidence(
        typedWord: String,
        candidate: String,
        prevWord: String? = null,
        prevWord2: String? = null,
        tapCoords: List<PointF>? = null
    ): Float {
        val result = correctionPipeline.rank(typedWord, listOfNotNull(prevWord2, prevWord), tapCoords)
        if (result.protected) return 0f
        return result.candidates.firstOrNull { it.word.equals(candidate, true) }?.posterior ?: 0f
    }

    companion object {
        const val SILENT_CORRECT_THRESHOLD = 0.40f // Autocorrect threshold
        const val SUGGESTION_THRESHOLD = 0.30f     // Suggestion candidate threshold

        @Volatile
        private var instance: DictionaryManager? = null

        fun getInstance(context: Context): DictionaryManager {
            val app = context.applicationContext
            return instance?.takeIf { it.appContext === app } ?: synchronized(this) {
                instance?.takeIf { it.appContext === app } ?: DictionaryManager(app).also { instance = it }
            }
        }

        val SUPPLEMENTAL_WORDS: Set<String> by lazy {
            val vocabText = """
                meeting meetings tomorrow yesterday tonight morning evening afternoon please thanks because definitely separate receive happened grammar keyboard beautiful really together friend friends family schedule message messages problem problems question questions answer answers system program computer phone email office business service product client project report review important perfect truly probably maybe without through against between before after during while until believe understand remember forget decide consider require suggest include provide continue expect create build offer describe explain appreciate welcome sorry excuse minute minutes hour hours second seconds today week weeks month months year years doctor hospital station airport hotel restaurant dinner lunch breakfast coffee water food music video photo camera screen battery color number street address city country world place house room door window car train bus flight ticket money card bank price cost market store shop game play team group class school student teacher learn study read write listen speak talk walk run drive travel visit stay leave arrive start stop finish begin end open close send receive call wait help need want like love feel hope wish think know see hear watch look find lose give take bring buy sell pay spend save keep hold put set show tell ask answer try work play live move change grow happen allow cause lead follow stand sit fall rise cut build kill die remain suggest require report decide pull push break wear choose agree check point support cover join catch draw fight throw fill drop plan enjoy explain touch train serve manage pass sell agree discuss prepare expect protect win lose reach teach walk wonder notice smile laugh cry shout sleep dream wake drink eat cook clean wash drive ride fly swim burn freeze hurt cure heal shine glow blow shake hide seek climb jump hang ring sing dance count measure weigh cost fit suit match seem appear sound taste smell belong consist contain depend differ exist matter mean mind own owe possess prefer realize recognize remember remind resemble satisfy suppose surprise trust understand value wish doubt dislike hate fear envy pity admire respect appreciate forgive blame praise thank congratulate welcome greet introduce invite refuse accept reject agree disagree argue quarrel warn threaten promise swear bet advise recommend urge demand insist request beg order command forbid prevent avoid escape rescue save help assist aid serve treat cure heal care nurse protect defend guard shelter shield secure insure guarantee assure confirm prove test check verify examine inspect investigate explore search seek hunt track trace discover invent create produce make build construct erect form shape mold design plan draft compose write author paint draw sketch carve sculpt cast print publish record film tape photograph snapshot shoot capture display exhibit present introduce unveil reveal disclose expose show demonstrate illustrate manifest express voice utter pronounce articulate enunciate state declare announce proclaim broadcast circulate distribute disseminate spread scatter disperse diffuse transmit convey carry transport transfer shift switch convert transform transmute change alter modify adapt adjust regulate tune calibrate correct rectify remedy repair mend fix patch restore renew revive refresh recreate regenerate reproduce replicate duplicate copy imitate mimic emulate simulate model pattern follow obey comply conform adhere stick cling cleave bind tie knot fasten secure attach join connect link couple unite combine blend merge fuse meld mix mingle intermix compound synthesize integrate incorporate embody include contain hold accommodate house shelter harbor lodge board quarter station post place put set situate locate position pose stand install establish found institute initiate inaugurate launch start begin commence originate arise spring stem derive proceed issue emanate flow pour stream spurt gush rush surge swell heave billow toss pitch roll rock sway swing oscillate vibrate tremble quiver shiver shudder quake totter wobble stagger reel lurch stumble trip slip slide glide skate skim drift float sail cruise voyage journey travel tour trek hike march stride pace step tread walk saunter stroll amble wander roam ramble rove straggle meander drift stray deviate diverge swerve veer turn pivot revolve rotate spin whirl twirl swirl eddy vortex circle orbit loop spiral coil curl wind twist twine weave knit braid plait interlace entangle tangle snarl knot unravel untangle unwind unwrap unfold open spread expand extend stretch reach prolong lengthen elongate broaden widen deepen heighten elevate raise lift hoist heave boost enhance heighten intensify magnify amplify increase augment supplement add annex append attach subjoin tag tack affix fasten fix clamp rivet weld solder cement glue paste stick seal lock bolt bar latch clasp buckle button snap hook link yoke harness couple chain tie bind cord rope wire strap gird wrap bandage swathe muffle cloak mantle robe drape shroud veil screen shield protect guard defend preserve conserve save rescue deliver liberate free release exempt acquit clear absolve pardon forgive condone overlook excuse justify warrant vindicate validate verify confirm corroborate substantiate authenticate certify endorse approve sanction authorize commission empower enable allow permit admit concede grant yield surrender relinquish abandon forsake desert quit leave depart vacate evacuate withdraw retire retreat recede ebbed subside wane dwindle decrease diminish lessen reduce contract shrink constrict narrow taper attenuate slender thin pare trim clip prune crop dock curtail shorten abbreviate abridge condense compress compact squeeze pinch press crush smash shatter fracture break crack snap burst explode rupture tear rip rend slit split cleave sever divide separate part sunder detach disconnect disjoin disunite isolate segregate quarantine insulate seclude sequester withdraw retire hide conceal screen shield mask disguise cloak veil shroud obscure eclipse shadow dim darken cloud fog mist haze blur fuzz smear smudge blot stain taint tarnish soil dirty pollute contaminate infect poison corrupt deprave spoil ruin wreck damage harm hurt injure wound bruise maim cripple disable incapacitate paralyze prostrate overcome overpower overwhelm subdue conquer vanquish defeat beat rout crush trounce thrash whip flog cane strike hit smite knock tap rap slap cuff smack thump thud bang bump crash clash collide bump jar jolt shake jiggle rattle clatter clank chink jingle tinkle chime toll peal ring buzz hum drone murmur whisper rustle sigh gasp pant puff blow breathe inhale exhale snort sniff snuffle sneeze cough hiccup belch burp gag choke stifle smother suffocate drown submerge sink founder plunge dive dip duck souse douse soak steep drench saturate wet moisten dampen humidify water irrigate spray sprinkle shower spatter splash splatter slosh swash spill slop overflow brim well bubble boil simmer seethe fume steam vaporize evaporate distill filter strain sift screen winnow purify cleanse scour scrub wipe mop sponge swab brush sweep vacuum dust polish shine buff burnish rub chafe fret gall scrape grate rasp file sand hone sharpen whet grind mill crush pound pulverize powder mash puree pulp paste knead mold work manipulate handle finger thumb feel touch caress stroke pet pat fondle cuddle hug embrace clasp grasp grip clutch snatch grab seize catch trap ensnare entangle capture arrest apprehend take hold contain keep retain withhold reserve store stash cache hoard accumulate amass gather collect assemble muster marshal mobilize rally convene convoke summon cite subpoena call invite bid ask solicit appeal plead petition sue beg implore beseech entreat supplicate crave pray importune pester badger nag hound harass harry molest plague torment torture rack afflict distress trouble worry fret grieve mourn lament bemoan bewail weep cry sob wail howl screech shriek scream yell shout bawl bellow roar clamor cheer applaud acclaim hail salute greet welcome acknowledge recognize admit own avow confess concede grant allow permit consent agree assent concur cooperate collaborate conspire connive plot scheme intrigue collude participate partake share divide portion ration allot allocate assign apportion distribute dispense mete administer provide supply furnish equip arm fit rig outfit provision cater feed nourish sustain maintain support back uphold champion advocate promote foster nurture cherish harbor cultivate tend mind watch guard patrol police protect defend safeguard shield screen shelter harbor haven refuge sanctuary asylum retreat resort haunt frequent visit attend patronize support foster promote encourage inspire hearten embolden cheer comfort console solace soothe calm tranquilize pacify appease placate mollify propitiate conciliate reconcile harmonize coordinate orchestrate organize arrange order array marshal dispose systematize codify classify categorize sort sift file index catalog list tabulate record enter log register enroll matriculate sign subscribe endorse countersign initial mark stamp imprint impress engrave etch inscribe carve chisel sculpt mold cast forge fabricate manufacture produce generate yield bear breed propagate multiply increase reproduce procreate beget father mother sire originate commence begin start dawn open launch initiate inaugurate embark undertake venture attempt try strive struggle contend vie compete contest battle fight war clash combat skirmish tussle scuffle brawl wrestle grapple box duel joust encounter meet confront face brave dare defy challenge provoke taunt mock deride ridicule scoff jeer sneer gibe flout disdain scorn despise abhor detest loathe abominate execrate curse damn blast blame censure condemn denounce reproach rebuke reprimand reprove admonish scold chide berate upbraid rate lecture harangue castigate chastise punish penalize discipline correct rectify remedy redress atone expiate compensate recompense indemnify reimburse repay refund remunerate reward settle liquidate discharge acquit pay defray satisfy meet honor fulfill perform execute discharge accomplish achieve effect attain realize consummate complete finish conclude terminate close wind culminate climax cap crown top surpass excel exceed transcend outdo outstrip outperform eclipse overshadow dwarf beat best worst defeat master conquer vanquish subdue tame domesticate curb check bridle rein harness control command govern rule reign dominate predominate prevail triumph win succeed prosper thrive flourish bloom blossom flower mature ripen age mellow season harden temper toughen anneal strengthen fortify reinforce brace prop buttress shore support sustain bear carry shoulder endure abide tolerate brook suffer stand withstand resist oppose counter parry repel repulse rebuff ward fend stave dodge evade elude avoid shun eschew steer skirt bypass sidestep circumvent outwit baffle foil thwart frustrate confound disconcert discomfit discompose disquiet agitate disturb perturb fluster ruffle upset unnerve intimidate daunt dismay terrify frighten scare alarm startle shock appall horrify disgust revolt sicken nauseate offend outrage insult affront slight snub humiliate mortify chagrin shame abash disgrace dishonor degrade debase demean humble abase lower reduce demote relegate downgrade depose dethrone oust expel eject banish exile deport transport extradite evict dispossess expropriate confiscate seize impound sequester distrain attach levy exact extort wrest wring wrench extract elicit evoke derive deduce infer gather conclude judge deem reckon estimate gauge appraise assess rate evaluate value price cost figure compute calculate reckon tally count enumerate number tabulate total sum aggregate add subtract deduct multiply divide balance reconcile audit verify check inspect scrutinize scan skim peruse read study pore learn memorize master grasp comprehend understand fathom penetrate pierce discern perceive see behold view survey inspect observe notice note mark remark heed mind regard consider ponder meditate ruminate contemplate reflect muse deliberate cogitate think reason rationalize analyze dissect parse resolve decompose dissolve melt thaw fuse liquefy solidify freeze congeal curdle clot coagulate set harden stiffen petrify calcify fossilize ossify dry parch sear scorch burn char singe toast bake roast broil grill fry saute braise stew boil simmer poach coddle scald steam blanch steep infuse brew distill ferment bubble effervesce fizz sparkle glitter glisten shimmer gleam glint flash flare blaze flame glow burn kindle ignite light illumine illuminate brighten clarify elucidate explain interpret construe translate render paraphrase rephrase rewrite recast remodel reform regenerate reorganize reconstruct rebuild restore revive resuscitate revitalize rejuvenate renew renovate refurbish redecorate recondition overhaul service tune adjust regulate control govern direct manage conduct handle administer execute perform discharge dispatch expedite hasten speed accelerate quicken hurry rush dash race sprint run scurry scamper scuttle dart shoot fly glide sail soar hover flutter flit flap wave brandish flourish wield ply employ use utilize harness exploit operate function act work serve avail benefit profit gain win earn acquire obtain procure secure get derive draw reap harvest gather collect glean amass heap pile stack load burden encumber saddle tax charge bill invoice debit credit trust rely depend count bank lean rest repose sleep slumber snooze nap doze drowse lounge loaf loiter linger tarry delay stall hesitate falter waver vacillate fluctuate oscillate swing sway rock reel totter wobble shiver quiver tremble quake shake jar jolt vibrate pulsate throb beat palpitate flutter pant gasp heave swell distend inflate expand stretch widen broaden enlarge amplify magnify maximize optimize perfect refine polish hone elevate exalt dignify ennoble honor glorify praise extol laud eulogize commend applaud cheer acclaim salute toast celebrate commemorate observe keep solemnize bless sanctify consecrate hallow dedicate devote commit consign entrust confide delegate assign charge commission accredit authorize empower license permit allow sanction warrant guarantee assure vouch attest testify swear affirm assert declare pronounce proclaim broadcast publish trumpet herald announce herald signal sign indicate denote signify imply hint suggest insinuate intimate connote mean intend aim propose plan design scheme contrive devise frame formulate prepare ready prime equip arm fortify gird brace steel nerve strengthen invigorate energize stimulate activate spark kindle prompt induce persuade convince prevail sway influence bias prejudice warp skew distort twist deform mangle mutilate mar spoil ruin wreck shatter destroy annihilate demolish raze level flatten crush quash quell suppress stifle smother extinguish quench douse snuff stamp trample tread step stride pace march parade advance proceed progress forge head lead guide steer pilot navigate helm conduct usher escort accompany chaperon convoy attend wait serve minister assist help succor relieve ease alleviate mitigate palliate allay assuage soothe calm lull pacify appease placate satisfy content gratify please delight gladden cheer comfort solace warm thrill exhilarate elate electrify enchant charm captivate fascinate beguile allure attract draw magnetize lure entice tempt seduce cajole coax wheedle inveigle flatter blandish compliment praise extol fawn toady truckle kowtow bow curtsy genuflect kneel stoop bend crouch cower shrink flinch wince quail recoil cringe crawl creep grovel slither glide slide slip skid coast drift stray wander roam ramble amble saunter stroll promenade walk tramp trudge plod lumber clump stamp trot canter gallop bound leap jump spring skip hop vault hurdle clear pass surmount scale climb ascend mount rise soar tower loom hover hang dangle suspend swing sway oscillate wave undulate ripple flicker flutter quiver shiver tremble quake shake jolt jerk twitch spasm convulse writhe squirm wiggle wriggle twist contort distort deform warp bend curve arch bow crook hook angle deflect divert turn wheel pivot swing swivel spin rotate revolve gyrate whirl roll tumble somersault cartwheel flip invert reverse transpose swap switch exchange interchange substitute replace supplant supersede displace usurp oust evict expel banish exile ostracize boycott shun spurn reject repudiate renounce abjure disown disclaim disavow retract recant revoke rescind repeal annul nullify void invalidate cancel countermand veto quash override overrule disallow bar block obstruct hinder impede hamper fetter shackle handcuff chain tie bind truss rope lash strap cinch girth belt fasten secure lock latch bolt bar seal stop plug cork bung choke clog congest block dam choke jam wedge cram pack stuff crowd throng squeeze compress compact press iron smooth flatten level even plane shave trim pare peel skin strip bare denude uncover expose reveal disclose unveil unmask divulge leak tell whisper breathe impart communicate convey transmit broadcast spread publish circulate disseminate proclaim announce declare state express voice utter sound vocalize articulate pronounce enunciate say speak talk chat converse discourse lecture address preach sermonize teach instruct educate train drill coach tutor guide lead direct command order decree ordain dictate prescribe require demand exact enforce compel coerce force drive impel push propel thrust plunge shove prod poke nudge jab punch strike hit smite pound batter buffet pummel thrash beat whip lash stripe scourge flagellate flog cane club bludgeon knock tap rap slap pat dab touch contact brush graze kiss caress fondle pet stroke massage knead rub chafe fret gall irritate inflame exasperate provoke anger enrage infuriate madden incense rile irk vex annoy bother trouble disturb disquiet worry plague harass harry torment pester badger hound dog bait tease taunt mock deride chaff banter kid josh rib needle poke prod goad spur egg prick sting bite nip pinch squeeze tweak twist wrench yank pull haul drag tow tug jerk draw attract magnetize lure entice allure charm captivate enchant bewitch fascinate hypnotize mesmerize spellbind transfix rivet grip hold retain keep save conserve hoard stash store cache hide bury inter cover shroud screen shield protect guard defend preserve cherish nurture harbor shelter house quarter lodge board accommodate host entertain treat regale feast feed nourish nurse rear raise bring foster train discipline school educate cultivate develop grow produce generate create make form shape mold fashion model carve sculpt cast forge construct erect build fabricate manufacture assemble compile compose draft write author pen indite inscribe record enter log chronicle narrate recount relate tell repeat reiterate restate rephrase summarize outline sketch trace draw delineate depict portray represent illustrate symbolize typify personify embody exemplify demonstrate prove verify corroborate substantiate confirm ratify sanction endorse approve applaud commend praise laud celebrate acclaim hail honor respect revere venerate worship adore idolize deify exalt elevate promote advance further forward expedite hasten accelerate speed quicken hurry rush dash race fly glide drift float sail cruise wander roam ramble stroll walk tramp march stride step pace tread plod trudge climb mount ascend scale conquer master overcome surmount vanquish defeat beat triumph win succeed thrive flourish prosper bloom shine glow sparkle glitter radiate beam flash gleam blaze burn kindle ignite fire light illuminate brighten cheer comfort solace warm thrill inspire uplift elevate enliven animate energize invigorate revive refresh renew restore heal cure remedy repair mend fix perfect complete finish conclude settle resolve decide determine fix establish secure ground root plant sow seed scatter disperse broadcast spread expand extend reach stretch widen broaden enlarge grow develop mature ripen flourish prosper succeed
            """.trimIndent()
            vocabText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        }
    }

    /**
     * Get frequency for a word from corpus or user dictionary in O(1) time.
     */
    fun getWordFrequency(word: String): Int {
        val w = word.lowercase(java.util.Locale.ROOT).trim()
        if (w.isEmpty()) return 0
        val base = maxOf(corpus.frequency(w), commonWordsFreqMap[w] ?: 0)
        if (synchronized(userWords) { userWords.contains(w) }) return maxOf(120, base)
        if (base > 0) return base
        if (commonWordsSet.contains(w)) return 40
        return 0
    }

    private data class SpellingLookup(val word: String, val distance: Float, val limit: Int, val vocabulary: Int)
    // Geometry/context/ML scoring stays fresh. Reuse only lexical proposals across changing tap samples.
    private val spellingCache = object : LinkedHashMap<SpellingLookup,List<SymSpellCorrectionEngine.SuggestionItem>>(256,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpellingLookup,List<SymSpellCorrectionEngine.SuggestionItem>>?) = size > 256
    }
    fun findDictionaryCorrections(word: String, maxDistance: Float = 2f, maxResults: Int = 16): List<SymSpellCorrectionEngine.SuggestionItem> {
        val key=SpellingLookup(word.lowercase(java.util.Locale.ROOT),maxDistance,maxResults,vocabularyVersion+corpus.canonical.size)
        val proposals=synchronized(spellingCache) { spellingCache[key] } ?: run {
            val result=(gboardEngine.symSpellEngine.lookup(word,maxDistance,maxResults)+corpus.corrections(word,maxDistance,maxResults))
                .distinctBy { it.term }.sortedWith(compareBy<SymSpellCorrectionEngine.SuggestionItem> { it.distance }
                    .thenByDescending { getWordFrequency(it.term) }).toList()
            synchronized(spellingCache) { spellingCache[key]=result }
            result
        }
        return proposals.filter { isWordInDictionary(it.term) && !isBlocked(it.term) }.take(maxResults)
    }

    /**
     * Check if a word exists in the app's dictionary or libraries (case-insensitive) in O(1) time.
     */
    fun isWordInDictionary(word: String): Boolean {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return false
        if (commonWordsSet.contains(w) || userWords.contains(w) || slangExpansions.containsKey(w) || recentlyAcceptedWords.contains(w)) return true
        if (w in corpus.canonical && !gboardEngine.isKnownTypo(w)) return true

        return false
    }
}
