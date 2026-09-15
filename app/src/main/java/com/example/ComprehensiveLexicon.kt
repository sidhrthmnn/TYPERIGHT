package com.example

/**
 * Frequency-rated lexicon term.
 */
data class LexiconWord(val word: String, val frequency: Int)

/**
 * Comprehensive modern English lexicon, everyday digital vocabulary,
 * frequency-rated terms, collocations, and high-precision typo corrections.
 */
object ComprehensiveLexicon {

    /**
     * Curated list of high-frequency English vocabulary across all common domains.
     */
    val WORDS: List<LexiconWord> by lazy {
        val wordList = mutableListOf<LexiconWord>()
        
        fun add(word: String, freq: Int) {
            wordList.add(LexiconWord(word, freq))
        }

        // --- Core Pronouns, Determiners & Auxiliary Verbs ---
        val core = listOf(
            "the" to 1000, "be" to 850, "to" to 800, "of" to 750, "and" to 700, "a" to 650,
            "in" to 600, "that" to 550, "have" to 500, "i" to 480, "it" to 460, "for" to 450,
            "not" to 440, "on" to 430, "with" to 420, "he" to 410, "as" to 400, "you" to 395,
            "do" to 390, "at" to 385, "this" to 380, "but" to 375, "his" to 370, "by" to 365,
            "from" to 360, "they" to 355, "we" to 350, "say" to 345, "her" to 340, "she" to 335,
            "or" to 330, "an" to 325, "will" to 320, "my" to 315, "one" to 310, "all" to 305,
            "would" to 300, "there" to 295, "their" to 290, "what" to 285, "so" to 280, "up" to 275,
            "out" to 270, "if" to 265, "about" to 260, "who" to 255, "get" to 250, "which" to 245,
            "go" to 240, "me" to 235, "when" to 230, "make" to 225, "can" to 220, "like" to 215,
            "time" to 210, "no" to 205, "just" to 200, "him" to 195, "know" to 190, "take" to 185,
            "people" to 180, "into" to 175, "year" to 170, "your" to 165, "good" to 160, "some" to 155,
            "could" to 150, "them" to 145, "see" to 140, "other" to 135, "than" to 130, "then" to 125,
            "now" to 120, "look" to 115, "only" to 110, "come" to 105, "its" to 100, "over" to 98,
            "think" to 96, "also" to 94, "back" to 92, "after" to 90, "use" to 88, "two" to 86,
            "how" to 85, "our" to 84, "work" to 83, "first" to 82, "well" to 81, "way" to 80,
            "even" to 79, "new" to 78, "want" to 77, "because" to 76, "any" to 75, "these" to 74,
            "give" to 73, "day" to 72, "most" to 71, "us" to 70, "is" to 490, "are" to 470,
            "was" to 450, "were" to 430, "been" to 380, "has" to 370, "had" to 360, "did" to 300,
            "does" to 280, "doing" to 250, "done" to 260
        )
        core.forEach { add(it.first, it.second) }

        // --- Contractions & Conversational Staples ---
        val contractions = listOf(
            "don't" to 350, "can't" to 340, "won't" to 310, "didn't" to 320, "doesn't" to 300,
            "isn't" to 280, "aren't" to 260, "wasn't" to 250, "weren't" to 230, "hasn't" to 220,
            "haven't" to 240, "hadn't" to 200, "couldn't" to 230, "wouldn't" to 240, "shouldn't" to 220,
            "I'm" to 400, "you're" to 360, "he's" to 330, "she's" to 320, "it's" to 420, "we're" to 310,
            "they're" to 320, "I've" to 350, "you've" to 290, "we've" to 280, "they've" to 260,
            "I'll" to 360, "you'll" to 300, "he'll" to 260, "she'll" to 250, "it'll" to 270,
            "we'll" to 290, "they'll" to 260, "I'd" to 320, "you'd" to 260, "he'd" to 240,
            "we'd" to 250, "they'd" to 240, "that's" to 380, "what's" to 340, "who's" to 280,
            "where's" to 270, "when's" to 240, "why's" to 230, "how's" to 260, "let's" to 350,
            "there's" to 340, "here's" to 310
        )
        contractions.forEach { add(it.first, it.second) }

        // --- Technology, Mobile, Internet & Modern Communication ---
        val tech = listOf(
            "phone" to 190, "mobile" to 170, "smartphone" to 140, "android" to 180, "apple" to 160,
            "google" to 220, "internet" to 180, "wifi" to 190, "bluetooth" to 160, "battery" to 170,
            "charger" to 150, "charging" to 140, "screen" to 180, "keyboard" to 200, "display" to 150,
            "camera" to 180, "photo" to 190, "photos" to 170, "picture" to 180, "pictures" to 160,
            "video" to 190, "videos" to 170, "audio" to 150, "sound" to 160, "volume" to 150,
            "mic" to 140, "microphone" to 130, "speaker" to 150, "speakers" to 140, "headphone" to 130,
            "headphones" to 150, "earbuds" to 130, "laptop" to 170, "computer" to 190, "pc" to 150,
            "tablet" to 140, "ipad" to 140, "device" to 160, "devices" to 140, "hardware" to 130,
            "software" to 160, "app" to 240, "apps" to 210, "application" to 170, "applications" to 150,
            "update" to 190, "updates" to 160, "updating" to 140, "updated" to 160, "download" to 180,
            "downloading" to 150, "downloaded" to 150, "upload" to 160, "uploading" to 140,
            "uploaded" to 140, "install" to 160, "installed" to 150, "installing" to 140,
            "delete" to 160, "deleted" to 150, "deleting" to 130, "remove" to 150, "removed" to 140,
            "message" to 220, "messages" to 190, "messaging" to 160, "text" to 210, "texts" to 170,
            "texting" to 160, "texted" to 150, "chat" to 190, "chats" to 160, "chatting" to 140,
            "email" to 210, "emails" to 180, "emailed" to 150, "emailing" to 140, "inbox" to 150,
            "send" to 210, "sent" to 200, "sending" to 170, "receive" to 180, "received" to 180,
            "receiving" to 150, "reply" to 180, "replied" to 160, "replying" to 140, "forward" to 150,
            "call" to 200, "called" to 180, "calling" to 170, "calls" to 160, "voice" to 170,
            "voicemail" to 130, "notification" to 160, "notifications" to 160, "alert" to 150,
            "alerts" to 130, "status" to 170, "profile" to 170, "account" to 190, "accounts" to 150,
            "password" to 180, "passwords" to 130, "username" to 160, "login" to 180, "logout" to 150,
            "sign" to 160, "signed" to 150, "signing" to 140, "website" to 180, "websites" to 150,
            "link" to 190, "links" to 160, "url" to 160, "browser" to 160, "browsing" to 130,
            "search" to 190, "searched" to 150, "searching" to 150, "network" to 160, "connection" to 170,
            "connected" to 160, "connecting" to 140, "disconnect" to 130, "offline" to 160, "online" to 190,
            "server" to 160, "servers" to 140, "cloud" to 170, "storage" to 160, "memory" to 160,
            "gigabyte" to 120, "hotspot" to 140, "code" to 170, "coding" to 140, "developer" to 150,
            "program" to 160, "programming" to 140, "system" to 170, "settings" to 190, "setup" to 160,
            "config" to 130, "data" to 200, "file" to 180, "files" to 160, "folder" to 160,
            "document" to 160, "documents" to 150, "pdf" to 160, "image" to 170, "images" to 160,
            "emoji" to 170, "emojis" to 150, "sticker" to 140, "stickers" to 130, "gif" to 150,
            "screenshot" to 160, "screenshots" to 140, "camera" to 180, "security" to 160, "privacy" to 160
        )
        tech.forEach { add(it.first, it.second) }

        // --- Greetings, Courtesy & Common Expressions ---
        val courtesy = listOf(
            "hello" to 250, "hi" to 260, "hey" to 250, "bye" to 200, "goodbye" to 180,
            "please" to 270, "thanks" to 300, "thank" to 280, "welcome" to 210, "sorry" to 260,
            "excuse" to 160, "pardon" to 130, "congratulations" to 160, "congrats" to 180,
            "cheers" to 160, "alright" to 190, "okay" to 250, "ok" to 260, "sure" to 240,
            "yeah" to 250, "yes" to 250, "yep" to 200, "nope" to 180, "maybe" to 210,
            "perhaps" to 160, "probably" to 190, "definitely" to 190, "absolutely" to 180,
            "exactly" to 180, "certainly" to 160, "indeed" to 150, "agreed" to 160,
            "awesome" to 200, "amazing" to 190, "great" to 230, "wonderful" to 180, "fantastic" to 170,
            "excellent" to 170, "perfect" to 200, "cool" to 210, "fine" to 200, "nice" to 220,
            "love" to 240, "loved" to 180, "loving" to 160, "like" to 250, "liked" to 180,
            "appreciate" to 170, "appreciated" to 150, "grateful" to 160, "glad" to 190, "happy" to 210
        )
        courtesy.forEach { add(it.first, it.second) }

        // --- People, Relationships & Roles ---
        val people = listOf(
            "friend" to 200, "friends" to 190, "family" to 210, "mother" to 180, "mom" to 210,
            "father" to 170, "dad" to 210, "parent" to 150, "parents" to 170, "brother" to 170,
            "brothers" to 140, "sister" to 170, "sisters" to 140, "son" to 160, "sons" to 130,
            "daughter" to 160, "daughters" to 130, "baby" to 180, "babies" to 140, "child" to 170,
            "children" to 180, "kid" to 190, "kids" to 190, "husband" to 170, "wife" to 180,
            "partner" to 170, "boyfriend" to 160, "girlfriend" to 160, "buddy" to 150, "colleague" to 160,
            "colleagues" to 150, "coworker" to 160, "coworkers" to 150, "boss" to 170, "manager" to 170,
            "leader" to 150, "team" to 190, "member" to 160, "members" to 160, "neighbor" to 150,
            "neighbors" to 140, "person" to 190, "people" to 220, "man" to 200, "men" to 170,
            "woman" to 200, "women" to 180, "boy" to 180, "boys" to 160, "girl" to 180,
            "girls" to 160, "guy" to 200, "guys" to 210, "doctor" to 170, "nurse" to 150,
            "patient" to 150, "teacher" to 170, "student" to 180, "students" to 170, "professor" to 140,
            "driver" to 150, "police" to 150, "lawyer" to 140, "client" to 170, "clients" to 160,
            "customer" to 180, "customers" to 170, "guest" to 150, "guests" to 140, "host" to 140
        )
        people.forEach { add(it.first, it.second) }

        // --- Work, Business, Projects & Finance ---
        val work = listOf(
            "work" to 230, "working" to 190, "worked" to 170, "job" to 210, "jobs" to 160,
            "career" to 160, "office" to 190, "desk" to 150, "meeting" to 210, "meetings" to 180,
            "project" to 190, "projects" to 170, "task" to 180, "tasks" to 170, "report" to 180,
            "reports" to 160, "reported" to 140, "presentation" to 160, "slides" to 140, "deadline" to 170,
            "deadlines" to 150, "schedule" to 180, "scheduled" to 160, "scheduling" to 140,
            "calendar" to 170, "appointment" to 160, "appointments" to 140, "plan" to 190,
            "plans" to 170, "planned" to 150, "planning" to 160, "strategy" to 150, "goal" to 170,
            "goals" to 160, "target" to 150, "targets" to 140, "budget" to 160, "finance" to 150,
            "financial" to 150, "money" to 220, "cash" to 180, "card" to 190, "cards" to 160,
            "credit" to 170, "debit" to 150, "bank" to 180, "banking" to 140, "dollar" to 180,
            "dollars" to 170, "cent" to 140, "cents" to 130, "price" to 180, "prices" to 160,
            "cost" to 180, "costs" to 160, "pay" to 200, "paid" to 190, "paying" to 160,
            "payment" to 180, "payments" to 160, "receipt" to 160, "invoice" to 160, "bill" to 170,
            "bills" to 150, "salary" to 160, "wage" to 140, "contract" to 160, "agreement" to 150,
            "company" to 190, "companies" to 160, "business" to 190, "businesses" to 150,
            "startup" to 150, "market" to 180, "marketing" to 150, "sales" to 170, "product" to 190,
            "products" to 180, "service" to 180, "services" to 170, "order" to 190, "orders" to 170,
            "ordered" to 160, "ordering" to 150, "purchase" to 160, "purchased" to 140, "buy" to 200,
            "buying" to 170, "bought" to 170, "sell" to 180, "selling" to 160, "sold" to 160,
            "store" to 190, "stores" to 160, "shop" to 180, "shopping" to 180, "mall" to 150,
            "discount" to 160, "deal" to 180, "deals" to 160, "refund" to 150, "package" to 170,
            "packages" to 150, "delivery" to 180, "deliver" to 160, "delivered" to 160, "shipping" to 160,
            "shipped" to 150, "shipment" to 140, "track" to 160, "tracking" to 160, "tracked" to 140
        )
        work.forEach { add(it.first, it.second) }

        // --- Places, Travel, Transportation & Direction ---
        val places = listOf(
            "home" to 230, "house" to 200, "apartment" to 170, "room" to 190, "rooms" to 160,
            "place" to 210, "places" to 170, "location" to 180, "address" to 180, "street" to 180,
            "road" to 180, "avenue" to 150, "building" to 170, "city" to 200, "cities" to 160,
            "town" to 170, "village" to 140, "state" to 180, "country" to 190, "world" to 210,
            "airport" to 170, "airplane" to 150, "plane" to 170, "flight" to 180, "flights" to 160,
            "ticket" to 180, "tickets" to 160, "boarding" to 140, "passport" to 150, "visa" to 140,
            "luggage" to 140, "suitcase" to 140, "bag" to 180, "bags" to 160, "backpack" to 150,
            "train" to 170, "trains" to 140, "subway" to 150, "metro" to 150, "bus" to 180,
            "buses" to 140, "station" to 170, "stop" to 180, "taxi" to 160, "cab" to 150,
            "uber" to 170, "lyft" to 140, "car" to 210, "cars" to 180, "vehicle" to 150,
            "drive" to 190, "driving" to 170, "driver" to 160, "drove" to 150, "driven" to 140,
            "ride" to 180, "riding" to 150, "walk" to 190, "walking" to 170, "walked" to 150,
            "run" to 190, "running" to 170, "ran" to 150, "hotel" to 180, "hotels" to 150,
            "resort" to 140, "trip" to 190, "trips" to 160, "travel" to 180, "traveling" to 160,
            "vacation" to 180, "holiday" to 170, "holidays" to 150, "tour" to 150, "journey" to 150,
            "map" to 180, "maps" to 160, "direction" to 170, "directions" to 170, "navigation" to 150,
            "north" to 160, "south" to 160, "east" to 160, "west" to 160, "left" to 190,
            "right" to 210, "straight" to 170, "corner" to 160, "near" to 180, "far" to 170,
            "close" to 190, "closer" to 150, "distance" to 160, "miles" to 160, "park" to 180,
            "beach" to 170, "lake" to 150, "river" to 150, "mountain" to 160, "mountains" to 150
        )
        places.forEach { add(it.first, it.second) }

        // --- Food, Cooking, Drinks & Dining ---
        val food = listOf(
            "food" to 210, "eat" to 200, "eating" to 170, "ate" to 150, "meal" to 180,
            "meals" to 150, "breakfast" to 190, "lunch" to 200, "dinner" to 210, "snack" to 160,
            "snacks" to 140, "restaurant" to 190, "restaurants" to 160, "cafe" to 170, "bar" to 170,
            "menu" to 170, "order" to 190, "cook" to 170, "cooking" to 160, "cooked" to 150,
            "kitchen" to 170, "recipe" to 160, "recipes" to 140, "drink" to 190, "drinking" to 160,
            "water" to 220, "coffee" to 220, "tea" to 190, "milk" to 180, "juice" to 170,
            "soda" to 150, "beer" to 170, "wine" to 170, "cocktail" to 140, "bread" to 180,
            "butter" to 160, "cheese" to 170, "egg" to 170, "eggs" to 180, "meat" to 160,
            "chicken" to 190, "beef" to 160, "pork" to 150, "fish" to 180, "rice" to 180,
            "pasta" to 170, "pizza" to 190, "burger" to 170, "sandwich" to 170, "sandwiches" to 140,
            "salad" to 170, "soup" to 170, "fruit" to 170, "fruits" to 150, "apple" to 170,
            "apples" to 150, "banana" to 160, "bananas" to 140, "orange" to 160, "oranges" to 140,
            "berry" to 140, "berries" to 140, "strawberry" to 150, "vegetable" to 160, "vegetables" to 160,
            "potato" to 160, "potatoes" to 150, "tomato" to 160, "tomatoes" to 150, "onion" to 150,
            "garlic" to 150, "dessert" to 160, "cake" to 170, "chocolate" to 180, "cookie" to 160,
            "cookies" to 160, "ice" to 180, "cream" to 170, "sugar" to 170, "salt" to 170,
            "pepper" to 160, "sauce" to 160, "spicy" to 150, "sweet" to 170, "delicious" to 170,
            "tasty" to 160, "hungry" to 180, "thirsty" to 160, "full" to 180
        )
        food.forEach { add(it.first, it.second) }

        // --- Time Expressions, Calendar & Durations ---
        val timeWords = listOf(
            "time" to 220, "times" to 180, "moment" to 170, "second" to 170, "seconds" to 160,
            "minute" to 190, "minutes" to 190, "hour" to 190, "hours" to 180, "day" to 220,
            "days" to 200, "daily" to 160, "week" to 210, "weeks" to 190, "weekly" to 150,
            "weekend" to 190, "weekends" to 160, "month" to 200, "months" to 180, "monthly" to 150,
            "year" to 210, "years" to 200, "yearly" to 140, "morning" to 210, "afternoon" to 190,
            "evening" to 190, "night" to 210, "tonight" to 210, "midnight" to 150, "noon" to 150,
            "today" to 240, "tomorrow" to 230, "yesterday" to 210, "soon" to 200, "early" to 190,
            "earlier" to 170, "late" to 200, "later" to 210, "latest" to 160, "now" to 230,
            "current" to 170, "currently" to 170, "already" to 200, "always" to 210, "never" to 200,
            "sometimes" to 190, "often" to 180, "usually" to 180, "seldom" to 130, "rarely" to 150,
            "recently" to 170, "lately" to 160, "before" to 210, "after" to 210, "during" to 180,
            "while" to 190, "until" to 200, "since" to 190, "past" to 180, "future" to 170,
            "Monday" to 180, "Tuesday" to 170, "Wednesday" to 170, "Thursday" to 170,
            "Friday" to 190, "Saturday" to 190, "Sunday" to 190,
            "January" to 160, "February" to 150, "March" to 160, "April" to 160,
            "May" to 170, "June" to 160, "July" to 160, "August" to 160,
            "September" to 160, "October" to 160, "November" to 160, "December" to 160
        )
        timeWords.forEach { add(it.first, it.second) }

        // --- Weather, Nature & Environment ---
        val weather = listOf(
            "weather" to 190, "sun" to 180, "sunny" to 170, "cloud" to 160, "clouds" to 150,
            "cloudy" to 160, "rain" to 190, "raining" to 170, "rained" to 150, "rainy" to 160,
            "storm" to 160, "stormy" to 140, "wind" to 160, "windy" to 150, "snow" to 170,
            "snowing" to 150, "snowy" to 140, "ice" to 160, "icy" to 140, "cold" to 190,
            "colder" to 150, "coldest" to 140, "hot" to 190, "hotter" to 150, "hottest" to 140,
            "warm" to 180, "warmer" to 150, "cool" to 180, "cooler" to 150, "freeze" to 150,
            "freezing" to 150, "frozen" to 140, "temperature" to 170, "degrees" to 160,
            "summer" to 180, "winter" to 180, "spring" to 170, "autumn" to 150, "fall" to 180,
            "nature" to 160, "sky" to 170, "earth" to 170, "star" to 160, "stars" to 150,
            "moon" to 160, "air" to 180, "water" to 210, "fire" to 170, "ocean" to 160,
            "sea" to 160, "tree" to 170, "trees" to 160, "flower" to 170, "flowers" to 160,
            "grass" to 160, "leaf" to 150, "leaves" to 150, "plant" to 160, "plants" to 150,
            "animal" to 160, "animals" to 160, "pet" to 170, "pets" to 160, "dog" to 190,
            "dogs" to 170, "cat" to 190, "cats" to 170, "bird" to 170, "birds" to 160
        )
        weather.forEach { add(it.first, it.second) }

        // --- Health, Emotions & State of Being ---
        val health = listOf(
            "health" to 180, "healthy" to 170, "sick" to 180, "ill" to 150, "illness" to 140,
            "pain" to 170, "hurt" to 170, "hurts" to 160, "hurting" to 140, "headache" to 160,
            "fever" to 150, "cough" to 150, "cold" to 190, "sore" to 150, "throat" to 150,
            "medicine" to 160, "pill" to 150, "pills" to 140, "doctor" to 180, "hospital" to 170,
            "clinic" to 150, "pharmacy" to 150, "tired" to 200, "exhausted" to 160, "sleep" to 200,
            "sleeping" to 170, "slept" to 160, "sleepy" to 160, "awake" to 160, "wake" to 180,
            "woke" to 160, "waking" to 150, "rest" to 180, "resting" to 150, "relax" to 170,
            "relaxing" to 160, "relaxed" to 150, "stress" to 170, "stressed" to 160, "worry" to 170,
            "worried" to 170, "worrying" to 140, "nervous" to 160, "anxious" to 160, "calm" to 170,
            "peace" to 160, "peaceful" to 150, "happy" to 210, "happiness" to 150, "sad" to 180,
            "angry" to 170, "mad" to 170, "upset" to 170, "excited" to 180, "exciting" to 170,
            "bored" to 160, "boring" to 160, "scared" to 160, "afraid" to 160, "safe" to 180,
            "danger" to 150, "dangerous" to 150, "fit" to 170, "fitness" to 160, "gym" to 180,
            "workout" to 180, "exercise" to 170, "body" to 180, "head" to 180, "face" to 180,
            "eye" to 180, "eyes" to 190, "ear" to 160, "ears" to 160, "mouth" to 170,
            "tooth" to 150, "teeth" to 160, "hand" to 190, "hands" to 180, "arm" to 160,
            "arms" to 160, "leg" to 160, "legs" to 160, "foot" to 170, "feet" to 170,
            "heart" to 180, "brain" to 160, "stomach" to 160, "back" to 190, "skin" to 170
        )
        health.forEach { add(it.first, it.second) }

        // --- Common Adjectives & Adverbs ---
        val descriptors = listOf(
            "big" to 200, "bigger" to 170, "biggest" to 160, "large" to 180, "larger" to 160,
            "small" to 200, "smaller" to 170, "smallest" to 150, "tiny" to 160, "huge" to 170,
            "great" to 220, "greater" to 170, "greatest" to 160, "little" to 200, "long" to 200,
            "longer" to 170, "longest" to 150, "short" to 190, "shorter" to 160, "shortest" to 140,
            "high" to 190, "higher" to 170, "highest" to 150, "low" to 180, "lower" to 160,
            "lowest" to 140, "deep" to 160, "shallow" to 140, "thick" to 150, "thin" to 150,
            "heavy" to 170, "heavier" to 140, "light" to 180, "lighter" to 150, "hard" to 200,
            "harder" to 170, "hardest" to 150, "soft" to 160, "smooth" to 150, "rough" to 140,
            "fast" to 200, "faster" to 180, "fastest" to 160, "quick" to 190, "quicker" to 160,
            "quickly" to 180, "slow" to 180, "slower" to 160, "slowly" to 170, "early" to 190,
            "late" to 200, "later" to 210, "easy" to 200, "easier" to 180, "easiest" to 160,
            "easily" to 170, "simple" to 180, "simpler" to 150, "difficult" to 170, "tough" to 160,
            "clean" to 180, "cleaner" to 150, "dirty" to 160, "clear" to 190, "clearly" to 170,
            "dark" to 180, "bright" to 170, "empty" to 170, "full" to 180, "rich" to 160,
            "poor" to 160, "strong" to 180, "stronger" to 160, "weak" to 150, "young" to 180,
            "younger" to 160, "old" to 200, "older" to 180, "oldest" to 160, "true" to 190,
            "false" to 160, "real" to 190, "really" to 230, "fake" to 160, "right" to 220,
            "wrong" to 190, "correct" to 180, "incorrect" to 140, "accurate" to 150, "exact" to 160,
            "exactly" to 180, "important" to 190, "main" to 180, "major" to 160, "minor" to 150,
            "special" to 180, "common" to 170, "rare" to 150, "normal" to 170, "strange" to 160,
            "weird" to 170, "different" to 190, "same" to 200, "similar" to 170, "busy" to 180,
            "free" to 190, "available" to 170, "ready" to 200, "open" to 190, "close" to 190,
            "closed" to 180, "final" to 170, "finally" to 180, "almost" to 190, "nearly" to 160,
            "quite" to 180, "pretty" to 190, "very" to 230, "too" to 210, "enough" to 190,
            "extra" to 160, "super" to 180, "totally" to 170, "completely" to 170, "especially" to 160
        )
        descriptors.forEach { add(it.first, it.second) }

        // --- Common Verbs & Action Words ---
        val actions = listOf(
            "ask" to 200, "asked" to 180, "asking" to 170, "tell" to 210, "told" to 190,
            "telling" to 170, "talk" to 210, "talked" to 180, "talking" to 180, "speak" to 180,
            "spoke" to 160, "spoken" to 150, "speaking" to 160, "listen" to 180, "listened" to 150,
            "listening" to 160, "hear" to 190, "heard" to 180, "hearing" to 160, "watch" to 190,
            "watched" to 170, "watching" to 170, "look" to 210, "looked" to 180, "looking" to 190,
            "see" to 220, "saw" to 190, "seen" to 180, "seeing" to 170, "feel" to 200,
            "felt" to 180, "feeling" to 180, "find" to 210, "found" to 200, "finding" to 170,
            "leave" to 190, "left" to 190, "leaving" to 160, "stay" to 190, "stayed" to 160,
            "staying" to 160, "wait" to 200, "waited" to 160, "waiting" to 180, "stop" to 190,
            "stopped" to 170, "stopping" to 150, "start" to 200, "started" to 180, "starting" to 170,
            "begin" to 170, "began" to 160, "begun" to 140, "beginning" to 160, "finish" to 180,
            "finished" to 180, "finishing" to 150, "complete" to 170, "completed" to 160,
            "completing" to 140, "try" to 210, "tried" to 180, "trying" to 190, "need" to 220,
            "needed" to 180, "needing" to 150, "want" to 230, "wanted" to 190, "wanting" to 160,
            "wish" to 170, "wished" to 140, "hope" to 200, "hoped" to 160, "hoping" to 170,
            "help" to 210, "helped" to 170, "helping" to 160, "show" to 190, "showed" to 170,
            "shown" to 160, "showing" to 160, "move" to 180, "moved" to 170, "moving" to 160,
            "change" to 190, "changed" to 170, "changing" to 160, "keep" to 200, "kept" to 170,
            "keeping" to 160, "hold" to 180, "held" to 160, "holding" to 160, "bring" to 190,
            "brought" to 170, "bringing" to 160, "send" to 210, "sent" to 200, "sending" to 170,
            "build" to 170, "built" to 160, "building" to 160, "create" to 180, "created" to 160,
            "creating" to 150, "open" to 190, "opened" to 170, "opening" to 160, "close" to 180,
            "closed" to 170, "closing" to 150, "check" to 210, "checked" to 180, "checking" to 180,
            "test" to 180, "tested" to 160, "testing" to 170, "learn" to 180, "learned" to 170,
            "learning" to 170, "teach" to 160, "taught" to 150, "teaching" to 150, "remember" to 190,
            "remembered" to 160, "remembering" to 140, "forget" to 170, "forgot" to 170, "forgotten" to 150,
            "forgetting" to 140, "meet" to 190, "met" to 170, "meeting" to 190, "join" to 170,
            "joined" to 160, "joining" to 150, "share" to 190, "shared" to 170, "sharing" to 160,
            "follow" to 180, "followed" to 160, "following" to 160, "lead" to 160, "led" to 150,
            "leading" to 150, "support" to 170, "supported" to 150, "supporting" to 150,
            "save" to 190, "saved" to 170, "saving" to 160, "spend" to 170, "spent" to 160,
            "spending" to 150, "choose" to 170, "chose" to 150, "chosen" to 150, "choosing" to 150,
            "decide" to 170, "decided" to 170, "deciding" to 140, "agree" to 180, "agreed" to 170,
            "agreeing" to 140, "enjoy" to 180, "enjoyed" to 160, "enjoying" to 160
        )
        actions.forEach { add(it.first, it.second) }

        // --- Question Words, Connectors & Pronouns ---
        val grammarParts = listOf(
            "who" to 220, "what" to 250, "where" to 230, "when" to 240, "why" to 220,
            "how" to 240, "which" to 220, "whose" to 160, "whom" to 130,
            "and" to 500, "or" to 400, "but" to 420, "so" to 380, "yet" to 200, "for" to 350,
            "nor" to 180, "because" to 280, "since" to 230, "as" to 320, "although" to 180,
            "though" to 210, "even" to 220, "if" to 320, "unless" to 180, "until" to 220,
            "while" to 210, "wherever" to 150, "whenever" to 160, "however" to 200,
            "therefore" to 160, "moreover" to 140, "furthermore" to 140, "otherwise" to 170,
            "instead" to 180, "besides" to 160, "anyway" to 190, "anyways" to 160
        )
        grammarParts.forEach { add(it.first, it.second) }

        wordList.distinctBy { it.word.lowercase() }
    }

