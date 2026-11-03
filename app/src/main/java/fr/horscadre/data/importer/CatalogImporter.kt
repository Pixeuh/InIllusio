package fr.horscadre.data.importer

import android.content.Context
import androidx.room.withTransaction
import fr.horscadre.data.local.HorsCadreDatabase
import fr.horscadre.data.local.entities.*
import org.json.JSONObject

class CatalogImporter(private val context: Context, private val database: HorsCadreDatabase) {
    suspend fun importBundledCatalog() {
        val json = context.assets.open("faits_sciences_humaines_v1_16.json").bufferedReader().use { it.readText() }
        importJson(json)
    }

    /** Parses the entire catalogue before opening a transaction, so invalid JSON cannot partially import. */
    suspend fun importJson(json: String) {
        val root = JSONObject(json)
        val editorialStatus = root.optString("statut", "corpus_editorial")
        val categories = root.getJSONArray("categories").let { array ->
            (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                CategoryEntity(value.getString("id"), value.getString("nom"))
            }
        }
        val facts = root.getJSONArray("faits").let { array ->
            (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                val id = value.getString("id")
                val source = if (value.isNull("source")) null else value.getJSONObject("source").let { jsonSource ->
                    ParsedSource(
                        entity = SourceEntity(
                            id = "$id-source-1",
                            type = jsonSource.optString("type", "inconnu"),
                            title = jsonSource.optString("titre", "Référence"),
                            year = jsonSource.intOrNull("annee"),
                            // URLs are no longer part of the editorial contract. Clear legacy values on re-import.
                            url = null,
                            bibliographicDetails = null
                        ),
                        author = jsonSource.optString("auteurs").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
                ParsedFact(
                    anecdote = AnecdoteEntity(
                        id = id,
                        categoryId = value.getString("categorie"),
                        title = value.getString("titre"),
                        hook = value.getString("accroche"),
                        explanation = value.getString("explication"),
                        editorialStatus = editorialStatus
                    ),
                    source = source
                )
            }
        }

        val dao = database.catalogDao()
        database.withTransaction {
            dao.archiveAllAnecdotes()
            dao.mergeDuplicateAuthorLinks()
            dao.deleteDuplicateAuthorLinks()
            dao.deleteDuplicateAuthors()
            categories.forEach { dao.putCategory(it) }
            facts.forEach { fact ->
                dao.putAnecdote(fact.anecdote)
                dao.clearAnecdoteSources(fact.anecdote.id)
                fact.source?.let { source ->
                    dao.putSource(source.entity)
                    dao.clearSourceAuthors(source.entity.id)
                    source.author?.let { name ->
                        val authorId = dao.authorId(name) ?: run {
                            dao.putAuthor(AuthorEntity(name = name))
                            checkNotNull(dao.authorId(name)) { "Impossible de résoudre l’auteur « $name »." }
                        }
                        dao.putSourceAuthor(SourceAuthorEntity(source.entity.id, authorId))
                    }
                    dao.putAnecdoteSource(AnecdoteSourceEntity(fact.anecdote.id, source.entity.id))
                }
            }
            dao.clearArchivedUncollectedSources()
            dao.deleteUnreferencedSources()
            dao.deleteUnreferencedAuthors()
        }
    }

    private data class ParsedFact(val anecdote: AnecdoteEntity, val source: ParsedSource?)
    private data class ParsedSource(val entity: SourceEntity, val author: String?)
}

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key).takeIf { it != 0 }
