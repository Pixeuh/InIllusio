package fr.inillusio.ui.illustration

import android.content.Context

data class DisciplinePalette(val primary: Int, val secondary: Int)

/** Reads the same palette stylesheet used by the local graphic editor. */
object DisciplinePalettes {
    private const val ASSET_NAME = "discipline-palettes.css"
    private val variable = Regex("--discipline-([a-z0-9_]+)-(primary|secondary)\\s*:\\s*#([0-9A-Fa-f]{6})\\s*;")
    private val safeFallback = DisciplinePalette(0xFF3D4548.toInt(), 0xFF727B7E.toInt())

    @Volatile private var cached: Map<String, DisciplinePalette>? = null

    fun forCategory(context: Context, categoryId: String): DisciplinePalette {
        val palettes = cached ?: synchronized(this) {
            cached ?: runCatching {
                context.applicationContext.assets.open(ASSET_NAME).bufferedReader().use { parseCss(it.readText()) }
            }.getOrDefault(emptyMap()).also { cached = it }
        }
        return palettes[categoryId] ?: palettes[CategoryIllustrationRenderer.SOCIOLOGY] ?: safeFallback
    }

    internal fun parseCss(css: String): Map<String, DisciplinePalette> {
        val values = mutableMapOf<String, MutableMap<String, Int>>()
        variable.findAll(css).forEach { match ->
            val category = match.groupValues[1]
            val role = match.groupValues[2]
            val argb = ("FF" + match.groupValues[3]).toLong(16).toInt()
            values.getOrPut(category) { mutableMapOf() }[role] = argb
        }
        return values.mapNotNull { (category, colors) ->
            val primary = colors["primary"]
            val secondary = colors["secondary"]
            if (primary != null && secondary != null) category to DisciplinePalette(primary, secondary) else null
        }.toMap()
    }
}
