package com.example

import android.content.Context
import android.graphics.PointF
import android.provider.UserDictionary
import android.view.textservice.TextServicesManager
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DictionaryManager(private val context: Context) {

    val mlPredictor = PatternLearningPredictor.getInstance(context)
    val nGramModel = NGramLanguageModel()
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

    fun learnTapPattern(char: Char, tapX: Float, tapY: Float) {
        val charKey = char.lowercaseChar()
        val p = keyCoordinates[charKey]
        if (p != null) {
            mlPredictor.learnTapPattern(charKey, tapX, tapY, p.x, p.y)
        }
    }

    fun findWordsWithPrefix(prefix: String, maxResults: Int = 3): List<String> {
        return trie.searchPrefix(prefix, maxResults).map { it.first }
    }

    /**
     * Find nearest dictionary candidates for user input using Levenshtein distance algorithm.
     */
    fun findLevenshteinCorrections(
        input: String,
        maxDistance: Int = 2,
        maxResults: Int = 5
    ): List<LevenshteinAutoCorrector.LevenshteinMatch> {
        return LevenshteinAutoCorrector.findClosestWords(
            input = input,
            dictionary = commonWords,
            maxDistance = maxDistance,
            maxResults = maxResults
        )
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
        WordFrequency("there", 170), WordFrequency("their", 165),
        WordFrequency("what", 160), WordFrequency("so", 155), WordFrequency("up", 150),
        WordFrequency("out", 145), WordFrequency("if", 140), WordFrequency("about", 135),
        WordFrequency("who", 130), WordFrequency("get", 125), WordFrequency("which", 120),
        WordFrequency("go", 115), WordFrequency("me", 110), WordFrequency("when", 105),
        WordFrequency("make", 100), WordFrequency("can", 98), WordFrequency("like", 96),
        WordFrequency("time", 94), WordFrequency("no", 92), WordFrequency("just", 90),
        WordFrequency("him", 88), WordFrequency("know", 86), WordFrequency("take", 84),
        WordFrequency("people", 82), WordFrequency("into", 80), WordFrequency("year", 78),
        WordFrequency("your", 76), WordFrequency("good", 74), WordFrequency("some", 72),
        WordFrequency("could", 70), WordFrequency("them", 68), WordFrequency("see", 66),
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
        WordFrequency("us", 7),

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
    )

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
        "btw" to "by the way"
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

    // Dynamic User Dictionary, Personal Blocklist, Learned Bigrams, and Suppressed Corrections
    private val prefs = context.getSharedPreferences("typeright_dictionary", Context.MODE_PRIVATE)
    private val userWords = mutableSetOf<String>()
    private val personalBlocklist = mutableSetOf<String>()
    private val personalizedBigrams = mutableMapOf<String, MutableList<String>>()
    private val suppressedCorrections = mutableMapOf<String, MutableSet<String>>()
    private val recentlyAcceptedWords = LinkedHashSet<String>()

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

    fun suppressCorrection(originalWord: String, correctedWord: String) {
        val orig = originalWord.lowercase().trim()
        val corr = correctedWord.lowercase().trim()
        if (orig.isNotEmpty() && corr.isNotEmpty()) {
            synchronized(suppressedCorrections) {
                val set = suppressedCorrections.getOrPut(orig) { mutableSetOf() }
                set.add(corr)
            }
            addToBlocklist(orig)
        }
    }

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

    private val trie = TrieDictionary()
    val wordTrie = WordTrie()
    private val phoneticIndex = HashMap<String, MutableList<String>>()
    private val commonWordsSet = HashSet<String>(1500)
    private val commonWordsFreqMap = HashMap<String, Int>(1500)

    val tfLiteModel by lazy { TfLiteCorrectionModel.getInstance(context) }
    private val database = AppDatabase.getDatabase(context)
    private val learnedWordDao = database.learnedWordDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val textServicesManager: TextServicesManager? = try {
        context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
    } catch (e: Throwable) {
        null
    }

    init {
        // Build local Trie index, SymSpell index, Fast Hash Set & Phonetic index from common words
        commonWords.forEach {
            val lower = it.word.lowercase()
            commonWordsSet.add(lower)
            commonWordsFreqMap[lower] = it.frequency
            trie.insert(it.word, it.frequency)
            wordTrie.insert(it.word, it.frequency)
            gboardEngine.symSpellEngine.insertWord(it.word, it.frequency)
            val pKey = computePhoneticKey(it.word)
            if (pKey.isNotEmpty()) {
                phoneticIndex.getOrPut(pKey) { mutableListOf() }.add(it.word)
            }
        }
        loadUserDictionary()
        loadWordsFromDatabase()
        loadSystemUserDictionary()
    }

    /**
     * High-speed phonetic representation algorithm (combining Soundex & Metaphone rules)
     * for resilient sound-alike spelling error detection.
     */
    fun computePhoneticKey(word: String): String {
        val clean = word.lowercase().filter { it.isLetter() }
        if (clean.isEmpty()) return ""

        var s = clean
        s = s.replace("ph", "f")
        s = s.replace("gh", "f")
        s = s.replace("dg", "j")
        s = s.replace("ck", "k")
        s = s.replace("kn", "n")
        s = s.replace("wr", "r")
        s = s.replace("wh", "w")
        s = s.replace("tion", "shn")
        s = s.replace("sion", "shn")
        s = s.replace("ce", "se")
        s = s.replace("ci", "si")
        s = s.replace("cy", "sy")
        s = s.replace("c", "k")
        s = s.replace("q", "k")
        s = s.replace("x", "ks")
        s = s.replace("z", "s")

        val collapsed = StringBuilder()
        for (ch in s) {
            if (collapsed.isEmpty() || collapsed.last() != ch) {
                collapsed.append(ch)
            }
        }

        if (collapsed.isEmpty()) return ""
        val firstChar = collapsed[0]
        val rest = collapsed.substring(1).filter { it !in "aeiouy" }
        return "$firstChar$rest"
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
                    trie.insert(clean, freq)
                    wordTrie.insert(clean, freq)
                    gboardEngine.symSpellEngine.insertWord(clean, freq)
                    val pKey = computePhoneticKey(clean)
                    if (pKey.isNotEmpty()) {
                        phoneticIndex.getOrPut(pKey) { mutableListOf() }.add(clean)
                    }
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
                mlPredictor.learnBigram(p, n)
                nGramModel.addBigram(p, n, 2)
            }
        }

        saveUserDictionary()
    }

    private fun loadWordsFromDatabase() {
        scope.launch {
            try {
                val dbWords = learnedWordDao.getAllWords()
                synchronized(userWords) {
                    dbWords.forEach {
                        userWords.add(it.word)
                        trie.insert(it.word, maxOf(30, it.frequency))
                        wordTrie.insert(it.word, maxOf(30, it.frequency))
                        gboardEngine.symSpellEngine.insertWord(it.word, maxOf(30, it.frequency))
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun loadUserDictionary() {
        val loaded = prefs.getStringSet("user_words", emptySet()) ?: emptySet()
        userWords.addAll(loaded)
        loaded.forEach {
            trie.insert(it, 35)
            wordTrie.insert(it, 35)
            gboardEngine.symSpellEngine.insertWord(it, 35)
        }
        personalBlocklist.addAll(prefs.getStringSet("personal_blocklist", emptySet()) ?: emptySet())
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
            val bigramStr = personalizedBigrams.entries.joinToString(";") { "${it.key}:${it.value.joinToString(",")}" }
            putString("personalized_bigrams", bigramStr)
            apply()
        }
    }

    /**
     * Checks whether a given word is recognized by the local dictionary, user words, or SymSpell index.
     */
    fun isValidOrKnownWord(word: String): Boolean {
        val clean = word.lowercase().trim().trim { !it.isLetterOrDigit() && it != '\'' }
        if (clean.isEmpty()) return true
        if (clean.length == 1 && (clean == "a" || clean == "i")) return true
        if (clean.all { it.isDigit() }) return true
        if (commonWordsSet.contains(clean)) return true
        if (synchronized(userWords) { userWords.contains(clean) }) return true
        if (gboardEngine.symSpellEngine.hasWord(clean)) return true
        return false
    }

    fun learnWord(word: String) {
        val clean = word.lowercase().trim()
        if (clean.isEmpty() || clean.length < 2 || isProfane(clean)) return
        val isBaseWord = commonWords.any { it.word.lowercase() == clean }
        if (!isBaseWord) {
            val alreadyLearned = synchronized(userWords) {
                if (!userWords.contains(clean)) {
                    userWords.add(clean)
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

    fun learnBigram(prev: String, current: String) {
        val p = prev.lowercase().trim()
        val c = current.lowercase().trim()
        if (p.isEmpty() || c.isEmpty() || isProfane(c)) return
        
        // Train the machine learning Markov model & N-gram Language Model
        mlPredictor.learnBigram(p, c)
        nGramModel.addBigram(p, c, 1)

        val list = personalizedBigrams.getOrPut(p) { mutableListOf() }
        if (!list.contains(c)) {
            list.add(0, c)
            if (list.size > 8) {
                list.removeAt(list.size - 1)
            }
            saveUserDictionary()
        }
    }

    fun learnTrigram(prev2: String, prev1: String, current: String) {
        val p2 = prev2.lowercase().trim()
        val p1 = prev1.lowercase().trim()
        val c = current.lowercase().trim()
        if (p2.isEmpty() || p1.isEmpty() || c.isEmpty() || isProfane(c)) return

        // Train the machine learning Markov model & N-gram Language Model
        mlPredictor.learnTrigram(p2, p1, c)
        nGramModel.addTrigram(p2, p1, c, 1)

        val key = "$p2 $p1"
        val list = personalizedBigrams.getOrPut(key) { mutableListOf() }
        if (!list.contains(c)) {
            list.add(0, c)
            if (list.size > 8) {
                list.removeAt(list.size - 1)
            }
            saveUserDictionary()
        }
    }

    fun learnQuadgram(prev3: String, prev2: String, prev1: String, current: String) {
        val p3 = prev3.lowercase().trim()
        val p2 = prev2.lowercase().trim()
        val p1 = prev1.lowercase().trim()
        val c = current.lowercase().trim()
        if (p3.isEmpty() || p2.isEmpty() || p1.isEmpty() || c.isEmpty() || isProfane(c)) return

        nGramModel.addQuadgram(p3, p2, p1, c, 1)
    }

    fun addToBlocklist(word: String) {
        val clean = word.lowercase().trim()
        if (clean.isNotEmpty()) {
            personalBlocklist.add(clean)
            saveUserDictionary()
        }
    }

    fun isProfane(word: String): Boolean {
        return profaneWords.contains(word.lowercase().trim())
    }

    fun expandSlang(word: String): String? {
        return slangExpansions[word.lowercase().trim()]
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
        val normalizedPrefix = prefix.lowercase().trim()

        if (isSensitiveField) {
            if (normalizedPrefix.isEmpty()) {
                return listOf("Password123!", "Pass@2026", "SecureKey#1")
            }
            val passwordSuggestions = listOf("Password", "Passcode", "Passkey", "Secret123", "Admin2026", "Security")
            val matched = passwordSuggestions.filter { it.lowercase().startsWith(normalizedPrefix) }
            if (matched.isNotEmpty()) return matched.take(3)
            return listOf("${prefix}123!", "${prefix}@2026", "${prefix}#key")
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

        if (normalizedPrefix.isEmpty()) {
            // Context-aware phrase completions from local grammar predictor
            val phrasePredictions = localGrammarPredictor.predictPhraseCompletions(contextList, "", 3)

            // Predict based on previous words using N-Gram Language Model
            val nGramPredictions = nGramModel.predictNextWords(contextList, "", 5)

            val normalizedPrev1 = prevWord?.lowercase()?.trim() ?: (contextList.lastOrNull()?.lowercase()?.trim() ?: "")
            val normalizedPrev2 = prevWord2?.lowercase()?.trim() ?: (if (contextList.size >= 2) contextList[contextList.size - 2].lowercase().trim() else "")
            
            // 1. Get predictions from our 3-word trigram & 2-word bigram machine-learning models
            val mlTrigramPredicted = if (normalizedPrev2.isNotEmpty() && normalizedPrev1.isNotEmpty()) {
                mlPredictor.predictNextWordsFromTrigram(normalizedPrev2, normalizedPrev1).map { it.first }
            } else emptyList()

            val mlBigramPredicted = if (normalizedPrev1.isNotEmpty()) mlPredictor.predictNextWords(normalizedPrev1).map { it.first } else emptyList()

            // 2. Personalized dynamic bigrams, then default pre-packaged bigrams
            val learnedPredicted = if (normalizedPrev1.isNotEmpty()) personalizedBigrams[normalizedPrev1] ?: emptyList() else emptyList()
            val predicted = if (normalizedPrev1.isNotEmpty()) bigrams[normalizedPrev1] ?: emptyList() else emptyList()
            
            val candidateList = mutableListOf<String>()
            candidateList.addAll(phrasePredictions)
            candidateList.addAll(mlTrigramPredicted)
            candidateList.addAll(nGramPredictions)
            candidateList.addAll(mlBigramPredicted)
            candidateList.addAll(learnedPredicted)
            candidateList.addAll(predicted)
            candidateList.addAll(userWords)
            candidateList.addAll(commonWords.map { it.word })

            val combinedPredictions = candidateList
                .filter { !settings.profanityFilterEnabled || !isProfane(it) }
                .distinct()
                .take(3)
            return combinedPredictions
        }

        // 1. Check slang/abbreviation expansion (e.g. omw -> on my way)
        val expansion = expandSlang(normalizedPrefix)
        if (expansion != null) {
            return listOf(prefix, expansion, "thanks")
        }

        // 2. Phrase completions matching typed prefix
        val phraseMatches = localGrammarPredictor.predictPhraseCompletions(contextList, normalizedPrefix, 3)

        // 3. Query N-gram model predictions matching current prefix
        val nGramMatches = nGramModel.predictNextWords(contextList, normalizedPrefix, 5)

        // 4. Query Trie for prefix matching in O(k) time
        val trieRawMatches = trie.searchPrefix(normalizedPrefix, 30)
            .map { it.first }
            .filter { !settings.profanityFilterEnabled || !isProfane(it) }

        val normalizedPrev1 = prevWord?.lowercase()?.trim() ?: (contextList.lastOrNull()?.lowercase()?.trim() ?: "")
        val normalizedPrev2 = prevWord2?.lowercase()?.trim() ?: (if (contextList.size >= 2) contextList[contextList.size - 2].lowercase().trim() else "")

        val mlTrigramMatches = if (normalizedPrev2.isNotEmpty() && normalizedPrev1.isNotEmpty()) {
            mlPredictor.predictNextWordsFromTrigram(normalizedPrev2, normalizedPrev1).map { it.first }
        } else emptyList()

        val contextBigrams = if (normalizedPrev1.isNotEmpty()) bigrams[normalizedPrev1] ?: emptyList() else emptyList()
        val learnedBigrams = if (normalizedPrev1.isNotEmpty()) personalizedBigrams[normalizedPrev1] ?: emptyList() else emptyList()

        val matchPool = mutableListOf<String>()
        matchPool.addAll(phraseMatches)
        matchPool.addAll(mlTrigramMatches)
        matchPool.addAll(nGramMatches)
        matchPool.addAll(trieRawMatches)
        matchPool.addAll(userWords)
        matchPool.addAll(commonWords.map { it.word })

        // Score and rank candidates by Phrase match, Trigram match, Bigram match, User habit words, and N-gram frequency
        val scoredMatches = matchPool
            .filter { it.lowercase().startsWith(normalizedPrefix) }
            .filter { !settings.profanityFilterEnabled || !isProfane(it) }
            .distinctBy { it.lowercase() }
            .sortedByDescending { word ->
                val lower = word.lowercase()
                var score = 100f
                if (phraseMatches.any { it.lowercase() == lower }) score += 3000f
                if (mlTrigramMatches.any { it.lowercase() == lower }) score += 2000f
                if (learnedBigrams.any { it.lowercase() == lower }) score += 1500f
                if (userWords.contains(lower)) score += 1200f
                if (nGramMatches.any { it.lowercase() == lower }) score += 1000f
                if (contextBigrams.any { it.lowercase() == lower }) score += 700f
                val wordFreq = commonWords.firstOrNull { it.word.lowercase() == lower }?.frequency ?: 10
                score += wordFreq.toFloat()
                // Prefer words whose length is close to typed prefix
                score -= (lower.length - normalizedPrefix.length) * 4f
                score
            }

        val suggestions = mutableListOf<String>()

        // Check for typo dynamic spelling corrections if typed prefix is not exact word
        val isExact = isWordInDictionary(normalizedPrefix)
        val corrections = if (!isExact) getSpellingCorrections(normalizedPrefix, prevWord, prevWord2, tapCoords) else emptyList()

        val isFirstUpper = prefix.isNotEmpty() && prefix[0].isUpperCase()
        val isAllUpper = prefix.isNotEmpty() && prefix.length > 1 && prefix.all { it.isUpperCase() }

        fun applyCasing(word: String): String {
            if (word.isEmpty()) return word
            if (isAllUpper) return word.uppercase()
            if (isFirstUpper && word.length > 1) return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (isFirstUpper && word.length == 1 && word.lowercase() == "i") return "I"
            return word
        }

        // Center Slot (Index 1): ALWAYS the primary accurate word / top prediction / autocorrect candidate
        val centerWord = when {
            corrections.isNotEmpty() -> corrections.first()
            isExact -> if (normalizedPrefix == "i") "I" else prefix
            scoredMatches.isNotEmpty() -> scoredMatches.first()
            else -> prefix
        }

        // Left Slot (Index 0): Raw typed literal if middle is a prediction/correction, otherwise alternative candidate
        val candidatePool = mutableListOf<String>().apply {
            addAll(corrections)
            addAll(scoredMatches)
            addAll(listOfNotNull(emojiPredictions[normalizedPrefix]))
            addAll(commonWords.map { it.word })
            addAll(userWords)
            addAll(listOf("and", "you", "to", "this", "in", "it"))
        }.filter { !settings.profanityFilterEnabled || !isProfane(it) }
         .distinctBy { it.lowercase() }

        val leftWord = if (centerWord.lowercase() != normalizedPrefix) {
            prefix
        } else {
            scoredMatches.firstOrNull { it.lowercase() != centerWord.lowercase() }
                ?: corrections.firstOrNull { it.lowercase() != centerWord.lowercase() }
                ?: candidatePool.firstOrNull { it.lowercase() != centerWord.lowercase() }
                ?: prefix
        }

        // Right Slot (Index 2): Alternative candidate / emoji / next-word prediction
        val rightWord = candidatePool.firstOrNull { candidate ->
            candidate.lowercase() != centerWord.lowercase() && candidate.lowercase() != leftWord.lowercase()
        } ?: "and"

        return listOf(applyCasing(leftWord), applyCasing(centerWord), applyCasing(rightWord))
    }

    /**
     * Finds spelling corrections scoring above the suggestion confidence threshold using fast Trie Levenshtein and fuzzy lookup.
     */
    fun getSpellingCorrections(
        word: String,
        prevWord: String? = null,
        prevWord2: String? = null,
        tapCoords: List<PointF>? = null
    ): List<String> {
        val normalized = word.lowercase().trim()
        if (normalized.isEmpty()) return emptyList()

        // Common typos & transposition dictionary
        val typoMap = mapOf(
            "teh" to "the", "yhe" to "the", "taht" to "that", "tgat" to "that", "yhat" to "that",
            "helo" to "hello", "recieve" to "receive", "recieved" to "received", "recieving" to "receiving",
            "recive" to "receive", "hw" to "how", "hwo" to "how", "yu" to "you", "yuo" to "you",
            "thx" to "thanks", "pls" to "please", "plz" to "please", "tks" to "thanks",
            "woudl" to "would", "wodul" to "would", "woukd" to "would", "shoudl" to "should",
            "shoukd" to "should", "coudl" to "could", "coukd" to "could", "cud" to "could",
            "yesturday" to "yesterday", "tommorow" to "tomorrow", "tommorrow" to "tomorrow",
            "goverment" to "government", "occured" to "occurred", "definately" to "definitely",
            "definetly" to "definitely", "beautifull" to "beautiful", "seperate" to "separate",
            "untill" to "until", "accommodate" to "accommodate", "accomodate" to "accommodate",
            "wierd" to "weird", "belive" to "believe", "truely" to "truly", "mispell" to "misspell",
            "alot" to "a lot", "infront" to "in front", "atleast" to "at least", "gonna" to "going to",
            "wanna" to "want to", "gotta" to "got to", "writting" to "writing", "speling" to "spelling",
            "grammer" to "grammar", "keybord" to "keyboard", "mye" to "my", "tyep" to "type",
            "wrk" to "work", "wrd" to "word", "appl" to "apple", "prdct" to "predict",
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "im" to "I'm", "ive" to "I've",
            "ill" to "I'll", "id" to "I'd", "youre" to "you're", "youve" to "you've", "theyre" to "they're",
            "weve" to "we've", "its" to "it's", "thats" to "that's", "isnt" to "isn't", "arent" to "aren't",
            "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't",
            "hadnt" to "hadn't", "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
            "lets" to "let's", "wnat" to "want", "smd" to "and", "ehst" to "what", "alredy" to "already",
            "alwasy" to "always", "beacuse" to "because", "becuase" to "because", "comming" to "coming",
            "realy" to "really", "thier" to "their", "tought" to "thought", "tihs" to "this",
            "whcih" to "which", "abotu" to "about", "peopel" to "people", "jsut" to "just",
            "knwo" to "know", "themselfs" to "themselves", "wich" to "which", "widht" to "width",
            "acording" to "according", "beleive" to "believe", "rember" to "remember", "frind" to "friend",
            "freind" to "friend", "mkae" to "make", "liek" to "like", "godo" to "good", "tha" to "the",
            "hte" to "the", "adn" to "and", "fomr" to "from", "frm" to "from", "oyu" to "you",
            "dontknow" to "don't know", "goodmorning" to "good morning", "goodnight" to "good night",
            "thankyou" to "thank you", "thanksalot" to "thanks a lot", "howareyou" to "how are you",
            "seeyou" to "see you", "loveyou" to "love you", "letsgo" to "let's go", "withyou" to "with you",
            "goingto" to "going to", "wantto" to "want to", "didnt" to "didn't", "doesnt" to "doesn't",
            "theres" to "there's", "wheres" to "where's", "heres" to "here's", "hows" to "how's",
            "whos" to "who's", "youll" to "you'll", "theyll" to "they'll", "theyve" to "they've",
            "embarass" to "embarrass", "neccessary" to "necessary", "necesary" to "necessary",
            "unfortunatly" to "unfortunately", "probaly" to "probably", "probly" to "probably",
            "familar" to "familiar", "guarentee" to "guarantee", "schedual" to "schedule",
            "intresting" to "interesting", "differant" to "different", "experiance" to "experience",
            "fone" to "phone", "enuf" to "enough", "nite" to "night", "thru" to "through",
            "calender" to "calendar", "restarant" to "restaurant", "restaraunt" to "restaurant",
            "runing" to "running", "begining" to "beginning", "priviledge" to "privilege"
        )
        val directTypoMatch = typoMap[normalized]

        // 1. Contraction Candidate Search (e.g. dont -> don't, cant -> can't, im -> I'm)
        val cleanNormalized = normalized.replace("'", "")
        val contractionCandidates = mutableListOf<String>()
        commonWords.forEach { item ->
            if (item.word.contains("'") && item.word.lowercase().replace("'", "") == cleanNormalized) {
                contractionCandidates.add(item.word)
            }
        }

        // 2. Missed space split candidates (e.g. "goodmorning" -> "good morning", "thankyou" -> "thank you", "forthe" -> "for the")
        val missedSpaceCandidates = findMissedSpaceSplits(normalized)

        // 3. Phonetic Sound-Alike candidate lookup (Soundex/Metaphone)
        val phoneticKey = computePhoneticKey(normalized)
        val phoneticCandidates = if (phoneticKey.isNotEmpty()) phoneticIndex[phoneticKey] ?: emptyList() else emptyList()

        // 4. Ultra-fast Levenshtein edit distance lookup via pruned Trie (<0.2ms)
        val trieLevenshteinCandidates = LevenshteinAutoCorrector.searchTrieLevenshtein(trie.root, normalized, maxDistance = 2)
            .map { it.word }

        // 5. SymSpell candidates
        val symSpellCandidates = gboardEngine.symSpellEngine.lookup(normalized, maxDistance = 2f).map { it.term }

        // 6. Neural TFLite correction candidate
        val tfLiteCandidate = try {
            val res = tfLiteModel.correctText(normalized).trim()
            if (res.isNotEmpty() && res.lowercase() != normalized) res else null
        } catch (_: Exception) { null }

        val candidateList = (listOfNotNull(directTypoMatch, tfLiteCandidate) + missedSpaceCandidates + contractionCandidates + phoneticCandidates + trieLevenshteinCandidates + symSpellCandidates).distinct()
            .filter { !settings.profanityFilterEnabled || !isProfane(it) }

        if (candidateList.isEmpty()) return emptyList()

        return candidateList
            .map { dictWord ->
                val confidence = calculateCorrectionConfidence(normalized, dictWord, prevWord, prevWord2, tapCoords)
                Pair(dictWord, confidence)
            }
            .filter { it.second >= SUGGESTION_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)
    }

    /**
     * Splits a run-on word where the user missed one or more spaces (e.g. "goodmorning" -> "good morning", "thankyou" -> "thank you", "howareyou" -> "how are you").
     */
    fun findMissedSpaceSplits(typed: String): List<String> {
        val lower = typed.lowercase().trim()
        if (lower.length < 3) return emptyList()

        val results = mutableListOf<Pair<String, Float>>()

        // 1. Two-word split: word = part1 + part2
        for (i in 1 until lower.length) {
            val p1 = lower.substring(0, i)
            val p2 = lower.substring(i)

            val valid1 = (p1 == "a" || p1 == "i" || (p1.length >= 2 && isWordInDictionary(p1)))
            val valid2 = (p2 == "a" || p2 == "i" || (p2.length >= 2 && isWordInDictionary(p2)))

            if (valid1 && valid2) {
                val s1 = if (p1 == "i") "I" else p1
                val s2 = if (p2 == "i") "I" else p2
                val splitCandidate = "$s1 $s2"

                val freq1 = commonWords.firstOrNull { it.word.lowercase() == p1 }?.frequency ?: (if (userWords.contains(p1)) 80 else 20)
                val freq2 = commonWords.firstOrNull { it.word.lowercase() == p2 }?.frequency ?: (if (userWords.contains(p2)) 80 else 20)
                var score = (freq1 + freq2).toFloat()

                val bigramMatches = bigrams[p1] ?: emptyList()
                val learnedMatches = personalizedBigrams[p1] ?: emptyList()
                if (bigramMatches.any { it.lowercase() == p2 } || learnedMatches.any { it.lowercase() == p2 }) {
                    score += 600f
                }

                if (p1.length == 1 && p1 != "a" && p1 != "i") score -= 300f
                if (p2.length == 1 && p2 != "a" && p2 != "i") score -= 300f

                results.add(splitCandidate to score)
            }
        }

        // 2. Three-word split (e.g. "howareyou", "iloveyou", "seeyousoon", "whatisthat")
        if (lower.length >= 6) {
            for (i in 1 until lower.length - 2) {
                for (j in i + 1 until lower.length) {
                    val p1 = lower.substring(0, i)
                    val p2 = lower.substring(i, j)
                    val p3 = lower.substring(j)

                    val valid1 = (p1 == "a" || p1 == "i" || (p1.length >= 2 && isWordInDictionary(p1)))
                    val valid2 = (p2 == "a" || p2 == "i" || (p2.length >= 2 && isWordInDictionary(p2)))
                    val valid3 = (p3 == "a" || p3 == "i" || (p3.length >= 2 && isWordInDictionary(p3)))

                    if (valid1 && valid2 && valid3) {
                        val s1 = if (p1 == "i") "I" else p1
                        val s2 = if (p2 == "i") "I" else p2
                        val s3 = if (p3 == "i") "I" else p3
                        val splitCandidate = "$s1 $s2 $s3"
                        results.add(splitCandidate to 400f)
                    }
                }
            }
        }

        return results.sortedByDescending { it.second }.map { it.first }.distinct()
    }

    /**
     * Compute keyboard-proximity weighted Damerau-Levenshtein edit distance.
     */
    fun computeWeightedEditDistance(s1: String, s2: String): Float {
        val clean1 = s1.lowercase().replace("'", "")
        val clean2 = s2.lowercase().replace("'", "")
        if (clean1 == clean2) return 0.1f // Contraction match (dont <-> don't)

        val n = s1.length
        val m = s2.length
        val dp = Array(n + 1) { FloatArray(m + 1) }
        for (i in 0..n) dp[i][0] = i.toFloat()
        for (j in 0..m) dp[0][j] = j.toFloat()

        for (i in 1..n) {
            for (j in 1..m) {
                val subCost = getSubstitutionCost(s1[i - 1], s2[j - 1])
                val delCost = if (s1[i - 1] == '\'') 0.1f else 1f
                val insCost = if (s2[j - 1] == '\'') 0.1f else 1f
                dp[i][j] = minOf(
                    dp[i - 1][j] + delCost,        // deletion
                    dp[i][j - 1] + insCost,        // insertion
                    dp[i - 1][j - 1] + subCost     // substitution
                )
                // Transposition (swapping adjacent letters e.g. teh <-> the)
                if (i > 1 && j > 1 && s1[i - 1].lowercaseChar() == s2[j - 2].lowercaseChar() && s1[i - 2].lowercaseChar() == s2[j - 1].lowercaseChar()) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 0.4f)
                }
            }
        }
        return dp[n][m]
    }

    private fun getSubstitutionCost(c1: Char, c2: Char): Float {
        if (c1 == c2) return 0f
        val p1 = keyCoordinates[c1.lowercaseChar()]
        val p2 = keyCoordinates[c2.lowercaseChar()]
        if (p1 != null && p2 != null) {
            // Adjust the physical key center p1 by the user's learned offset
            val offset = mlPredictor.getTouchOffset(c1.lowercaseChar())
            val adjustedX = p1.x + offset.x
            val adjustedY = p1.y + offset.y

            val dx = adjustedX - p2.x
            val dy = adjustedY - p2.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 0.18f) {
                // Continuous probability scaling for adjacent key mis-taps
                return 0.25f + (dist / 0.18f) * 0.35f
            }
        }
        return 1.0f
    }

    /**
     * Decodes a list of normalized touch points (0.0 to 1.0) into the most likely matching words.
     */
    fun decodeSwipePath(path: List<PointF>): List<String> {
        if (path.size < 2) return emptyList()

        // 1. Query predictions from our ML template matching model
        val mlSwipePredictions = mlPredictor.predictFromSwipePatterns(path, 0.05f)

        val results = mutableListOf<Pair<String, Float>>()

        // Include both base dictionary words and user learned words in the candidates
        val candidates = commonWords.map { it.word to it.frequency } + userWords.map { it to 25 }

        for ((word, frequency) in candidates) {
            val lowercaseWord = word.lowercase().trim()
            if (lowercaseWord.length < 2) continue

            var score = calculateSwipeCost(lowercaseWord, path)
            if (score < 1000f) {
                // If the user has a learned ML swipe template for this word, apply a substantial score bonus!
                val mlMatch = mlSwipePredictions.firstOrNull { it.first.lowercase() == lowercaseWord }
                if (mlMatch != null) {
                    score *= (1.0f - mlMatch.second * 0.5f) // Up to 50% cost distance reduction for high similarity templates!
                }
                
                val frequencyBonus = 1.0f - (frequency / 1000f) * 0.15f
                results.add(Pair(word, score * frequencyBonus))
            }
        }

        // Add any high-confidence ML swipe predictions that weren't captured by physics calculations
        mlSwipePredictions.forEach { (word, similarity) ->
            if (!results.any { it.first.lowercase() == word.lowercase() }) {
                // Synthesize a highly competitive cost score to surface it in suggestions
                results.add(Pair(word, 0.18f * (1.0f - similarity)))
            }
        }

        return results.sortedBy { it.second }.map { it.first }.distinct().take(3)
    }

    private fun calculateSwipeCost(word: String, path: List<PointF>): Float {
        val cleanWord = word.lowercase().trim().filter { it.isLetter() }
        if (cleanWord.length < 2 || path.size < 2) return Float.MAX_VALUE

        val firstCharPos = keyCoordinates[cleanWord.first()] ?: return Float.MAX_VALUE
        val lastCharPos = keyCoordinates[cleanWord.last()] ?: return Float.MAX_VALUE

        val startDist = distance(firstCharPos, path.first())
        if (startDist > 0.38f) return Float.MAX_VALUE

        val endDist = distance(lastCharPos, path.last())
        if (endDist > 0.38f) return Float.MAX_VALUE

        // 1. Unique sequence of keys (collapsing duplicate adjacent letters e.g. 'l-o-o-k' -> 'l-o-k')
        val charSequence = cleanWord.fold(StringBuilder()) { sb, c ->
            if (sb.isEmpty() || sb.last() != c) sb.append(c) else sb
        }.toString()

        val uniqueKeys = charSequence.mapNotNull { keyCoordinates[it] }
        if (uniqueKeys.size < 2) return Float.MAX_VALUE

        // 2. Compute letter-to-path sequential matching distance
        var currentPathIdx = 0
        var totalSeqDist = 0f

        for (keyPos in uniqueKeys) {
            var minLetterDist = Float.MAX_VALUE
            var bestIdx = currentPathIdx

            for (i in currentPathIdx until path.size) {
                val dist = distance(keyPos, path[i])
                if (dist < minLetterDist) {
                    minLetterDist = dist
                    bestIdx = i
                }
            }

            totalSeqDist += minLetterDist
            currentPathIdx = bestIdx
        }

        val normalizedSeqDistance = totalSeqDist / uniqueKeys.size

        // 3. Calculate path length vs expected word trajectory length mismatch
        var expectedWordLength = 0f
        for (i in 0 until uniqueKeys.size - 1) {
            expectedWordLength += distance(uniqueKeys[i], uniqueKeys[i + 1])
        }

        var actualPathLength = 0f
        for (i in 0 until path.size - 1) {
            actualPathLength += distance(path[i], path[i + 1])
        }

        val lengthDiff = kotlin.math.abs(actualPathLength - expectedWordLength)
        val lengthMismatchPenalty = lengthDiff * 0.35f

        // 4. Calculate path divergence (sample points along user swipe path to penalize points far from word skeleton)
        val sampleCount = kotlin.math.min(12, path.size)
        val step = kotlin.math.max(1, path.size / sampleCount)
        var totalDivergence = 0f
        var sampledPoints = 0

        var idx = 0
        while (idx < path.size) {
            val pt = path[idx]
            val minDistToWordKeys = uniqueKeys.minOf { distance(it, pt) }
            totalDivergence += minDistToWordKeys
            sampledPoints++
            idx += step
        }
        val avgDivergence = if (sampledPoints > 0) totalDivergence / sampledPoints else 0f

        // Combined cost score
        return normalizedSeqDistance + (startDist * 1.2f) + (endDist * 1.2f) + lengthMismatchPenalty + (avgDivergence * 0.7f)
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
            val dictionaryWords = (commonWords.map { it.word } + userWords)
            val isValid = dictionaryWords.any { it.lowercase() == w2 }
            if (!isValid) return false
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
        val w1 = typedWord.lowercase().trim()
        val w2 = candidate.lowercase().trim()
        if (w1.isEmpty() || w2.isEmpty()) return 0.0f
        if (w1 == w2) return 1.0f

        // Contraction match (e.g. dont <-> don't, cant <-> can't)
        if (w1.replace("'", "") == w2.replace("'", "")) {
            return 0.95f
        }

        // Missed space split match (e.g. "goodmorning" <-> "good morning", "thankyou" <-> "thank you", "infront" <-> "in front")
        if (w2.contains(" ") && w2.replace(" ", "") == w1) {
            val parts = w2.split(" ")
            val allValid = parts.all { it == "a" || it == "i" || isWordInDictionary(it) }
            if (allValid) {
                var splitConfidence = 0.88f
                if (parts.size == 2) {
                    val p1 = parts[0]
                    val p2 = parts[1]
                    if (bigrams[p1]?.any { it.lowercase() == p2 } == true || personalizedBigrams[p1]?.any { it.lowercase() == p2 } == true) {
                        splitConfidence = 0.96f
                    }
                }
                return splitConfidence
            }
        }

        // Safeguard 1: Suppressed corrections (from backspace-undo)
        if (suppressedCorrections[w1]?.contains(w2) == true) return 0.0f

        // Safeguard 2: Code tokens, URLs, emails, camelCase, snake_case
        if (isCodeOrSpecialToken(w1) || isCodeOrSpecialToken(w2)) return 0.0f

        // Safeguard 3: Valid dictionary words, user dictionary words, recently accepted words
        if (isWordInDictionary(w1) || userWords.contains(w1) || recentlyAcceptedWords.contains(w1)) {
            return 0.0f
        }

        // --- Signal 1: Edit Distance (w1 * edit_dist) ---
        val d = computeWeightedEditDistance(w1, w2)
        if (d > 2.2f) return 0.0f
        var editDistScore = (1.0f - (d / 2.2f)) * 0.55f
        if (d <= 1.1f) {
            editDistScore += 0.20f
        }

        // --- Signal 2: Tap Geometry (w2 * tap_geometry) ---
        var tapGeometryScore = 0.0f
        if (tapCoords != null && tapCoords.size == w2.length) {
            var totalTouchDist = 0.0f
            var validKeyCount = 0
            for (i in w2.indices) {
                val keyChar = w2[i].lowercaseChar()
                val targetPos = keyCoordinates[keyChar]
                if (targetPos != null) {
                    val touch = tapCoords[i]
                    val offset = mlPredictor.getTouchOffset(keyChar)
                    val adjX = targetPos.x + offset.x
                    val adjY = targetPos.y + offset.y
                    val dx = touch.x - adjX
                    val dy = touch.y - adjY
                    totalTouchDist += sqrt(dx * dx + dy * dy)
                    validKeyCount++
                }
            }
            if (validKeyCount > 0) {
                val avgDist = totalTouchDist / validKeyCount
                tapGeometryScore = when {
                    avgDist < 0.10f -> 0.25f
                    avgDist < 0.25f -> (1.0f - (avgDist - 0.10f) / 0.15f) * 0.25f
                    else -> -0.10f
                }
            }
        } else if (w1.length == w2.length) {
            var adjacentCount = 0
            for (i in w1.indices) {
                val c1 = w1[i]
                val c2 = w2[i]
                if (c1 != c2) {
                    val p1 = keyCoordinates[c1.lowercaseChar()]
                    val p2 = keyCoordinates[c2.lowercaseChar()]
                    if (p1 != null && p2 != null) {
                        val dx = p1.x - p2.x
                        val dy = p1.y - p2.y
                        if (sqrt(dx * dx + dy * dy) < 0.18f) adjacentCount++
                    }
                }
            }
            tapGeometryScore = 0.04f * adjacentCount
        }

        // --- Signal 3: Language Model Probability (N-Gram + Bigram / Trigram) ---
        var lmScore = 0.0f
        val prev1 = prevWord?.lowercase()?.trim() ?: ""
        val prev2 = prevWord2?.lowercase()?.trim() ?: ""
        val contextList = listOfNotNull(prevWord2, prevWord)

        if (contextList.isNotEmpty()) {
            val nGramProb = nGramModel.getProbability(w2, contextList)
            lmScore += (nGramProb * 0.25f)
        }

        if (prev1.isNotEmpty()) {
            val learnedPredicted = personalizedBigrams[prev1] ?: emptyList()
            val predicted = bigrams[prev1] ?: emptyList()
            val mlNextWords = mlPredictor.predictNextWords(prev1).map { it.first.lowercase() }

            if (learnedPredicted.any { it.lowercase() == w2 }) {
                lmScore += 0.20f
            }
            if (predicted.any { it.lowercase() == w2 }) {
                lmScore += 0.12f
            }
            if (mlNextWords.contains(w2)) {
                lmScore += 0.15f
            }

            // Trigram context boost P(word | prev2, prev1)
            if (prev2.isNotEmpty()) {
                val trigramKey = "$prev2 $prev1"
                val trigramPredicted = personalizedBigrams[trigramKey] ?: emptyList()
                val mlTrigramPredicted = mlPredictor.predictNextWordsFromTrigram(prev2, prev1).map { it.first.lowercase() }
                if (trigramPredicted.any { it.lowercase() == w2 } || mlTrigramPredicted.contains(w2)) {
                    lmScore += 0.22f
                }
            }
        }

        // --- Signal 4: User Dictionary / Personalization (w4 * user_freq) ---
        var userFreqScore = 0.0f
        if (userWords.contains(w2)) {
            userFreqScore = 0.15f
        }

        // --- Signal 5: Corpus Frequency Prior (w5 * unigram_freq) ---
        val freq = commonWords.firstOrNull { it.word.lowercase() == w2 }?.frequency ?: 10
        val unigramScore = (freq / 1000f) * 0.10f

        // --- Signal 6: Phonetic Sound-Alike Signal (Soundex / Metaphone match) ---
        var phoneticScore = 0.0f
        val pk1 = computePhoneticKey(w1)
        val pk2 = computePhoneticKey(w2)
        if (pk1.isNotEmpty() && pk1 == pk2) {
            phoneticScore = 0.22f
        }

        // --- Signal 7: Neural TFLite Agreement ---
        var tfLiteScore = 0.0f
        try {
            val tfliteFix = tfLiteModel.correctText(w1).trim().lowercase()
            if (tfliteFix.isNotEmpty() && tfliteFix == w2) {
                tfLiteScore = 0.35f
            }
        } catch (_: Exception) {}

        var totalConfidence = editDistScore + tapGeometryScore + lmScore + userFreqScore + unigramScore + phoneticScore + tfLiteScore

        // Short word penalty (only for large edit distance on short words)
        if (w1.length <= 3 && d > 1.1f) {
            totalConfidence -= 0.10f
        }

        return totalConfidence.coerceIn(0.0f, 1.0f)
    }

    companion object {
        const val SILENT_CORRECT_THRESHOLD = 0.40f // Autocorrect threshold
        const val SUGGESTION_THRESHOLD = 0.30f     // Suggestion candidate threshold
    }

    /**
     * Get frequency for a word from corpus or user dictionary in O(1) time.
     */
    fun getWordFrequency(word: String): Int {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return 0
        if (userWords.contains(w)) return 120
        return commonWordsFreqMap[w] ?: 10
    }

    /**
     * Check if a word exists in the app's dictionary or libraries (case-insensitive) in O(1) time.
     */
    fun isWordInDictionary(word: String): Boolean {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return false
        if (commonWordsSet.contains(w) || userWords.contains(w) || slangExpansions.containsKey(w) || recentlyAcceptedWords.contains(w)) return true

        // Strict grammatical suffix check to prevent false positives on random typos
        if (w.length >= 3 && w.endsWith("s") && !w.endsWith("ss") && commonWordsSet.contains(w.dropLast(1))) return true
        if (w.length >= 4 && w.endsWith("es") && commonWordsSet.contains(w.dropLast(2))) return true
        if (w.length >= 4 && w.endsWith("ed") && (commonWordsSet.contains(w.dropLast(2)) || commonWordsSet.contains(w.dropLast(1)))) return true
        if (w.length >= 5 && w.endsWith("ing") && (commonWordsSet.contains(w.dropLast(3)) || commonWordsSet.contains(w.dropLast(3) + "e"))) return true
        if (w.length >= 4 && w.endsWith("ly") && commonWordsSet.contains(w.dropLast(2))) return true

        return false
    }
}
