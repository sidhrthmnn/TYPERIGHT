package com.aistudio.typeright.util

/**
 * QWERTY proximity map for typo detection
 */
object QwertyProximityMap {
    private val proximityMap = mapOf(
        'a' to "qwsz",
        'b' to "vghn",
        'c' to "xdfv",
        'd' to "serfcx",
        'e' to "wrdsf",
        'f' to "drgcvx",
        'g' to "fthbvcd",
        'h' to "gyjnbv",
        'i' to "uokl",
        'j' to "hyumnb",
        'k' to "ijolm",
        'l' to "kop",
        'm' to "njkl",
        'n' to "bhjmk",
        'o' to "ipkl",
        'p' to "olm",
        'q' to "wsa",
        'r' to "etdfg",
        's' to "aqwedxz",
        't' to "rygfh",
        'u' to "iyhjk",
        'v' to "cfgb",
        'w' to "qase",
        'x' to "sdfcz",
        'y' to "tuhgj",
        'z' to "asxd"
    )
    
    /**
     * Get nearby characters on QWERTY keyboard
     */
    fun getProximity(char: Char): String {
        return proximityMap[char.lowercaseChar()] ?: ""
    }
    
    /**
     * Check if two characters are adjacent on keyboard
     */
    fun areAdjacent(char1: Char, char2: Char): Boolean {
        return getProximity(char1).contains(char2.lowercaseChar())
    }
}
