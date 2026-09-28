package com.example

/**
 * Intelligent semantic mapper that converts typed text and words into comprehensive
 * matching emoji sets and Google Emoji Kitchen mashup items.
 */
object EmojiWordMapper {

    private val wordToEmojis: Map<String, List<String>> = mapOf(
        // Emotions & Faces
        "happy" to listOf("😊", "😀", "😃", "😄", "😁", "😆", "🥹", "☺️", "😋", "🥳", "😇", "🤩", "😸", "😺"),
        "smile" to listOf("😀", "😃", "😄", "😁", "😊", "🙂", "☺️", "😆", "😸", "😺"),
        "grin" to listOf("😁", "😀", "😃", "😆", "😸"),
        "joy" to listOf("😂", "🥹", "😊", "😄", "🥳", "😹"),
        "laugh" to listOf("😂", "🤣", "😆", "😅", "😹", "🤪", "😜"),
        "lol" to listOf("😂", "🤣", "😹", "💀", "😆"),
        "haha" to listOf("😂", "🤣", "😆", "😹", "😄"),
        "hehe" to listOf("🤭", "😏", "😸", "😋"),
        "rofl" to listOf("🤣", "😂"),
        "hilarious" to listOf("🤣", "😂", "💀"),
        "love" to listOf("❤️", "💖", "💕", "💓", "💗", "💘", "💝", "💞", "💟", "💌", "😍", "🥰", "😘", "🫶", "😻"),
        "heart" to listOf("❤️", "💖", "💕", "💓", "💗", "💘", "💝", "🖤", "🤍", "🤎", "💜", "💙", "💚", "💛", "🧡", "❣️", "💔"),
        "adore" to listOf("🥰", "😍", "❤️", "💖"),
        "kiss" to listOf("😘", "😚", "😗", "😙", "💋", "😽"),
        "smooch" to listOf("😘", "💋"),
        "wink" to listOf("😉", "😜", "😽"),
        "cool" to listOf("😎", "🕶️", "🆒", "🧊", "🥶", "🤙", "🏄"),
        "awesome" to listOf("😎", "🔥", "🤩", "🙌", "🤙", "👏"),
        "sad" to listOf("😢", "😭", "🥺", "🙁", "☹️", "😞", "😓", "😥", "😿", "💔", "😔", "😟", "🫤"),
        "cry" to listOf("😭", "😢", "🥺", "😿", "💧", "🌧️"),
        "tear" to listOf("😢", "😭", "🥺", "💧"),
        "sob" to listOf("😭", "😿", "🥺"),
        "sleep" to listOf("😴", "🥱", "💤", "🛌", "😪", "🛏️"),
        "sleepy" to listOf("😴", "🥱", "💤", "😪"),
        "tired" to listOf("🥱", "😫", "😩", "😴", "😮‍💨"),
        "exhausted" to listOf("😫", "😩", "🥱", "😮‍💨", "🫠"),
        "angry" to listOf("😡", "😠", "🤬", "👿", "💢", "😤", "😾"),
        "mad" to listOf("😡", "😠", "🤬", "😤", "😾"),
        "rage" to listOf("🤬", "😡", "🔥", "💢"),
        "furious" to listOf("🤬", "😡", "😤"),
        "fire" to listOf("🔥", "❤️‍🔥", "🚒", "🌶️", "♨️", "💥", "🧨", "☀️", "🥵"),
        "flame" to listOf("🔥", "❤️‍🔥", "💥"),
        "lit" to listOf("🔥", "💥", "🤩", "⚡"),
        "hot" to listOf("🔥", "🥵", "🌶️", "☀️"),
        "burn" to listOf("🔥", "❤️‍🔥"),
        "spicy" to listOf("🌶️", "🔥", "🥵"),
        "shock" to listOf("😱", "😲", "🤯", "😳", "🙀", "😯", "😮"),
        "shocked" to listOf("😱", "😲", "🤯", "😳", "🙀"),
        "surprise" to listOf("😲", "😮", "🫢", "🎉", "🙀"),
        "surprised" to listOf("😲", "🫢", "😮", "😳"),
        "wow" to listOf("🤩", "😮", "😲", "🤯", "👏"),
        "omg" to listOf("😱", "😲", "🤯", "🫢"),
        "mindblown" to listOf("🤯", "💥", "💣"),
        "explode" to listOf("🤯", "💥", "💣", "🌋"),
        "boom" to listOf("💥", "💣", "🤯"),
        "think" to listOf("🤔", "💭", "🧐", "🧠", "💡"),
        "thinking" to listOf("🤔", "💭", "🧐", "🧠"),
        "ponder" to listOf("🤔", "🧐", "💭"),
        "idea" to listOf("💡", "🧠", "✨"),
        "shh" to listOf("🤫", "🤐", "😶"),
        "quiet" to listOf("🤫", "🤐", "😶", "🔇"),
        "secret" to listOf("🤫", "🤐", "🔒", "🗝️"),
        "sick" to listOf("🤒", "🤕", "🤢", "🤮", "🤧", "😷", "💊", "💉"),
        "ill" to listOf("🤒", "🤕", "😷", "💊"),
        "vomit" to listOf("🤮", "🤢"),
        "puke" to listOf("🤮", "🤢"),
        "gross" to listOf("🤢", "🤮", "😖"),
        "disgust" to listOf("🤢", "🤮", "😣"),
        "dizzy" to listOf("😵", "😵‍💫", "🥴"),
        "freeze" to listOf("🥶", "❄️", "🧊", "☃️"),
        "cold" to listOf("🥶", "❄️", "🧊", "☃️", "🧣"),
        "ice" to listOf("🧊", "❄️", "🍦"),
        "sweat" to listOf("😰", "😥", "😓", "😅", "🥵", "😬"),
        "nervous" to listOf("😬", "😰", "😥", "🫣"),
        "anxious" to listOf("😰", "😥", "🥺", "🫣"),
        "angel" to listOf("😇", "👼", "✨", "🕊️"),
        "innocent" to listOf("😇", "🥺", "✨"),
        "devil" to listOf("😈", "👿", "👹", "👺"),
        "evil" to listOf("😈", "👿", "👹", "👺", "💀"),
        "crazy" to listOf("🤪", "😜", "🤡", "🙃"),
        "silly" to listOf("🤪", "😜", "😛", "🙃"),
        "wild" to listOf("🤪", "🦁", "🐅", "🌴"),
        "nerd" to listOf("🤓", "📚", "💻", "🧐"),
        "geek" to listOf("🤓", "💻", "🎮"),
        "smirk" to listOf("😏", "😼"),
        "skull" to listOf("💀", "☠️", "🪦"),
        "dead" to listOf("💀", "☠️", "🪦", "👻"),
        "death" to listOf("💀", "☠️", "🪦"),
        "skeleton" to listOf("💀", "☠️", "🦴"),
        "ghost" to listOf("👻", "🎃", "🦇", "🕷️", "🕸️"),
        "spooky" to listOf("👻", "🎃", "🦇", "🕷️", "💀"),
        "alien" to listOf("👽", "🛸", "👾", "🌌"),
        "ufo" to listOf("🛸", "👽", "🌌"),
        "robot" to listOf("🤖", "🦾", "🦿", "👾"),
        "bot" to listOf("🤖", "👾"),
        "poop" to listOf("💩"),
        "shit" to listOf("💩"),
        "clown" to listOf("🤡", "🎪", "🎈"),
        "party" to listOf("🥳", "🎉", "🎊", "🍾", "🎂", "🎈", "🪅", "🎁", "🕺", "💃"),
        "celebrate" to listOf("🥳", "🎉", "🎊", "🍾", "🥂"),
        "celebration" to listOf("🎉", "🎊", "🥳", "🎈"),
        "birthday" to listOf("🎂", "🎁", "🎈", "🥳", "🎉", "🍰", "🧁"),
        "bday" to listOf("🎂", "🎁", "🎈", "🥳"),

        // Gestures & Body
        "ok" to listOf("👌", "👍", "✅", "🆗"),
        "okay" to listOf("👌", "👍", "✅"),
        "yes" to listOf("✅", "👍", "✔️", "💯"),
        "yeah" to listOf("👍", "✅", "🙌"),
        "no" to listOf("❌", "👎", "🛑", "🚫", "🙅", "⛔"),
        "nope" to listOf("❌", "👎", "🙅"),
        "agree" to listOf("🤝", "👍", "✅"),
        "like" to listOf("👍", "❤️", "🥰", "😍"),
        "dislike" to listOf("👎", "❌"),
        "wave" to listOf("👋", "🙋", "🖐️"),
        "hi" to listOf("👋", "🙋", "✨"),
        "hello" to listOf("👋", "🙋", "😊"),
        "bye" to listOf("👋", "🏃", "🚶"),
        "goodbye" to listOf("👋", "🚶"),
        "pray" to listOf("🙏", "🤲", "🙇", "✨"),
        "please" to listOf("🙏", "🥺", "🤲"),
        "thanks" to listOf("🙏", "💐", "💖", "🤝"),
        "thank" to listOf("🙏", "💐", "💖"),
        "grateful" to listOf("🙏", "💖", "🥰"),
        "clap" to listOf("👏", "🙌", "🎉"),
        "applause" to listOf("👏", "🙌", "🎉"),
        "kudos" to listOf("👏", "🙌", "🏆"),
        "fist" to listOf("✊", "👊", "🤛", "🤜"),
        "punch" to listOf("👊", "🥊", "💥"),
        "peace" to listOf("✌️", "🕊️", "☮️"),
        "handshake" to listOf("🤝", "💼"),
        "deal" to listOf("🤝", "💼", "💰"),
        "hug" to listOf("🫂", "🤗", "❤️"),
        "hugs" to listOf("🫂", "🤗", "❤️", "💕"),
        "muscle" to listOf("💪", "🏋️", "🏆"),
        "strong" to listOf("💪", "🏋️", "🦾", "🦁"),
        "flex" to listOf("💪", "✨"),
        "eye" to listOf("👀", "👁️", "🧐"),
        "eyes" to listOf("👀", "👁️", "🧐"),
        "look" to listOf("👀", "👁️", "🔎"),
        "see" to listOf("👀", "👁️"),
        "watch" to listOf("👀", "⌚", "📺"),
        "lips" to listOf("👄", "💋"),
        "mouth" to listOf("👄", "👅"),
        "tongue" to listOf("👅", "😋", "😜", "😛"),
        "brain" to listOf("🧠", "💡", "🤓"),
        "ear" to listOf("👂", "🎧"),
        "listen" to listOf("👂", "🎧", "🎵"),

        // Animals & Pets
        "cat" to listOf("🐱", "🐈", "🐈‍⬛", "😻", "😹", "😽", "😿", "🙀", "😾", "🐾"),
        "cats" to listOf("🐱", "🐈", "🐈‍⬛", "😻", "😹", "😽", "🐾"),
        "kitty" to listOf("🐱", "🐈", "😻", "🐾"),
        "kitten" to listOf("🐱", "🐈", "😻", "🐾"),
        "dog" to listOf("🐶", "🐕", "🦮", "🐕‍🦺", "🐩", "🐾", "🐺", "🦴"),
        "dogs" to listOf("🐶", "🐕", "🦮", "🐕‍🦺", "🐩", "🐾", "🦴"),
        "puppy" to listOf("🐶", "🐕", "🐾", "🦴"),
        "pup" to listOf("🐶", "🐾"),
        "bear" to listOf("🐻", "🧸", "🐼", "🐨"),
        "teddy" to listOf("🧸", "🐻"),
        "koala" to listOf("🐨", "🌿"),
        "panda" to listOf("🐼", "🎋"),
        "lion" to listOf("🦁", "👑"),
        "tiger" to listOf("🐯", "🐅"),
        "fox" to listOf("🦊"),
        "wolf" to listOf("🐺", "🌕"),
        "monkey" to listOf("🐵", "🐒", "🦍", "🦧", "🙈", "🙉", "🙊", "🍌"),
        "ape" to listOf("🦍", "🦧", "🐒", "🐵"),
        "horse" to listOf("🐴", "🐎", "🦄"),
        "pony" to listOf("🐴", "🦄"),
        "unicorn" to listOf("🦄", "✨", "🌈"),
        "zebra" to listOf("🦓"),
        "deer" to listOf("🦌"),
        "cow" to listOf("🐮", "🐄", "🐂"),
        "bull" to listOf("🐂", "🐂"),
        "pig" to listOf("🐷", "🐖", "🐗", "🐽"),
        "piggy" to listOf("🐷", "🐽"),
        "sheep" to listOf("🐑", "🐏"),
        "lamb" to listOf("🐑"),
        "goat" to listOf("🐐"),
        "llama" to listOf("🦙"),
        "giraffe" to listOf("🦒"),
        "elephant" to listOf("🐘"),
        "mouse" to listOf("🐭", "🐁"),
        "rat" to listOf("🐀", "🐁"),
        "hamster" to listOf("🐹"),
        "rabbit" to listOf("🐰", "🐇", "🥕"),
        "bunny" to listOf("🐰", "🐇"),
        "bat" to listOf("🦇"),
        "sloth" to listOf("🦥"),
        "otter" to listOf("🦦"),
        "hedgehog" to listOf("🦔"),
        "bird" to listOf("🐦", "🦅", "🦆", "🦉", "🦜", "🕊️", "🦚", "🪶"),
        "birds" to listOf("🐦", "🦅", "🦆", "🦉", "🦜", "🕊️"),
        "eagle" to listOf("🦅"),
        "duck" to listOf("🦆", "🐥", "🐣"),
        "owl" to listOf("🦉"),
        "parrot" to listOf("🦜"),
        "swan" to listOf("🦢"),
        "flamingo" to listOf("🦩"),
        "penguin" to listOf("🐧"),
        "chicken" to listOf("🐔", "🐓", "🐣", "🐤"),
        "frog" to listOf("🐸"),
        "turtle" to listOf("🐢"),
        "snake" to listOf("🐍"),
        "dragon" to listOf("🐉", "🐲", "🔥"),
        "dinosaur" to listOf("🦖", "🦕"),
        "whale" to listOf("🐳", "🐋"),
        "dolphin" to listOf("🐬"),
        "shark" to listOf("🦈"),
        "fish" to listOf("🐟", "🐠", "🐡", "🦈", "🎣"),
        "octopus" to listOf("🐙"),
        "crab" to listOf("🦀"),
        "lobster" to listOf("🦞"),
        "shrimp" to listOf("🦐"),
        "butterfly" to listOf("🦋"),
        "bee" to listOf("🐝", "🍯"),
        "honey" to listOf("🍯", "🐝"),
        "spider" to listOf("🕷️", "🕸️"),
        "bug" to listOf("🐛", "🪲", "🐜", "🦗"),
        "insect" to listOf("🐛", "🪲", "🐜"),

        // Nature, Weather & Space
        "sun" to listOf("☀️", "🌞", "🌅", "🌄", "🏖️"),
        "sunny" to listOf("☀️", "🌞", "😎", "🏖️"),
        "sunshine" to listOf("☀️", "🌞", "✨"),
        "star" to listOf("⭐", "🌟", "✨", "💫", "🤩", "🌠"),
        "stars" to listOf("⭐", "🌟", "✨", "💫", "🌃"),
        "sparkle" to listOf("✨", "🪄", "💫", "🌟"),
        "sparkles" to listOf("✨", "🪄", "💫", "🌟"),
        "magic" to listOf("✨", "🪄", "🔮", "🧙"),
        "moon" to listOf("🌙", "🌕", "🌑", "🌚", "🌃"),
        "night" to listOf("🌙", "🌃", "🌌", "⭐", "😴"),
        "sky" to listOf("🌤️", "☁️", "🌌", "🌈"),
        "space" to listOf("🚀", "🛸", "🌌", "🪐", "👾"),
        "rocket" to listOf("🚀", "🛸", "🌌"),
        "cloud" to listOf("☁️", "⛅", "🌤️", "🌥️"),
        "rain" to listOf("🌧️", "☔", "💧", "⛈️"),
        "raining" to listOf("🌧️", "☔", "💧"),
        "storm" to listOf("⛈️", "🌩️", "⚡", "🌧️"),
        "lightning" to listOf("⚡", "🌩️", "⛈️"),
        "thunder" to listOf("⚡", "🌩️", "⛈️"),
        "snow" to listOf("❄️", "☃️", "⛄", "🧊", "🥶"),
        "winter" to listOf("❄️", "☃️", "🏂", "⛷️", "🧤"),
        "rainbow" to listOf("🌈", "✨"),
        "water" to listOf("💧", "🌊", "💦", "🥤"),
        "ocean" to listOf("🌊", "🏖️", "🐬", "🐋", "🏝️"),
        "sea" to listOf("🌊", "⛵", "🏖️", "🐠"),
        "beach" to listOf("🏖️", "🏝️", "🌴", "☀️", "🌊"),
        "tree" to listOf("🌲", "🌳", "🌴", "🌿", "🍃", "🌱"),
        "trees" to listOf("🌲", "🌳", "🌴", "🍃"),
        "forest" to listOf("🌲", "🌳", "🍃", "🦌"),
        "nature" to listOf("🌿", "🌱", "🍃", "🌲", "🌳", "🌸"),
        "flower" to listOf("🌸", "🌺", "🌹", "🌻", "🌷", "🌼", "💐", "🥀"),
        "flowers" to listOf("🌸", "🌺", "🌹", "🌻", "🌷", "🌼", "💐"),
        "rose" to listOf("🌹", "🥀"),
        "sunflower" to listOf("🌻", "☀️"),
        "leaf" to listOf("🍃", "🌿", "🍁", "🍂", "🌱"),
        "plant" to listOf("🌱", "🪴", "🌿", "🍀"),
        "clover" to listOf("🍀", "☘️"),

        // Food & Drink
        "food" to listOf("🍕", "🍔", "🍟", "🌭", "🌮", "🍣", "🍜", "🍩", "🍪", "🥪"),
        "eat" to listOf("🍽️", "🍕", "🍔", "😋", "🤤", "🥪"),
        "hungry" to listOf("🤤", "😋", "🍕", "🍔", "🥪"),
        "delicious" to listOf("😋", "🤤", "👌", "✨"),
        "yummy" to listOf("😋", "🤤", "🍩"),
        "pizza" to listOf("🍕", "🧀", "🇮🇹"),
        "burger" to listOf("🍔", "🍟", "🥪"),
        "hamburger" to listOf("🍔", "🍟"),
        "fries" to listOf("🍟", "🍔"),
        "taco" to listOf("🌮", "🌯"),
        "burrito" to listOf("🌯", "🌮"),
        "sandwich" to listOf("🥪", "🍞"),
        "sushi" to listOf("🍣", "🍱", "🍙"),
        "ramen" to listOf("🍜", "🥢", "🍲"),
        "noodle" to listOf("🍜", "🍝"),
        "noodles" to listOf("🍜", "🍝"),
        "pasta" to listOf("🍝", "🇮🇹", "🍕"),
        "spaghetti" to listOf("🍝"),
        "bread" to listOf("🍞", "🥖", "🥐", "🥯"),
        "croissant" to listOf("🥐", "🥖"),
        "cheese" to listOf("🧀", "🐭"),
        "meat" to listOf("🥩", "🍖", "🍗"),
        "steak" to listOf("🥩", "🍷"),
        "bacon" to listOf("🥓", "🍳"),
        "egg" to listOf("🥚", "🍳"),
        "salad" to listOf("🥗", "🥬", "🥑"),
        "soup" to listOf("🍲", "🥣"),
        "popcorn" to listOf("🍿", "🎬"),
        "cookie" to listOf("🍪", "🥛"),
        "cookies" to listOf("🍪", "🥛"),
        "cake" to listOf("🎂", "🍰", "🧁"),
        "cupcake" to listOf("🧁", "🍰"),
        "donut" to listOf("🍩", "☕"),
        "chocolate" to listOf("🍫", "🍩", "🍪"),
        "candy" to listOf("🍬", "🍭"),
        "lollipop" to listOf("🍭", "🍬"),
        "icecream" to listOf("🍦", "🍨", "🍧"),
        "fruit" to listOf("🍎", "🍌", "🍓", "🍇", "🍊", "🍉", "🍑", "🍒", "🍍"),
        "apple" to listOf("🍎", "🍏"),
        "banana" to listOf("🍌", "🐵"),
        "strawberry" to listOf("🍓", "🍰"),
        "grape" to listOf("🍇", "🍷"),
        "watermelon" to listOf("🍉"),
        "lemon" to listOf("🍋"),
        "orange" to listOf("🍊", "🧃"),
        "peach" to listOf("🍑"),
        "cherry" to listOf("🍒"),
        "pineapple" to listOf("🍍"),
        "avocado" to listOf("🥑", "🥗"),
        "coffee" to listOf("☕", "🧋", "🍵", "🥤", "🍩"),
        "tea" to listOf("🍵", "🫖", "🧋"),
        "boba" to listOf("🧋", "🍵"),
        "beer" to listOf("🍺", "🍻", "🥂"),
        "wine" to listOf("🍷", "🍾", "🥂"),
        "cocktail" to listOf("🍸", "🍹", "🥂", "🥃"),
        "drink" to listOf("🥤", "🍺", "🍷", "🍹", "☕"),
        "milk" to listOf("🥛", "🍪"),
        "juice" to listOf("🧃", "🥤", "🍊"),

        // Vehicles & Travel
        "car" to listOf("🚗", "🚙", "🏎️", "🚘", "🚕", "🚓"),
        "cars" to listOf("🚗", "🚙", "🏎️", "🚘"),
        "drive" to listOf("🚗", "🚙", "🚘", "🛣️"),
        "truck" to listOf("🚚", "🚛", "🛻"),
        "bus" to listOf("🚌", "🚍"),
        "bike" to listOf("🚲", "🏍️", "🛵"),
        "bicycle" to listOf("🚲"),
        "motorcycle" to listOf("🏍️", "🛵"),
        "train" to listOf("🚆", "🚂", "🚇", "🚄"),
        "plane" to listOf("✈️", "🛫", "🛬", "🚁"),
        "airplane" to listOf("✈️", "🛫", "🛬"),
        "flight" to listOf("✈️", "🛫", "🛬"),
        "fly" to listOf("✈️", "🪶", "🚀"),
        "boat" to listOf("⛵", "🚢", "🚤", "⚓"),
        "ship" to listOf("🚢", "⛵", "⚓"),
        "travel" to listOf("✈️", "🧳", "🗺️", "🏖️", "🏨"),
        "trip" to listOf("✈️", "🧳", "🚗", "🗺️"),
        "vacation" to listOf("🏖️", "🏝️", "✈️", "🌴", "☀️"),

        // Activities, Games & Sports
        "game" to listOf("🎮", "🕹️", "🎲", "👾", "🎯"),
        "gaming" to listOf("🎮", "🕹️", "👾", "🎧"),
        "gamer" to listOf("🎮", "🕹️", "👾"),
        "play" to listOf("🎮", "⚽", "🎲"),
        "sport" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏆"),
        "sports" to listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏆"),
        "soccer" to listOf("⚽", "🥅"),
        "football" to listOf("⚽", "🏈"),
        "basketball" to listOf("🏀"),
        "baseball" to listOf("⚾"),
        "tennis" to listOf("🎾"),
        "golf" to listOf("⛳"),
        "boxing" to listOf("🥊"),
        "swim" to listOf("🏊", "🏊‍♂️", "🏊‍♀️", "🌊"),
        "swimming" to listOf("🏊", "🏊‍♂️", "🏊‍♀️", "🌊"),
        "run" to listOf("🏃", "🏃‍♂️", "🏃‍♀️", "👟"),
        "running" to listOf("🏃", "🏃‍♂️", "🏃‍♀️", "👟"),
        "gym" to listOf("🏋️", "💪", "🏃", "🤸"),
        "workout" to listOf("🏋️", "💪", "🏃", "🤸"),
        "fitness" to listOf("💪", "🏋️", "🏃"),
        "trophy" to listOf("🏆", "🥇", "🥈", "🥉", "👑"),
        "win" to listOf("🏆", "🥇", "🎉", "👑"),
        "winner" to listOf("🏆", "🥇", "👑", "🎉"),
        "champion" to listOf("🏆", "👑", "🥇"),
        "medal" to listOf("🥇", "🥈", "🥉", "🎖️"),
        "crown" to listOf("👑", "🤴", "👸", "🏰", "💎"),
        "king" to listOf("👑", "🤴", "🏰"),
        "queen" to listOf("👑", "👸", "🏰"),

        // Objects & Technology
        "money" to listOf("💰", "💵", "💸", "🤑", "💳", "🪙", "🏦"),
        "cash" to listOf("💵", "💰", "💸", "🤑"),
        "dollar" to listOf("💵", "💲", "💰"),
        "rich" to listOf("🤑", "💰", "💎", "👑"),
        "coin" to listOf("🪙", "💰"),
        "diamond" to listOf("💎", "💍", "✨"),
        "ring" to listOf("💍", "💎"),
        "music" to listOf("🎵", "🎶", "🎧", "🎸", "🎹", "🎤", "🥁"),
        "song" to listOf("🎵", "🎶", "🎤", "🎧"),
        "sing" to listOf("🎤", "🎵", "🎶"),
        "guitar" to listOf("🎸", "🎵"),
        "piano" to listOf("🎹", "🎵"),
        "headphones" to listOf("🎧", "🎵"),
        "camera" to listOf("📷", "📸", "🤳"),
        "photo" to listOf("📷", "📸", "🖼️"),
        "picture" to listOf("🖼️", "📸", "🎨"),
        "video" to listOf("🎥", "🎬", "📹"),
        "movie" to listOf("🎬", "🍿", "🎥", "🎟️"),
        "film" to listOf("🎬", "🎥", "🎞️"),
        "phone" to listOf("📱", "📲", "📞", "☎️"),
        "mobile" to listOf("📱", "📲"),
        "computer" to listOf("💻", "🖥️", "⌨️"),
        "laptop" to listOf("💻"),
        "code" to listOf("💻", "👨‍💻", "👩‍💻", "⌨️"),
        "coding" to listOf("💻", "👨‍💻", "👩‍💻"),
        "book" to listOf("📚", "📖", "📕", "📗", "📘"),
        "books" to listOf("📚", "📖"),
        "read" to listOf("📖", "📚", "👓"),
        "reading" to listOf("📖", "📚"),
        "study" to listOf("📚", "📝", "🤓"),
        "write" to listOf("✍️", "📝", "✏️", "🖊️"),
        "writing" to listOf("✍️", "📝", "🖊️"),
        "pen" to listOf("🖊️", "✒️", "📝"),
        "pencil" to listOf("✏️", "📝"),
        "clock" to listOf("⏰", "⏱️", "🕐", "⌚"),
        "time" to listOf("⏰", "⏱️", "⏳", "⌚"),
        "watch" to listOf("⌚", "⏰", "⏱️"),
        "gift" to listOf("🎁", "🎀", "🎉"),
        "present" to listOf("🎁", "🎀"),
        "balloon" to listOf("🎈", "🎉"),
        "lock" to listOf("🔒", "🔐", "🔑"),
        "key" to listOf("🔑", "🗝️", "🔐"),
        "house" to listOf("🏠", "🏡"),
        "home" to listOf("🏠", "🏡"),
        "city" to listOf("🏙️", "🌆", "🌇"),
        "building" to listOf("🏢", "🏬", "🏙️"),
        "100" to listOf("💯"),
        "perfect" to listOf("💯", "👌", "✨"),
        "check" to listOf("✅", "✔️", "☑️"),
        "done" to listOf("✅", "✔️", "🎉"),
        "warning" to listOf("⚠️", "🚨", "🛑")
    )

