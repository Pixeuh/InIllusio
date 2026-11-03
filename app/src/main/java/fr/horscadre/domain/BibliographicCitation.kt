package fr.horscadre.domain

import fr.horscadre.data.local.dao.SourceDetails

/** Compact editorial citation used on the lock-screen poster. */
fun SourceDetails.shortCitation(): String = buildList {
    author?.trim()?.takeIf(String::isNotEmpty)?.let { add(shortAuthor(it)) }
    title.trim().takeIf(String::isNotEmpty)?.let { add(shortTitle(it)) }
    year?.let { add(it.toString()) }
}.joinToString(" · ")

private fun shortAuthor(value: String): String {
    val normalized = value.substringBefore(" — ").substringBefore(" – ").substringBefore(" (").trim()
    if (normalized in setOf("Banque de France", "Académie française", "Parc archéologique de Pompéi")) return normalized
    if (normalized == "IGN" || normalized.startsWith("IGN ")) return "IGN"
    val multiple = normalized.contains(" et al.")
    val firstAuthor = normalized.substringBefore(" et al.").substringBefore(" et ").substringBefore(',').trim()
    val surname = firstAuthor.split(Regex("\\s+")).lastOrNull().orEmpty()
    return if (multiple) "$surname et al." else surname.ifEmpty { normalized }
}

private fun shortTitle(value: String): String {
    val core = value.substringBefore(" — ").substringBefore(" – ").substringBefore(" —").substringBefore(" (")
        .substringBefore(". ").substringBefore(": ").trim()
    if (core.length <= 30) return core
    val boundary = core.lastIndexOf(' ', 27)
    return core.take(if (boundary >= 18) boundary else 27).trimEnd() + "…"
}
