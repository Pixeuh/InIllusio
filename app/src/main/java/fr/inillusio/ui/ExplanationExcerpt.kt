package fr.inillusio.ui

/** Returns a verbatim, bounded excerpt for the back of a collection card. */
internal fun explanationExcerpt(explanation: String, maxCharacters: Int = 240): String {
    if (maxCharacters <= 0) return ""
    if (explanation.length <= maxCharacters) return explanation

    val contentLimit = maxCharacters - 1
    val whitespace = explanation.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), contentLimit - 1)
    val end = if (whitespace > 0) whitespace else contentLimit
    return explanation.substring(0, end).trimEnd() + "…"
}
