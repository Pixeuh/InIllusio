(function () {
  const text = (value) => typeof value === 'string' && value.trim().length > 0;
  const sourceTypes = new Set(['livre', 'article', 'rapport', 'autre']);

  function validateCatalog(catalog) {
    const errors = [];
    if (!catalog || typeof catalog !== 'object' || Array.isArray(catalog)) {
      return ['Le JSON doit contenir un objet à sa racine.'];
    }
    if (!Array.isArray(catalog.categories) || !Array.isArray(catalog.faits)) {
      return ['La structure attendue contient les tableaux « categories » et « faits ».'];
    }
    if (!text(catalog.statut)) errors.push('Le statut éditorial du catalogue est obligatoire.');

    const categoryIds = new Set();
    catalog.categories.forEach((category, index) => {
      if (!category || !text(category.id) || !text(category.nom)) {
        errors.push(`La catégorie ${index + 1} doit contenir un identifiant et un nom.`);
      } else if (categoryIds.has(category.id)) {
        errors.push(`Identifiant de catégorie dupliqué : ${category.id}.`);
      } else categoryIds.add(category.id);
    });

    const factIds = new Set();
    catalog.faits.forEach((fact, index) => {
      const label = `Anecdote ${index + 1}`;
      if (!fact || typeof fact !== 'object' || Array.isArray(fact)) {
        errors.push(`${label} n’est pas un objet valide.`);
        return;
      }
      if (!text(fact.id)) errors.push(`${label} : identifiant obligatoire.`);
      else if (factIds.has(fact.id)) errors.push(`${label} : identifiant dupliqué « ${fact.id} ».`);
      else factIds.add(fact.id);
      if (!categoryIds.has(fact.categorie)) errors.push(`${label} « ${fact.id || index + 1} » : catégorie inconnue.`);
      if (!text(fact.titre)) errors.push(`${label} « ${fact.id || index + 1} » : titre obligatoire.`);
      if (!text(fact.accroche)) errors.push(`${label} « ${fact.id || index + 1} » : accroche obligatoire.`);
      if (!text(fact.explication)) errors.push(`${label} « ${fact.id || index + 1} » : explication obligatoire.`);
      if (!fact.source || typeof fact.source !== 'object' || Array.isArray(fact.source)) {
        errors.push(`${label} « ${fact.id || index + 1} » : la source doit être un objet.`);
        return;
      }
      if (!sourceTypes.has(fact.source.type)) errors.push(`${label} « ${fact.id || index + 1} » : type de publication invalide (livre, article, rapport ou autre).`);
      if (!text(fact.source.auteurs)) errors.push(`${label} « ${fact.id || index + 1} » : auteur ou organisme obligatoire.`);
      if (!text(fact.source.titre)) errors.push(`${label} « ${fact.id || index + 1} » : titre de source obligatoire.`);
      if (fact.source.annee != null && fact.source.annee !== '' && (!Number.isInteger(Number(fact.source.annee)) || Number(fact.source.annee) < 0 || Number(fact.source.annee) > 3000)) {
        errors.push(`${label} « ${fact.id || index + 1} » : année de source invalide.`);
      }
    });
    return errors;
  }

  window.EditorValidation = { validateCatalog };
})();
