(function () {
  const $ = (id) => document.getElementById(id);
  const state = { catalog: null, selectedIndex: -1, fileHandle: null, filename: 'faits_sciences_humaines.json', baseline: '', pageSize: 100, toastTimer: 0 };
  const knownSourceKeys = new Set(['type', 'auteurs', 'titre', 'url', 'annee']);

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
  }
  function normalize(value) { return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr'); }
  function getFact() { return state.catalog?.faits[state.selectedIndex] || null; }
  function categoryFor(fact) { return state.catalog?.categories.find((item) => item.id === fact?.categorie) || null; }
  function isDirty() { return !!state.catalog && JSON.stringify(state.catalog) !== state.baseline; }

  function notify(message, kind) {
    const toast = $('toast');
    toast.textContent = message;
    toast.classList.toggle('error', kind === 'error');
    toast.classList.add('show');
    clearTimeout(state.toastTimer);
    state.toastTimer = window.setTimeout(() => toast.classList.remove('show'), 3600);
  }

  function updateSaveState() {
    const dirty = isDirty();
    $('dirty-state').textContent = dirty ? 'Modifications non enregistrées' : (state.catalog ? 'Tout est enregistré' : 'Aucune base ouverte');
    $('dirty-state').classList.toggle('unsaved', dirty);
    $('save-button').disabled = !state.catalog || !dirty;
    $('catalog-name').textContent = state.catalog ? state.filename : 'Aucune base ouverte';
  }

  function updateCounts(filteredCount) {
    $('fact-count').textContent = state.catalog ? state.catalog.faits.length : '—';
    $('filtered-count').textContent = state.catalog && filteredCount !== state.catalog.faits.length ? `${filteredCount} affichée${filteredCount > 1 ? 's' : ''}` : '';
    if (state.catalog) {
      const validated = state.catalog.faits.filter((fact) => fact.statut_editorial === 'validee').length;
      $('editorial-counts').textContent = `${validated} validées · ${state.catalog.faits.length - validated} à valider`;
    }
  }

  function renderFilters() {
    const categories = $('category-filter');
    const formCategories = $('fact-category');
    categories.innerHTML = '<option value="">Toutes les catégories</option>';
    formCategories.innerHTML = '';
    for (const category of state.catalog.categories) {
      const option = `<option value="${escapeHtml(category.id)}">${escapeHtml(category.nom)}</option>`;
      categories.insertAdjacentHTML('beforeend', option);
      formCategories.insertAdjacentHTML('beforeend', option);
    }
    renderStatusFilter();
    categories.disabled = false;
    $('status-filter').disabled = false;
    $('add-button').disabled = false;
  }

  function renderStatusFilter() {
    const status = $('status-filter');
    const selected = status.value;
    status.innerHTML = '<option value="">Toutes les anecdotes</option><option value="a_valider">À valider</option><option value="validee">Validées</option>';
    status.value = ['a_valider', 'validee'].includes(selected) ? selected : '';
  }

  function visibleFactIndices() {
    const query = normalize($('search-input').value.trim());
    const categoryId = $('category-filter').value;
    const status = $('status-filter').value;
    return state.catalog.faits.map((fact, index) => ({ fact, index })).filter(({ fact }) => {
      const category = categoryFor(fact);
      const searchable = normalize([fact.titre, fact.accroche, fact.explication, fact.source?.auteurs, fact.source?.titre, category?.nom].join(' '));
      return (!query || searchable.includes(query)) && (!categoryId || fact.categorie === categoryId) && (!status || fact.statut_editorial === status);
    });
  }

  function renderList() {
    if (!state.catalog) return;
    const matches = visibleFactIndices();
    updateCounts(matches.length);
    const shown = matches.slice(0, state.pageSize);
    $('fact-list').innerHTML = shown.map(({ fact, index }) => {
      const category = categoryFor(fact);
      const color = window.InIllusioPalettes.forCategory(category?.id).primary;
      const selected = index === state.selectedIndex;
      const validated = fact.statut_editorial === 'validee';
      return `<button class="fact-row${selected ? ' selected' : ''}" type="button" role="option" aria-selected="${selected}" data-index="${index}"><span class="fact-row-top"><i class="category-dot" style="background:${color}"></i><span class="fact-category">${escapeHtml(category?.nom || 'Catégorie inconnue')}</span></span><span class="fact-row-title">${escapeHtml(fact.titre || 'Sans titre')}</span><span class="fact-row-status ${validated ? 'status-validated' : 'status-pending'}">${validated ? 'Validée' : 'À valider'}</span></button>`;
    }).join('');
    $('load-more').hidden = matches.length <= state.pageSize;
    $('load-more').textContent = `Afficher les ${Math.min(100, matches.length - state.pageSize)} suivantes`;
  }

  function fieldValue(id, value) { $(id).value = value == null ? '' : String(value); }

  function renderSourceExtras(source) {
    const container = $('source-extra-fields');
    const keys = Object.keys(source || {}).filter((key) => !knownSourceKeys.has(key));
    container.innerHTML = keys.map((key) => {
      const value = source[key];
      const type = value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value;
      const nested = type === 'object' || type === 'array';
      const control = nested
        ? `<textarea rows="3" data-extra-source="${escapeHtml(key)}" data-value-type="${type}" aria-label="${escapeHtml(key)}">${escapeHtml(JSON.stringify(value, null, 2))}</textarea>`
        : `<input data-extra-source="${escapeHtml(key)}" data-value-type="${type}" value="${escapeHtml(value)}" aria-label="${escapeHtml(key)}">`;
      return `<label class="field"><span>${escapeHtml(key)} <small>champ bibliographique conservé</small></span>${control}</label>`;
    }).join('');
  }

  function renderEditor() {
    const fact = getFact();
    const empty = !fact;
    $('empty-editor').hidden = !!state.catalog;
    $('edit-view').hidden = !state.catalog;
    if (empty) return;
    $('edit-kicker').textContent = state.selectedIndex < 0 ? 'NOUVELLE ANECDOTE' : 'ANECDOTE · ' + String(state.selectedIndex + 1).padStart(2, '0');
    $('edit-title').textContent = fact.titre || 'Nouvelle anecdote';
    $('fact-id').textContent = fact.id;
    $('fact-category').value = fact.categorie || '';
    fieldValue('fact-title', fact.titre);
    fieldValue('fact-hook', fact.accroche);
    fieldValue('fact-explanation', fact.explication);
    $('fact-validated').checked = fact.statut_editorial === 'validee';
    fieldValue('editorial-note', fact.note_editoriale);
    $('editorial-note-wrap').hidden = !Object.prototype.hasOwnProperty.call(fact, 'note_editoriale') && state.selectedIndex >= 0;
    if (state.selectedIndex < 0) $('editorial-note-wrap').hidden = false;
    const source = fact.source || {};
    fieldValue('source-type', source.type);
    fieldValue('source-authors', source.auteurs);
    fieldValue('source-title', source.titre);
    fieldValue('source-year', source.annee);
    renderSourceExtras(source);
    $('delete-button').hidden = state.selectedIndex < 0;
    $('hook-counter').textContent = `${(fact.accroche || '').length} caractères`;
    $('hook-warning').hidden = (fact.accroche || '').length < 150;
    $('validation-box').hidden = true;
    updatePreview();
  }

  function updatePreview() {
    const fact = getFact();
    window.EditorPreview.render(fact, categoryFor(fact));
  }

  function refreshAll() {
    renderFilters();
    renderList();
    renderEditor();
    updateSaveState();
  }

  function setLoadedCatalog(catalog, filename, handle) {
    if (!catalog || typeof catalog !== 'object' || Array.isArray(catalog) || !Array.isArray(catalog.categories) || !Array.isArray(catalog.faits)) {
      throw new Error('Structure JSON non reconnue : les tableaux « categories » et « faits » sont nécessaires.');
    }
    const originalCatalog = JSON.stringify(catalog);
    const missingEditorialStatuses = catalog.faits.filter((fact) => fact && typeof fact === 'object' && !Object.prototype.hasOwnProperty.call(fact, 'statut_editorial')).length;
    const hadGlobalStatus = Object.prototype.hasOwnProperty.call(catalog, 'statut');
    catalog = window.EditorData.normalizeCatalog(catalog);
    state.catalog = catalog;
    state.selectedIndex = catalog.faits.length ? 0 : -1;
    state.fileHandle = handle || null;
    state.filename = filename || 'faits_sciences_humaines.json';
    // Preserve the imported snapshot as baseline so normalization is offered for export.
    state.baseline = originalCatalog;
    state.pageSize = 100;
    refreshAll();
    const errors = window.EditorValidation.validateCatalog(catalog);
    if (missingEditorialStatuses) notify(`${missingEditorialStatuses} anecdote(s) sans statut ont été marquées « À valider ». Enregistrez la migration.`);
    else if (hadGlobalStatus) notify('Le statut global obsolète a été retiré. Enregistrez la migration.');
    else if (errors.length) notify(`Base chargée (${catalog.faits.length} anecdotes). ${errors.length} point(s) à corriger avant l’enregistrement.`, 'error');
    else notify(`${catalog.faits.length} anecdotes et ${catalog.categories.length} catégories chargées.`);
  }

  async function openWithPicker() {
    if (isDirty() && !window.confirm('Les modifications non enregistrées seront perdues. Ouvrir une autre base ?')) return;
    try {
      await window.EditorIO.openFile(setLoadedCatalog, $('file-input'));
    } catch (error) {
      notify(`Impossible d’ouvrir ce fichier : ${error.message}`, 'error');
    }
  }

  function showErrors(errors) {
    const box = $('validation-box');
    box.innerHTML = `<strong>Enregistrement bloqué : ${errors.length} point(s) à corriger.</strong><ul>${errors.slice(0, 12).map((error) => `<li>${escapeHtml(error)}</li>`).join('')}${errors.length > 12 ? `<li>… et ${errors.length - 12} autre(s).</li>` : ''}</ul>`;
    box.hidden = false;
    box.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  async function saveCatalog() {
    if (!state.catalog) return;
    const errors = window.EditorValidation.validateCatalog(state.catalog);
    if (errors.length) { showErrors(errors); notify('Corrigez les erreurs avant l’enregistrement.', 'error'); return; }
    try {
      const result = await window.EditorIO.save(state.catalog, state.filename, state.fileHandle);
      state.baseline = JSON.stringify(state.catalog);
      updateSaveState();
      notify(result === 'direct' ? 'Modifications enregistrées dans le fichier d’origine.' : 'JSON exporté dans votre dossier de téléchargements.');
    } catch (error) {
      notify(`Enregistrement impossible : ${error.message}`, 'error');
    }
  }

  function selectIndex(index) {
    state.selectedIndex = index;
    $('validation-box').hidden = true;
    renderList();
    renderEditor();
  }

  function addFact() {
    const fact = window.EditorData.createFact(state.catalog);
    state.catalog.faits.push(fact);
    selectIndex(state.catalog.faits.length - 1);
    $('fact-title').focus();
    updateSaveState();
    notify('Nouvelle anecdote créée. Complétez les champs obligatoires.');
  }

  function deleteFact() {
    const fact = getFact();
    if (!fact || !window.confirm(`Supprimer « ${fact.titre || 'cette anecdote'} » du catalogue ? Cette action sera appliquée au prochain enregistrement.`)) return;
    state.catalog.faits.splice(state.selectedIndex, 1);
    state.selectedIndex = state.catalog.faits.length ? Math.min(state.selectedIndex, state.catalog.faits.length - 1) : -1;
    refreshAll();
    notify('Anecdote supprimée du brouillon. Enregistrez pour confirmer la modification du fichier.');
  }

  function setField(target) {
    const fact = getFact();
    if (!fact) return;
    if (target.dataset.extraSource) {
      const key = target.dataset.extraSource;
      const type = target.dataset.valueType;
      try {
        if (type === 'object' || type === 'array') fact.source[key] = JSON.parse(target.value);
        else if (type === 'number') fact.source[key] = target.value === '' ? null : Number(target.value);
        else if (type === 'boolean') fact.source[key] = target.value === 'true';
        else fact.source[key] = target.value;
      } catch (_) {
        target.setCustomValidity('La valeur doit rester un JSON valide pour ce champ.');
        target.reportValidity();
        return;
      }
      target.setCustomValidity('');
    } else {
      const map = {
        'fact-category': ['categorie', target.value], 'fact-title': ['titre', target.value],
        'fact-hook': ['accroche', target.value], 'fact-explanation': ['explication', target.value],
        'source-type': ['source.type', target.value], 'source-authors': ['source.auteurs', target.value],
        'source-title': ['source.titre', target.value],
      };
      if (target.id === 'fact-validated') fact.statut_editorial = target.checked ? 'validee' : 'a_valider';
      else if (target.id === 'source-year') {
        if (!fact.source) fact.source = {};
        if (target.value === '') {
          if (Object.prototype.hasOwnProperty.call(fact.source, 'annee')) fact.source.annee = null;
        } else fact.source.annee = Number(target.value);
      } else if (target.id === 'editorial-note') {
        if (target.value || Object.prototype.hasOwnProperty.call(fact, 'note_editoriale')) fact.note_editoriale = target.value;
      } else if (map[target.id]) {
        const [key, value] = map[target.id];
        if (key.startsWith('source.')) {
          if (!fact.source) fact.source = {};
          const property = key.slice(7);
          if (value || Object.prototype.hasOwnProperty.call(fact.source, property)) fact.source[property] = value;
        } else fact[key] = value;
      }
    }
    $('edit-title').textContent = fact.titre || 'Nouvelle anecdote';
    $('hook-counter').textContent = `${(fact.accroche || '').length} caractères`;
    $('hook-warning').hidden = (fact.accroche || '').length < 150;
    updatePreview();
    if (target.id === 'fact-title' || target.id === 'fact-category' || target.id === 'fact-validated' || target.dataset.extraSource) renderList();
    updateSaveState();
  }

  function setTheme(theme) {
    const shell = $('preview-shell');
    shell.classList.toggle('theme-dark', theme === 'dark');
    shell.classList.toggle('theme-light', theme === 'light');
    $('theme-light').classList.toggle('active', theme === 'light');
    $('theme-dark').classList.toggle('active', theme === 'dark');
    $('theme-light').setAttribute('aria-pressed', String(theme === 'light'));
    $('theme-dark').setAttribute('aria-pressed', String(theme === 'dark'));
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('open-button').addEventListener('click', openWithPicker);
    document.querySelectorAll('[data-open]').forEach((button) => button.addEventListener('click', openWithPicker));
    $('file-input').addEventListener('change', async (event) => {
      const file = event.target.files?.[0];
      if (!file) return;
      try { setLoadedCatalog(JSON.parse(await file.text()), file.name, null); }
      catch (error) { notify(`Fichier JSON invalide : ${error.message}`, 'error'); }
      event.target.value = '';
    });
    $('save-button').addEventListener('click', saveCatalog);
    $('add-button').addEventListener('click', addFact);
    $('delete-button').addEventListener('click', deleteFact);
    $('fact-form').addEventListener('input', (event) => {
      if (!['object', 'array'].includes(event.target.dataset.valueType)) setField(event.target);
    });
    $('fact-form').addEventListener('change', (event) => {
      if (['object', 'array'].includes(event.target.dataset.valueType)) setField(event.target);
    });
    $('fact-list').addEventListener('click', (event) => {
      const row = event.target.closest('[data-index]');
      if (row) selectIndex(Number(row.dataset.index));
    });
    $('search-input').addEventListener('input', () => { state.pageSize = 100; renderList(); });
    $('category-filter').addEventListener('change', () => { state.pageSize = 100; renderList(); });
    $('status-filter').addEventListener('change', () => { state.pageSize = 100; renderList(); });
    $('load-more').addEventListener('click', () => { state.pageSize += 100; renderList(); });
    $('theme-light').addEventListener('click', () => setTheme('light'));
    $('theme-dark').addEventListener('click', () => setTheme('dark'));
    document.addEventListener('keydown', (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); $('search-input').focus(); }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') { event.preventDefault(); saveCatalog(); }
    });
    window.addEventListener('beforeunload', (event) => {
      if (isDirty()) { event.preventDefault(); event.returnValue = ''; }
    });
  });
})();