    /**
     * Common typo and fat-finger correction lookup.
     */
    val TYPOS: Map<String, String> = mapOf(
        // Core typos & transpositions
        "teh" to "the", "yhe" to "the", "hte" to "the", "tha" to "the", "tht" to "that",
        "taht" to "that", "tgat" to "that", "yhat" to "that", "tath" to "that",
        "adn" to "and", "nad" to "and", "annd" to "and", "smd" to "and",
        "wiht" to "with", "wtih" to "with", "wth" to "with", "whit" to "with",
        "thsi" to "this", "tihs" to "this", "thid" to "this", "tjos" to "this",
        "thye" to "they", "tgey" to "they", "thwy" to "they", "tje" to "the",
        "cna" to "can", "xan" to "can", "acn" to "can",
        "fro" to "for", "fpr" to "for", "fir" to "for", "dor" to "for",
        "oyu" to "you", "yuo" to "you", "yu" to "you", "yoy" to "you",
        "ahve" to "have", "hvea" to "have", "hav" to "have", "haev" to "have",
        "whcih" to "which", "wich" to "which", "whci" to "which",
        "abotu" to "about", "abot" to "about", "aubot" to "about",
        "woudl" to "would", "wodul" to "would", "woul" to "would",
        "coudl" to "could", "cud" to "could", "clould" to "could",
        "shoudl" to "should", "shud" to "should", "shoul" to "should",
        "jsut" to "just", "juts" to "just", "jst" to "just",
        "knwo" to "know", "konw" to "know", "nkow" to "know",
        "mkae" to "make", "amke" to "make", "mka" to "make",
        "liek" to "like", "lkie" to "like", "lik" to "like",
        "godo" to "good", "godd" to "good", "goof" to "good",
        "peopel" to "people", "poeple" to "people", "pelple" to "people",
        "tought" to "thought", "thoght" to "thought", "thout" to "thought",
        "becuase" to "because", "beacuse" to "because", "bcz" to "because", "bcuz" to "because", "becasue" to "because",
        "definately" to "definitely", "definetly" to "definitely", "defintely" to "definitely",
        "recieve" to "receive", "recieved" to "received", "recieving" to "receiving", "recive" to "receive",
        "seperate" to "separate", "seperated" to "separated", "seperating" to "separating",
        "occured" to "occurred", "occuring" to "occurring", "occurr" to "occur",
        "untill" to "until", "untl" to "until",
        "beleive" to "believe", "belive" to "believe", "beleived" to "believed",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "tommorrow" to "tomorrow",
        "yesturday" to "yesterday", "yestarday" to "yesterday", "yeserday" to "yesterday",
        "alot" to "a lot", "noone" to "no one", "everytime" to "every time",
        "accomodate" to "accommodate", "accommadate" to "accommodate",
        "neccessary" to "necessary", "necesary" to "necessary", "neccesary" to "necessary",
        "wierd" to "weird", "weired" to "weird",
        "truely" to "truly", "truly" to "truly",
        "freind" to "friend", "frind" to "friend", "frend" to "friend", "freinds" to "friends",
        "togeather" to "together", "togather" to "together", "together" to "together",
        "thier" to "their", "theri" to "their", "theire" to "their",
        "calender" to "calendar", "calander" to "calendar",
        "succesful" to "successful", "sucessful" to "successful", "successfull" to "successful",
        "goverment" to "government", "govnerment" to "government", "goverment" to "government",
        "resturant" to "restaurant", "restarant" to "restaurant", "restaraunt" to "restaurant",
        "mispell" to "misspell", "mispelled" to "misspelled", "misspel" to "misspell",
        "grammer" to "grammar", "gramer" to "grammar",
        "writting" to "writing", "writeing" to "writing",
        "comming" to "coming", "cooming" to "coming",
        "begining" to "beginning", "beggining" to "beginning",
        "runing" to "running", "swimmin" to "swimming",
        "realy" to "really", "relly" to "really", "raelly" to "really",
        "alwasy" to "always", "alway" to "always", "alwys" to "always",
        "alredy" to "already", "allready" to "already",
        "embarass" to "embarrass", "embarassed" to "embarrassed",
        "unfortunatly" to "unfortunately", "unfortuanately" to "unfortunately",
        "probaly" to "probably", "probly" to "probably", "prolly" to "probably", "probaby" to "probably",
        "familar" to "familiar", "familliar" to "familiar",
        "guarentee" to "guarantee", "garantee" to "guarantee", "gaurantee" to "guarantee",
        "schedual" to "schedule", "schedul" to "schedule", "skedule" to "schedule",
        "intresting" to "interesting", "intrest" to "interest",
        "differant" to "different", "diffrent" to "different",
        "experiance" to "experience", "experence" to "experience",
        "computre" to "computer", "compter" to "computer", "comptuer" to "computer",
        "keybord" to "keyboard", "keybaord" to "keyboard", "kyboard" to "keyboard",
        "applicatoin" to "application", "aplication" to "application",
        "messgae" to "message", "mesage" to "message", "messge" to "message",
        "quetion" to "question", "queston" to "question", "quesiton" to "question",
        "answer" to "answer", "anwer" to "answer", "aswer" to "answer",
        "adress" to "address", "addres" to "address",
        "dissappoint" to "disappoint", "disapoint" to "disappoint",
        "happend" to "happened", "hapened" to "happened",
        "noticable" to "noticeable", "noticible" to "noticeable",
        "oppurtunity" to "opportunity", "oportunity" to "opportunity",
        "priviledge" to "privilege", "privlege" to "privilege",
        "rember" to "remember", "remeber" to "remember", "remeber" to "remember",
        "themselfs" to "themselves", "themself" to "themselves",
        "fomr" to "from", "frm" to "from", "form" to "from",
        "somthing" to "something", "smth" to "something", "someting" to "something",
        "anyting" to "anything", "anythng" to "anything",
        "evning" to "evening", "mornign" to "morning", "mrng" to "morning",
        "fone" to "phone", "enuf" to "enough", "nite" to "night", "thru" to "through",
        "hw" to "how", "hwo" to "how", "hwo" to "how",
        "helo" to "hello", "helllo" to "hello", "hllo" to "hello",
        "thx" to "thanks", "pls" to "please", "plz" to "please", "tks" to "thanks",
        "idk" to "I don't know", "tbh" to "to be honest", "imo" to "in my opinion",
        "imho" to "in my humble opinion", "fyi" to "for your information",
        "btw" to "by the way", "brb" to "be right back", "np" to "no problem",
        "yw" to "you're welcome", "ty" to "thank you", "rn" to "right now",
        "asap" to "as soon as possible", "ttyl" to "talk to you later"
    )