    // Curated partners for building Google Emoji Kitchen mashups
    private val kitchenPartners = listOf(
        "❤️", "🔥", "😎", "😂", "🥳", "😭", "✨", "🥺", "💀", "🥰",
        "🤩", "😈", "🤖", "👻", "🎉", "💯", "😴", "🤯", "🥶", "😻",
        "🐶", "🍕", "☕", "👑"
    )

    /**
     * Extracts individual words and existing emojis from typed text.
     */
    fun getEmojisForText(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<String>()

        // 1. Direct emoji characters found in the text
        val emojiRegex = Regex("[\\p{So}\\p{Sk}\\p{Sm}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")
        emojiRegex.findAll(text).forEach { match ->
            val emojiStr = match.value.trim()
            if (emojiStr.isNotEmpty()) {
                results.add(emojiStr)
            }
        }

        // 2. Extract words (split by whitespace & punctuation)
        val words = text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }

        for (rawWord in words) {
            // Direct word lookup
            wordToEmojis[rawWord]?.let { results.addAll(it) }

            // Stemming variations (plurals, ing, ed)
            val stems = listOf(
                rawWord.removeSuffix("s"),
                rawWord.removeSuffix("es"),
                rawWord.removeSuffix("ing"),
                rawWord.removeSuffix("ed"),
                rawWord.removeSuffix("y") + "ies",
                rawWord.removeSuffix("ies") + "y"
            ).filter { it != rawWord && it.length >= 2 }

            for (stem in stems) {
                wordToEmojis[stem]?.let { results.addAll(it) }
            }

            // Prefix and partial word matching for comprehensive emoji discovery
            for ((dictWord, emojiList) in wordToEmojis) {
                if (dictWord.startsWith(rawWord) || (rawWord.length >= 3 && rawWord.startsWith(dictWord))) {
                    results.addAll(emojiList)
                } else if (rawWord.length >= 3 && dictWord.contains(rawWord)) {
                    results.addAll(emojiList)
                }
            }
        }

