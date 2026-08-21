package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiFlags
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Data representation for an Emoji Category.
 */
data class EmojiCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val emojis: List<String>
)

/**
 * Data representation for an Emoji Kitchen mashup sticker.
 */
data class EmojiKitchenItem(
    val id: String,
    val primaryEmoji: String,
    val secondaryEmoji: String,
    val previewText: String,
    val description: String
)

/**
 * Bottom sub-tabs within the revamped emoji & media layout.
 */
enum class EmojiSubTab {
    EMOJI,
    GIF,
    STICKERS,
    KAOMOJI
}

/**
 * Revamped Gboard-style Emoji Screen recreating Google's modern layout:
 * 1. Top Bar: Back circular button, Search pill, and Category navigation icons.
 * 2. Emoji Kitchen Carousel: Interactive mashup cards with high-res rendering.
 * 3. Categorized Emoji Grid: Recent emoji section + comprehensive Google/Unicode emoji catalog (8 columns).
 * 4. Bottom Media Switcher Bar: ABC return button, [Emoji, GIF, Stickers, Kaomoji] segmented tabs, and Delete button.
 */
@Composable
fun RevampedEmojiLayout(
    keyColor: Color,
    textColor: Color,
    accentColor: Color,
    onKeyClick: (String) -> Unit,
    onEmojiToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentSubTab by remember { mutableStateOf(EmojiSubTab.EMOJI) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Persistent recent emojis initialized with the exact set from the reference image
    val recentEmojis = remember {
        mutableStateListOf(
            "👌", "🛑", "👋", "🤯", "⛅️", "😛", "📐", "🔨",
            "😂", "🙄", "😉", "🙃", "😳", "🏋️", "😮", "😇",
            "🌇", "🙂", "🥲", "☹️", "🤳", "👍", "✂️", "☀️"
        )
    }

    // Curated Google Emoji Kitchen Mashups
    val emojiKitchenItems = remember {
        listOf(
            EmojiKitchenItem("ek_heart_smile", "❤️", "🥰", "🥰", "Heart Smile"),
            EmojiKitchenItem("ek_wink_kiss", "😉", "😘", "😘", "Wink Kiss Heart"),
            EmojiKitchenItem("ek_sleep_teeth", "😴", "😬", "😬", "Sleeping Grit Teeth"),
            EmojiKitchenItem("ek_sparkle_heart", "💖", "✨", "💖", "Sparkling Heart Smile"),
            EmojiKitchenItem("ek_koala_think", "🐨", "🤔", "🐨", "Koala Thinking"),
            EmojiKitchenItem("ek_cat_cool", "🐱", "😎", "😎", "Cool Shades Kitty"),
            EmojiKitchenItem("ek_puppy_party", "🐶", "🥳", "🐶", "Party Hat Puppy"),
            EmojiKitchenItem("ek_fire_laugh", "🔥", "😂", "🔥", "Fiery Joy Laugh"),
            EmojiKitchenItem("ek_avocado_cry", "🥑", "🥺", "🥑", "Puppy Eyed Avocado"),
            EmojiKitchenItem("ek_ghost_sunglasses", "👻", "🕶️", "👻", "Cool Ghost")
        )
    }

    // Comprehensive Google / Unicode standard categorized emoji sets
    val allCategories = remember {
        listOf(
            EmojiCategory(
                id = "recent",
                name = "Recent emoji",
                icon = Icons.Default.Schedule,
                emojis = recentEmojis
            ),
            EmojiCategory(
                id = "smileys",
                name = "Smileys & Emotion",
                icon = Icons.Default.SentimentSatisfied,
                emojis = listOf(
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
                    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
                    "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🫢", "🫡",
                    "🤫", "🫠", "🤔", "🫣", "🤐", "🤨", "😐", "😑", "😶", "🫥",
                    "😶‍🌫️", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "🫨", "😌", "😔",
                    "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵",
                    "🥶", "🥴", "😵", "😵‍💫", "🤯", "🤠", "🥳", "🥸", "😎", "🤓",
                    "🧐", "😕", "🫤", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
                    "🥺", "🥹", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
                    "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
                    "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻",
                    "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽", "🙀"
                )
            ),
            EmojiCategory(
                id = "people",
                name = "People & Body",
                icon = Icons.Default.DirectionsRun,
                emojis = listOf(
                    "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌",
                    "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜",
                    "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
                    "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀",
                    "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄", "🫦", "💋", "🩸",
                    "👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👩‍🦱", "🧑‍🦱", "👨‍🦱",
                    "👩‍🦰", "🧑‍🦰", "👨‍🦰", "👱‍♀️", "👱", "👱‍♂️", "👩‍🦳", "🧑‍🦳", "👨‍🦳", "👩‍🦲",
                    "🧑‍🦲", "👨‍🦲", "👵", "🧓", "👴", "👲", "👳‍♀️", "👳", "👳‍♂️", "🧕",
                    "👮‍♀️", "👮", "👮‍♂️", "👷‍♀️", "👷", "👷‍♂️", "💂‍♀️", "💂", "💂‍♂️", "🕵️‍♀️",
                    "🕵️", "🕵️‍♂️", "👩‍⚕️", "🧑‍⚕️", "👨‍⚕️", "👩‍🌾", "🧑‍🌾", "👨‍🌾", "👩‍🍳", "🧑‍🍳",
                    "👨‍🍳", "👩‍🎓", "🧑‍🎓", "👨‍🎓", "👩‍🎤", "🧑‍🎤", "👨‍🎤", "👩‍🏫", "🧑‍🏫", "👨‍🏫",
                    "👩‍🏭", "🧑‍🏭", "👨‍🏭", "👩‍💻", "🧑‍💻", "👨‍💻", "👩‍💼", "🧑‍💼", "👨‍💼", "👩‍🔧",
                    "🧑‍🔧", "👨‍🔧", "👩‍🔬", "🧑‍🔬", "👨‍🔬", "👩‍🎨", "🧑‍🎨", "👨‍🎨", "👩‍🚒", "🧑‍🚒",
                    "👨‍🚒", "👩‍✈️", "🧑‍✈️", "👨‍✈️", "👩‍🚀", "🧑‍🚀", "👨‍🚀", "👩‍⚖️", "🧑‍⚖️", "👨‍⚖️",
                    "👰‍♀️", "👰", "👰‍♂️", "🤵‍♀️", "🤵", "🤵‍♂️", "👸", "🤴", "🥷", "🦸‍♀️",
                    "🦸", "🦸‍♂️", "🦹‍♀️", "🦹", "🦹‍♂️", "🤶", "🧑‍🎄", "🎅", "🧙‍♀️", "🧙",
                    "🧙‍♂️", "🧝‍♀️", "🧝", "🧝‍♂️", "🧛‍♀️", "🧛", "🧛‍♂️", "🧟‍♀️", "🧟", "🧟‍♂️",
                    "🧞‍♀️", "🧞", "🧞‍♂️", "🧜‍♀️", "🧜", "🧜‍♂️", "🧚‍♀️", "🧚", "🧚‍♂️", "👼",
                    "🤰", "🫄", "🤱", "👩‍🍼", "🧑‍🍼", "👨‍🍼", "🙇‍♀️", "🙇", "🙇‍♂️", "💁‍♀️",
                    "💁", "💁‍♂️", "🙅‍♀️", "🙅", "🙅‍♂️", "🙆‍♀️", "🙆", "🙆‍♂️", "🙋‍♀️", "🙋",
                    "🙋‍♂️", "🧏‍♀️", "🧏", "🧏‍♂️", "🤦‍♀️", "🤦", "🤦‍♂️", "🤷‍♀️", "🤷", "🤷‍♂️",
                    "🙎‍♀️", "🙎", "🙎‍♂️", "🙍‍♀️", "🙍", "🙍‍♂️", "💇‍♀️", "💇", "💇‍♂️", "💆‍♀️",
                    "💆", "💆‍♂️", "🧖‍♀️", "🧖", "🧖‍♂️", "💅", "🤳", "💃", "🕺", "👯‍♀️",
                    "👯", "👯‍♂️", "🚶‍♀️", "🚶", "🚶‍♂️", "🏃‍♀️", "🏃", "🏃‍♂️", "🏋️‍♀️", "🏋️"
                )
            ),
            EmojiCategory(
                id = "animals",
                name = "Animals & Nature",
                icon = Icons.Default.Pets,
                emojis = listOf(
                    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
                    "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
                    "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
                    "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
                    "🐞", "🐜", "🪰", "🪲", "🪳", "🦂", "🕷️", "🕸️", "🐢", "🐍",
                    "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
                    "🐟", "🐬", "🐳", "🐋", "🦈", "🦭", "🐊", "🐅", "🐆", "🦓",
                    "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘",
                    "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐",
                    "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛", "🪶", "🐓", "🦃",
                    "🦤", "🦚", "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡",
                    "🦫", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔", "🐾", "🐉", "🐲",
                    "🌵", "🎄", "🌲", "🌳", "🌴", "🪵", "🌱", "🌿", "☘️", "🍀",
                    "🎍", "🪴", "🎋", "🍃", "🍂", "🍁", "🍄", "🐚", "🪨", "🌾",
                    "💐", "🌷", "🌹", "🥀", "🪷", "🌺", "🌸", "🌼", "🌻", "☀️",
                    "🌤️", "⛅️", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️",
                    "☃️", "⛄️", "🌬️", "💨", "🌪️", "🌫️", "🌈", "🔥", "💧", "🌊"
                )
            ),
            EmojiCategory(
                id = "food",
                name = "Food & Drink",
                icon = Icons.Default.LocalCafe,
                emojis = listOf(
                    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
                    "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
                    "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅",
                    "🥔", "🍠", "🫘", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚",
                    "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭",
                    "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔",
                    "🥗", "🥘", "🫕", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱",
                    "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍢", "🥠", "🥮", "🍧",
                    "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫",
                    "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼", "🫖", "☕️",
                    "🍵", "🧃", "🥤", "🧋", "🫗", "🍶", "🍾", "🍷", "🍸", "🍹",
                    "🍺", "🍻", "🥂", "🥃", "🫗", "🥤", "🧊", "🥢", "🍽️", "🍴"
                )
            ),
            EmojiCategory(
                id = "travel",
                name = "Travel & Places",
                icon = Icons.Default.DirectionsCar,
                emojis = listOf(
                    "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
                    "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛺", "🚲", "🛴", "🛹",
                    "🛼", "🚏", "🛣️", "🛤️", "⛽️", "🛞", "🚨", "🚥", "🚦", "🚧",
                    "⚓️", "⛵️", "🛶", "🚤", "🛳️", "⛴️", "🛥️", "🚢", "✈️", "🛩️",
                    "🛫", "🛬", "🪂", "💺", "🚁", "🚟", "🚠", "🚡", "🛰️", "🚀",
                    "🛸", "🪐", "🌠", "🌌", "🏖️", "🏝️", "🏜️", "🏕️", "🏞️", "🏟️",
                    "🏛️", "🏗️", "🧱", "🏘️", "🏚️", "🏠", "🏡", "🏢", "🏣", "🏤",
                    "🏥", "🏦", "🏨", "🏩", "🏪", "🏫", "🏬", "🏭", "🏯", "🏰",
                    "💒", "🗼", "🗽", "⛪️", "🕌", "🛕", "🕍", "⛩️", "🕋", "⛲️",
                    "⛺️", "🌁", "🌃", "🏙️", "🌄", "🌅", "🌆", "🌇", "🌉", "♨️"
                )
            ),
            EmojiCategory(
                id = "activities",
                name = "Activities & Events",
                icon = Icons.Default.EmojiEvents,
                emojis = listOf(
                    "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                    "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳️",
                    "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷",
                    "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️‍♀️", "🏋️", "🏋️‍♂️", "🤼‍♀️",
                    "🤼", "🤼‍♂️", "🤸‍♀️", "🤸", "🤸‍♂️", "⛹️‍♀️", "⛹️", "⛹️‍♂️", "🤺", "🤾‍♀️",
                    "🤾", "🤾‍♂️", "🏌️‍♀️", "🏌️", "🏌️‍♂️", "🏇", "🧘‍♀️", "🧘", "🧘‍♂️", "🏄‍♀️",
                    "🏄", "🏄‍♂️", "🏊‍♀️", "🏊", "🏊‍♂️", "🤽‍♀️", "🤽", "🤽‍♂️", "🚣‍♀️", "🚣",
                    "🚣‍♂️", "🧗‍♀️", "🧗", "🧗‍♂️", "🚵‍♀️", "🚵", "🚵‍♂️", "🚴‍♀️", "🚴", "🚴‍♂️",
                    "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️",
                    "🎪", "🤹‍♀️", "🤹", "🤹‍♂️", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧",
                    "🎼", "🎹", "🥁", "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻",
                    "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩", "🎳", "🎉", "🎊"
                )
            ),
            EmojiCategory(
                id = "objects",
                name = "Objects & Tools",
                icon = Icons.Default.Lightbulb,
                emojis = listOf(
                    "📱", "📲", "☎️", "📞", "📟", "📠", "🔋", "🪫", "🔌", "💻",
                    "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💽", "💾", "💿", "📀", "🧮",
                    "🎥", "🎞️", "📽️", "🎬", "📺", "📷", "📸", "📹", "📼", "🔍",
                    "🔎", "🕯️", "💡", "🔦", "🏮", "🪔", "📔", "📕", "📖", "📗",
                    "📘", "📙", "📚", "📓", "📒", "📃", "📜", "📄", "📰", "🗞️",
                    "📑", "🔖", "🏷️", "💰", "🪙", "💴", "💵", "💶", "💷", "💸",
                    "💳", "🧾", "✉️", "📧", "📨", "📩", "📤", "📥", "📦", "📫",
                    "📪", "📬", "📭", "📮", "🗳️", "✏️", "✒️", "🖋️", "🖊️", "🖌️",
                    "🖍️", "📝", "💼", "📁", "📂", "🗂️", "📅", "📆", "🗒️", "🗓️",
                    "📇", "📈", "📉", "📊", "📋", "📌", "📍", "📎", "🖇️", "📏",
                    "📐", "✂️", "🗃️", "🗄️", "🗑️", "🔒", "🔓", "🔏", "🔐", "🔑",
                    "🗝️", "🔨", "🪓", "⛏️", "⚒️", "🛠️", "🗡️", "⚔️", "💣", "🪃",
                    "🏹", "🛡️", "🪚", "🔧", "🪛", "🔩", "⚙️", "🗜️", "⚖️", "🦯",
                    "🔗", "⛓️", "🪝", "🧰", "🧲", "🪜", "⚗️", "🧪", "🧫", "🧬",
                    "🔬", "🔭", "📡", "💉", "🩸", "💊", "🩹", "🩼", "🩺", "🩻",
                    "🚪", "🛗", "🪞", "🪟", "🛏️", "🛋️", "🪑", "🚽", "🪠", "🚿",
                    "🛁", "🪤", "🪒", "🧴", "🧷", "🧹", "🧺", "🧻", "🪣", "🧼",
                    "🫧", "🪥", "🧽", "🧯", "🛒", "🚬", "⚰️", "🪦", "⚱️", "🧿"
                )
            ),
            EmojiCategory(
                id = "symbols",
                name = "Symbols & Hearts",
                icon = Icons.Default.Favorite,
                emojis = listOf(
                    "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣️", "💔",
                    "❤️‍🔥", "❤️‍🩹", "❤️", "🩷", "🧡", "💛", "💚", "💙", "🩵", "💜",
                    "🤎", "🖤", "🩶", "🤍", "💯", "💢", "💥", "💫", "💦", "💨",
                    "🕳️", "💬", "👁️‍🗨️", "🗨️", "🗯️", "💭", "💤", "🌐", "♨️", "🛑",
                    "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗", "❕", "❓",
                    "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️",
                    "🔰", "♻️", "✅", "🈯️", "💹", "❇️", "✳️", "❎", "🌐", "💠",
                    "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿️", "🅿️", "🛗", "🈳", "🈂️",
                    "🛂", "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "⚧️", "🚻", "🚮",
                    "🎦", "📶", "🈹", "🈴", "🈺", "🉐", "🈹", "🈚️", "🈲", "🈸",
                    "🈴", "🈲", "㊗️", "㊙️", "🈑", "🈵", "🔴", "🟠", "🟡", "🟢",
                    "🔵", "🟣", "🟤", "⚫️", "⚪️", "🟥", "🟧", "🟨", "🟩", "🟦",
                    "🟪", "🟫", "⬛️", "⬜️", "◼️", "◻️", "◾️", "◽️", "▪️", "▫️",
                    "🔶", "🔷", "🔸", "🔹", "🔺", "🔻", "💠", "🔘", "🔳", "🔲"
                )
            ),
            EmojiCategory(
                id = "flags",
                name = "Flags",
                icon = Icons.Default.EmojiFlags,
                emojis = listOf(
                    "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇺🇸", "🇬🇧",
                    "🇨🇦", "🇦🇺", "🇩🇪", "🇫🇷", "🇯🇵", "🇮🇳", "🇮🇹", "🇪🇸", "🇧🇷", "🇲🇽",
                    "🇰🇷", "🇨🇳", "🇷🇺", "🇿🇦", "🇸🇬", "🇳🇿", "🇨🇭", "🇳🇱", "🇸🇪", "🇳🇴"
                )
            )
        )
    }

    // Curated Kaomoji sets for the `:-)` tab
    val kaomojiList = remember {
        listOf(
            "¯\\_(ツ)_/¯", "(｡♥‿♥｡)", "(╯°□°)╯︵ ┻━┻", "(•‿•)",
            "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ", "( ͡° ͜ʖ ͡°)", "(•_•)",
            "(>_<)", "(≧◡≦)", "(•ω•)", "(✿◠‿◠)",
            "(*^▽^*)", "(¬‿¬)", "(ง'̀-'́)ง", "(ಥ_ಥ)",
            "＼(＾O＾)／", "(•ิ_•ิ)", "(づ￣ ³￣)づ", "(•̀o•́)ง"
        )
    }

    val onEmojiTapped: (String) -> Unit = { emoji ->
        onKeyClick(emoji)
        // Dynamically update recent list and prevent duplicates
        if (!recentEmojis.contains(emoji)) {
            recentEmojis.add(0, emoji)
            if (recentEmojis.size > 32) recentEmojis.removeLast()
        } else {
            recentEmojis.remove(emoji)
            recentEmojis.add(0, emoji)
        }
    }

    // Filtered emojis for search query
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.lowercase().trim()
            allCategories.flatMap { it.emojis }.distinct().filter { emoji ->
                emoji.contains(q) || matchEmojiKeyword(emoji, q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // ==========================================
        // 1. TOP BAR (Back + Search + Category Row)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Back Arrow Button (matches screenshot)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.10f))
                    .clickable { onEmojiToggle() }
                    .testTag("emoji_top_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Keyboard",
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Pill-shaped Search Bar
            Box(
                modifier = Modifier
                    .weight(if (isSearchActive) 1f else 0.85f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(textColor.copy(alpha = 0.12f))
                    .clickable { isSearchActive = true }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = textColor.copy(alpha = 0.70f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isSearchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Search",
                            color = textColor.copy(alpha = 0.60f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Category Navigation Icons Row (Clock, Smile, People, Pets, Cup, Car...)
            if (!isSearchActive) {
                Spacer(modifier = Modifier.width(4.dp))
                LazyRow(
                    modifier = Modifier.weight(1.15f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(allCategories.size) { index ->
                        val category = allCategories[index]
                        val isSelected = index == selectedCategoryIndex && currentSubTab == EmojiSubTab.EMOJI
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) textColor.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    currentSubTab = EmojiSubTab.EMOJI
                                    selectedCategoryIndex = index
                                    isSearchActive = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.name,
                                tint = if (isSelected) textColor else textColor.copy(alpha = 0.65f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================
        // 2. MAIN CONTENT AREA (Emoji Kitchen, Grid, Search, or Tabs)
        // =========================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            when {
                // Search Active View
                isSearchActive && searchQuery.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Search Results (${searchResults.size})",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(searchResults) { emoji ->
                                EmojiGridTile(emoji = emoji, textColor = textColor, onKeyClick = onEmojiTapped)
                            }
                        }
                    }
                }

                // Kaomoji Tab
                currentSubTab == EmojiSubTab.KAOMOJI -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Emoticons & Kaomoji",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(kaomojiList) { kaomoji ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(keyColor)
                                        .clickable { onKeyClick(kaomoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = kaomoji,
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Stickers Tab
                currentSubTab == EmojiSubTab.STICKERS || currentSubTab == EmojiSubTab.GIF -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (currentSubTab == EmojiSubTab.GIF) "GIF Search & Clips" else "Sticker Packs & Expressive Art",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select any Emoji Kitchen mashup above or emoji below to insert expressive stickers directly into your conversation.",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Standard Google Emoji View (Emoji Kitchen + Categorized Grid)
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // A. EMOJI KITCHEN SECTION (Matches screenshot)
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Emoji Kitchen",
                                    color = textColor.copy(alpha = 0.70f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    emojiKitchenItems.forEach { item ->
                                        EmojiKitchenCard(
                                            item = item,
                                            keyColor = keyColor,
                                            textColor = textColor,
                                            onTap = {
                                                onEmojiTapped(item.previewText)
                                            }
                                        )
                                    }

                                    // Next arrow circular action button (matches screenshot)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(textColor.copy(alpha = 0.15f))
                                            .clickable {
                                                // Rotate/insert random kitchen mashup
                                                val next = emojiKitchenItems.random()
                                                onEmojiTapped(next.previewText)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "More Emoji Kitchen",
                                            tint = textColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }

                        // B. CURRENT / SELECTED CATEGORY EMOJI GRID
                        val currentCategory = allCategories.getOrNull(selectedCategoryIndex) ?: allCategories[0]

                        item {
                            Text(
                                text = currentCategory.name,
                                color = textColor.copy(alpha = 0.70f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }

                        // Chunked rows for seamless high-performance rendering in LazyColumn
                        val emojiRows = currentCategory.emojis.chunked(8)
                        items(emojiRows) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowItems.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clickable { onEmojiTapped(emoji) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 24.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                // Fill remaining spaces if row is incomplete
                                if (rowItems.size < 8) {
                                    repeat(8 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. BOTTOM BAR (ABC + [Emoji, GIF, Stickers, Kaomoji] Switcher + Backspace)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ABC Switcher (Pill button on left)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onEmojiToggle() }
                    .testTag("back_to_abc_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Segmented Media Switcher (Emoji, GIF, Stickers, Kaomoji)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Emoji Pill (Active state with pill container)
                MediaTabPill(
                    label = null,
                    icon = Icons.Default.EmojiEmotions,
                    isSelected = currentSubTab == EmojiSubTab.EMOJI,
                    textColor = textColor,
                    onClick = {
                        currentSubTab = EmojiSubTab.EMOJI
                        isSearchActive = false
                    }
                )

                // 2. GIF Pill
                MediaTabPill(
                    label = "GIF",
                    icon = null,
                    isSelected = currentSubTab == EmojiSubTab.GIF,
                    textColor = textColor,
                    onClick = {
                        currentSubTab = EmojiSubTab.GIF
                        isSearchActive = false
                    }
                )

                // 3. Stickers Pill
                MediaTabPill(
                    label = null,
                    icon = Icons.Default.StickyNote2,
                    isSelected = currentSubTab == EmojiSubTab.STICKERS,
                    textColor = textColor,
                    onClick = {
                        currentSubTab = EmojiSubTab.STICKERS
                        isSearchActive = false
                    }
                )

                // 4. Kaomoji Pill
                MediaTabPill(
                    label = ":-)",
                    icon = null,
                    isSelected = currentSubTab == EmojiSubTab.KAOMOJI,
                    textColor = textColor,
                    onClick = {
                        currentSubTab = EmojiSubTab.KAOMOJI
                        isSearchActive = false
                    }
                )
            }

            // Backspace / Delete Key (Matches Gboard right slot)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onDelete() }
                    .testTag("emoji_delete_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Delete",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Interactive Emoji Kitchen Card Component with animated press response.
 */
@Composable
private fun EmojiKitchenCard(
    item: EmojiKitchenItem,
    keyColor: Color,
    textColor: Color,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(textColor.copy(alpha = 0.08f))
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.previewText,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Individual Tile for Emoji Grid Search.
 */
@Composable
private fun EmojiGridTile(
    emoji: String,
    textColor: Color,
    onKeyClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onKeyClick(emoji) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Segmented Media Tab Pill for bottom navigation bar.
 */
@Composable
private fun MediaTabPill(
    label: String?,
    icon: ImageVector?,
    isSelected: Boolean,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (isSelected) textColor.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                color = if (isSelected) textColor else textColor.copy(alpha = 0.60f),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) textColor else textColor.copy(alpha = 0.60f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Helper to match emoji by semantic keyword.
 */
private fun matchEmojiKeyword(emoji: String, query: String): Boolean {
    val map = mapOf(
        "heart" to listOf("❤️", "💖", "💘", "💝", "💗", "💓", "💕", "❣️", "💔", "🥰"),
        "love" to listOf("❤️", "💖", "🥰", "😍", "😘", "💕"),
        "smile" to listOf("😀", "😃", "😄", "😁", "😊", "🙂", "☺️"),
        "happy" to listOf("😀", "😃", "😄", "😁", "😊", "🥳", "😇"),
        "laugh" to listOf("😂", "🤣", "😆", "😹"),
        "fire" to listOf("🔥", "❤️‍🔥", "💥"),
        "ok" to listOf("👌", "👍", "✅"),
        "hand" to listOf("👋", "🤚", "✋", "🖐️", "👌", "👍", "👎", "👏", "🙌", "🙏"),
        "cat" to listOf("🐱", "🐈", "🐈‍⬛", "😸", "😹", "😻", "😼", "😽", "🙀"),
        "dog" to listOf("🐶", "🐕", "🦮", "🐩"),
        "stop" to listOf("🛑", "⛔️", "🚫"),
        "cloud" to listOf("⛅️", "☁️", "🌤️", "🌥️", "🌧️"),
        "sun" to listOf("☀️", "🌞", "🌅", "🌄"),
        "food" to listOf("🍕", "🍔", "🍟", "🌭", "🍿", "🍩", "🍪", "🍫"),
        "coffee" to listOf("☕️", "🫖", "🥤", "🧋"),
        "car" to listOf("🚗", "🚕", "🚙", "🏎️", "🚓"),
        "star" to listOf("⭐", "🌟", "✨", "💫"),
        "party" to listOf("🥳", "🎉", "🎊", "🎈"),
        "cool" to listOf("😎", "🕶️"),
        "sad" to listOf("😢", "😭", "😔", "😟", "🙁", "☹️"),
        "angry" to listOf("😠", "😡", "🤬", "😤"),
        "pray" to listOf("🙏"),
        "check" to listOf("✅", "✔️"),
        "flag" to listOf("🏁", "🚩", "🎌", "🏴", "🏳️"),
        "music" to listOf("🎵", "🎶", "🎼", "🎹", "🎸")
    )
    for ((k, list) in map) {
        if (k.contains(query) && list.contains(emoji)) return true
    }
    return false
}