    /**
     * Map of unpunctuated contractions to standard contractions.
     */
    val UNPUNCTUATED_CONTRACTIONS: Map<String, String> = mapOf(
        "dont" to "don't", "cant" to "can't", "wont" to "won't",
        "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
        "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
        "hes" to "he's", "shes" to "she's", "its" to "it's",
        "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
        "weve" to "we've", "we're" to "we're", "didnt" to "didn't",
        "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't",
        "havent" to "haven't", "hadnt" to "hadn't", "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't", "couldnt" to "couldn't",
        "thats" to "that's", "whats" to "what's", "heres" to "here's",
        "theres" to "there's", "wheres" to "where's", "hows" to "how's", "lets" to "let's"
    )

    /**
     * Extended contextual bigrams for predictive suggestions.
     */
    val EXTENDED_BIGRAMS: Map<String, List<String>> = mapOf(
        "how" to listOf("are you", "is it", "do you", "was your", "can I", "about", "much is"),
        "what" to listOf("time", "are you", "do you", "is your", "happened", "about", "kind of"),
        "where" to listOf("are you", "is the", "can I", "did you", "should we", "are we"),
        "when" to listOf("are you", "can we", "will you", "did you", "is the", "do you"),
        "why" to listOf("did you", "would you", "don't we", "is it", "are you", "not"),
        "thank" to listOf("you so much", "you for", "you very much", "you!"),
        "thanks" to listOf("for your help", "a lot", "so much", "again", "for letting me know"),
        "let" to listOf("me know", "us know", "me check", "me see", "it be", "me do that"),
        "please" to listOf("let me know", "find attached", "check this", "call me", "send me", "help me"),
        "looking" to listOf("forward to", "for", "at", "good", "great", "forward"),
        "forward" to listOf("to hearing from you", "to seeing you", "to your reply", "to working with you"),
        "could" to listOf("you please", "be", "have been", "we meet", "you help"),
        "would" to listOf("like to", "be great", "love to", "you mind", "appreciate it"),
        "feel" to listOf("free to", "like", "better", "good", "free"),
        "see" to listOf("you soon", "you later", "you tomorrow", "what happens", "if you can"),
        "sounds" to listOf("good to me", "great!", "like a plan", "good!", "awesome"),
        "good" to listOf("morning!", "night!", "afternoon!", "luck with", "to hear", "job!"),
        "take" to listOf("care!", "your time", "a look at", "it easy", "care of"),
        "keep" to listOf("in touch", "up the good work", "me posted", "it up", "going"),
        "talk" to listOf("to you later", "to you soon", "about it", "with you"),
        "at" to listOf("the same time", "your earliest convenience", "the moment", "home", "work"),
        "in" to listOf("the meantime", "addition to", "front of", "case you", "terms of"),
        "on" to listOf("my way", "the other hand", "the way", "top of that", "time")
    )
}
