(function () {
  const copy = (value) => JSON.parse(JSON.stringify(value));

  function createId(catalog, categoryId) {
    const prefix = `${categoryId}-`;
    const used = new Set(catalog.faits.map((fact) => String(fact.id)));
    let next = catalog.faits.reduce((max, fact) => {
      const id = String(fact.id);
      const suffix = id.startsWith(prefix) ? id.slice(prefix.length) : '';
      return /^\d+$/.test(suffix) ? Math.max(max, Number(suffix)) : max;
    }, 0) + 1;
    let id = `${prefix}${String(next).padStart(2, '0')}`;
    while (used.has(id)) id = `${prefix}${String(++next).padStart(2, '0')}`;
    return id;
  }

  function createFact(catalog) {
    const categoryId = catalog.categories[0]?.id || '';
    return {
      id: createId(catalog, categoryId),
      categorie: categoryId,
      titre: '',
      accroche: '',
      explication: '',
      statut_editorial: 'a_valider',
      source: { type: 'article', auteurs: '', titre: '', annee: null },
    };
  }

  function normalizeCatalog(catalog) {
    const normalized = copy(catalog);
    delete normalized.statut;
    normalized.faits.forEach((fact) => {
      if (!fact || typeof fact !== 'object') return;
      // Legacy entries with no individual status are always marked pending.
      if (!Object.prototype.hasOwnProperty.call(fact, 'statut_editorial')) fact.statut_editorial = 'a_valider';
      if (!fact?.source || typeof fact.source !== 'object') return;
      delete fact.source.url;
      // The former "lien" value described access, not the kind of publication.
      if (fact.source.type === 'lien') fact.source.type = 'autre';
    });
    return normalized;
  }

  function shortReference(source) {
    if (!source) return 'Référence bibliographique';
    const authors = String(source.auteurs || '').trim();
    const title = String(source.titre || '').trim();
    const year = source.annee == null || source.annee === '' ? '' : ` (${source.annee})`;
    return [authors, title].filter(Boolean).join(' — ') + year || 'Référence bibliographique';
  }

  window.EditorData = { copy, createFact, normalizeCatalog, shortReference };
})();
