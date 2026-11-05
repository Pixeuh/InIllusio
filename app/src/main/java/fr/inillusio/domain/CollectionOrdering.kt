package fr.inillusio.domain

import java.text.Collator
import java.util.Locale

enum class CollectionSortOrder(val label: String, val buttonLabel: String) {
    RECEIVED_NEWEST("Date de réception : récentes d’abord", "Récentes d’abord"),
    RECEIVED_OLDEST("Date de réception : anciennes d’abord", "Anciennes d’abord"),
    CATEGORY_A_TO_Z("Discipline : A → Z", "Discipline A → Z"),
    CATEGORY_Z_TO_A("Discipline : Z → A", "Discipline Z → A")
}

enum class CollectionSourceFilter(val label: String) {
    ALL("Toutes les cartes"), OFFICIAL("Officielles"), PERSONAL("Personnelles")
}

data class CollectionOrderKey(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val savedAt: Long,
    val isPersonal: Boolean = false
)

/** Filters and orders collection items without changing their stored acquisition timestamps. */
fun <T> filterAndSortCollection(
    items: List<T>,
    selectedCategoryIds: Set<String>?,
    order: CollectionSortOrder,
    sourceFilter: CollectionSourceFilter = CollectionSourceFilter.ALL,
    keyOf: (T) -> CollectionOrderKey
): List<T> {
    val collator = Collator.getInstance(Locale.forLanguageTag("fr-FR")).apply {
        strength = Collator.PRIMARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }
    val categoryOrder = Comparator<CollectionOrderKey> { left, right ->
        val byName = collator.compare(left.categoryName, right.categoryName)
        if (byName != 0) byName else left.categoryId.compareTo(right.categoryId)
    }
    val newestFirst = compareByDescending<CollectionOrderKey> { it.savedAt }.thenBy { it.id }
    val oldestFirst = compareBy<CollectionOrderKey> { it.savedAt }.thenBy { it.id }
    val comparator = when (order) {
        CollectionSortOrder.RECEIVED_NEWEST -> newestFirst
        CollectionSortOrder.RECEIVED_OLDEST -> oldestFirst
        CollectionSortOrder.CATEGORY_A_TO_Z -> categoryOrder.then(newestFirst)
        CollectionSortOrder.CATEGORY_Z_TO_A -> Comparator { left, right ->
            val byCategoryDescending = categoryOrder.compare(right, left)
            if (byCategoryDescending != 0) byCategoryDescending else newestFirst.compare(left, right)
        }
    }

    return items.asSequence()
        .map { it to keyOf(it) }
        .filter { (_, key) -> selectedCategoryIds == null || key.categoryId in selectedCategoryIds }
        .filter { (_, key) -> when (sourceFilter) {
            CollectionSourceFilter.ALL -> true
            CollectionSourceFilter.OFFICIAL -> !key.isPersonal
            CollectionSourceFilter.PERSONAL -> key.isPersonal
        } }
        .sortedWith { left, right -> comparator.compare(left.second, right.second) }
        .map { it.first }
        .toList()
}
