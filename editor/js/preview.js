(function () {
  const glyphs = {
    sociologie: '◉', anthropologie: '⌘', psychologie_sociale: '◎', linguistique: 'Aa',
    histoire: '◷', economie: '↗', philosophie: '◇', geographie_humaine: '⌖',
  };

  function render(fact, category) {
    const safeFact = fact || {};
    const safeCategory = category || {};
    document.getElementById('preview-category').textContent = (safeCategory.nom || 'Catégorie').toLocaleUpperCase('fr');
    document.getElementById('art-category').textContent = (safeCategory.nom || 'Sciences humaines').toLocaleUpperCase('fr');
    document.getElementById('art-glyph').textContent = glyphs[safeCategory.id] || '✳';
    document.getElementById('preview-title').textContent = safeFact.titre || 'Votre titre apparaîtra ici';
    document.getElementById('preview-hook').textContent = safeFact.accroche || 'L’accroche de votre anecdote s’affichera ici au fil de votre saisie.';
    document.getElementById('preview-explanation').textContent = safeFact.explication || 'L’explication scientifique complète apparaîtra ici.';
    document.getElementById('preview-citation-text').textContent = window.EditorData.shortReference(safeFact.source);
    const palette = window.HorsCadrePalettes.forCategory(safeCategory.id);
    document.getElementById('preview-shell').style.setProperty('--category-primary', palette.primary);
    document.getElementById('preview-shell').style.setProperty('--category-secondary', palette.secondary);
  }

  window.EditorPreview = { render };
})();
