suspend fun process(
    text: String,
    mode: PolishMode = PolishMode.PROOFREAD,
    context: TextContext = TextContext(mode = mode),
    confidenceThreshold: Float = DEFAULT_LOCAL_CONFIDENCE_THRESHOLD
): AiResult = withContext(Dispatchers.Default) {
    // ...