        return results.distinct()
    }

    /**
     * Generates all Emoji Kitchen suggestion cards based strictly on the current typed text.
     * Returns emptyList() if no text or no matching emojis exist.
     */
    fun getKitchenItemsForText(text: String): List<EmojiKitchenItem> {
        val emojis = getEmojisForText(text)
        if (emojis.isEmpty()) return emptyList()

        val items = mutableListOf<EmojiKitchenItem>()
        var counter = 0

        // 1. Base candidate emojis from typed text
        for (emoji in emojis) {
            items.add(
                EmojiKitchenItem(
                    id = "ek_${counter++}",
                    primaryEmoji = emoji,
                    secondaryEmoji = emoji,
                    previewText = emoji,
                    description = "Base Emoji $emoji"
                )
            )
        }

        // 2. Pair combinations between all matched candidate emojis
        if (emojis.size >= 2) {
            for (i in emojis.indices) {
                for (j in (i + 1) until emojis.size) {
                    val e1 = emojis[i]
                    val e2 = emojis[j]
                    val combo = resolveKnownMashup(e1, e2) ?: "$e1$e2"
                    items.add(
                        EmojiKitchenItem(
                            id = "ek_${counter++}",
                            primaryEmoji = e1,
                            secondaryEmoji = e2,
                            previewText = combo,
                            description = "Mashup $e1 + $e2"
                        )
                    )
                }
            }
        }

        // 3. All creative mashup combinations with kitchen partner emojis
        for (emoji in emojis) {
            for (partner in kitchenPartners) {
                if (partner != emoji) {
                    val combo = resolveKnownMashup(emoji, partner) ?: "$emoji$partner"
                    items.add(
                        EmojiKitchenItem(
                            id = "ek_${counter++}",
                            primaryEmoji = emoji,
                            secondaryEmoji = partner,
                            previewText = combo,
                            description = "Kitchen Mashup $emoji + $partner"
                        )
                    )
                }
            }
        }

        return items.distinctBy { it.previewText }
    }

    /**
     * Resolves known standard Google Emoji Kitchen mashups to composite Unicode representations
     * or compound stickers when applicable.
     */
    private fun resolveKnownMashup(e1: String, e2: String): String? {
        val pair = if (e1 < e2) "$e1+$e2" else "$e2+$e1"
        return when (pair) {
            "🐱+❤️" -> "😻"
            "😂+🐱" -> "😹"
            "🐱+😭" -> "😿"
            "🐱+😘" -> "😽"
            "🐱+🙀" -> "🙀"
            "🐱+😡" -> "😾"
            "❤️+🔥" -> "❤️‍🔥"
            "❤️+🩹" -> "❤️‍🩹"
            "❤️+😭" -> "💔"
            "❤️+✨" -> "💖"
            "❤️+🥰" -> "🥰"
            "❤️+😍" -> "😍"
            "❤️+💘" -> "💘"
            "😂+🔥" -> "🔥"
            "✨+⭐" -> "🌟"
            "🤩+⭐" -> "🌟"
            "☕+❤️" -> "☕"
            "🐶+🥳" -> "🐶"
            "🍕+❤️" -> "🍕"
            "🍔+👑" -> "🍔"
            "😴+💤" -> "💤"
            "🥳+🎉" -> "🎉"
            "😇+🕊️" -> "🕊️"
            "🤠+🐎" -> "🤠"
            else -> null
        }
    }
}
