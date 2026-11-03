const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function createEditorRuntime() {
  const savedBlobs = [];
  const clickedLinks = [];
  const window = {};
  const context = {
    window,
    Blob: class FakeBlob {
      constructor(parts, options) { this.parts = parts; this.type = options.type; }
    },
    URL: {
      createObjectURL(blob) { savedBlobs.push(blob); return `blob:test-${savedBlobs.length}`; },
      revokeObjectURL() {},
    },
    document: {
      createElement(tag) {
        assert.equal(tag, 'a');
        return { click() { clickedLinks.push(this); } };
      },
    },
    setTimeout,
  };
  window.setTimeout = setTimeout;
  for (const filename of ['data.js', 'validation.js', 'io.js']) {
    const source = fs.readFileSync(path.join(__dirname, '..', 'js', filename), 'utf8');
    vm.runInNewContext(source, context, { filename });
  }
  return { window, savedBlobs, clickedLinks };
}

function fakeCatalog() {
  return {
    version: 'test',
    categories: [{ id: 'sociologie', nom: 'Sociologie' }],
    faits: [{
      id: 'fiction-01', categorie: 'sociologie', titre: 'Une anecdote fictive',
      accroche: 'Une accroche avec des accents : déjà, forêt, cœur.',
      explication: 'Une explication isolée du catalogue de production.',
      statut_editorial: 'a_valider',
      source: { type: 'article', auteurs: 'Camille Étoile', titre: 'Revue imaginaire', annee: 2026 },
    }],
  };
}

test('new editorial entry starts pending and validation accepts its individual status', () => {
  const { window } = createEditorRuntime();
  const catalog = fakeCatalog();
  const added = window.EditorData.createFact(catalog);
  assert.equal(added.statut_editorial, 'a_valider');
  added.titre = 'Titre';
  added.accroche = 'Accroche';
  added.explication = 'Explication';
  added.source.auteurs = 'Camille Étoile';
  added.source.titre = 'Revue imaginaire';
  assert.deepEqual(Array.from(window.EditorValidation.validateCatalog({
    categories: catalog.categories,
    faits: [{ ...added, id: 'fiction-02' }],
  })), []);
  added.statut_editorial = 'validee';
  assert.equal(added.statut_editorial, 'validee');
});

test('UTF-8 export and re-import preserve facts, references, and status', async () => {
  const { window, savedBlobs, clickedLinks } = createEditorRuntime();
  const catalog = window.EditorData.normalizeCatalog({ ...fakeCatalog(), statut: 'legacy-global' });
  catalog.faits[0].statut_editorial = 'validee';
  let directlyWritten = '';
  const handle = {
    async createWritable() {
      return {
        async write(value) { directlyWritten = value; },
        async close() {},
      };
    },
  };
  assert.equal(await window.EditorIO.save(catalog, 'catalogue-test.json', handle), 'direct');
  assert.equal(JSON.parse(directlyWritten).faits[0].statut_editorial, 'validee');

  window.EditorIO.downloadJson(catalog, 'catalogue-test.json');
  assert.equal(clickedLinks.length, 1);
  assert.equal(clickedLinks[0].download, 'catalogue-test.json');
  const serialized = savedBlobs[0].parts.join('');
  const restored = JSON.parse(serialized);
  assert.equal(restored.statut, undefined);
  assert.equal(restored.faits[0].statut_editorial, 'validee');
  assert.equal(restored.faits[0].accroche, 'Une accroche avec des accents : déjà, forêt, cœur.');
  assert.equal(restored.faits[0].source.auteurs, 'Camille Étoile');
  assert.equal(JSON.stringify(restored), JSON.stringify(catalog));

  window.showOpenFilePicker = async () => [{
    getFile: async () => ({ name: 'catalogue-test.json', text: async () => serialized }),
  }];
  let imported;
  await window.EditorIO.openFile((value, name) => { imported = { value, name }; }, null);
  assert.equal(imported.name, 'catalogue-test.json');
  assert.equal(JSON.stringify(imported.value), JSON.stringify(restored));
});

test('missing status is normalized to pending and legacy URLs are removed before export', () => {
  const { window } = createEditorRuntime();
  const source = fakeCatalog();
  delete source.faits[0].statut_editorial;
  source.faits[0].source.url = 'https://example.invalid/legacy';
  source.faits[0].source.type = 'lien';
  const normalized = window.EditorData.normalizeCatalog(source);
  assert.equal(normalized.faits[0].statut_editorial, 'a_valider');
  assert.equal(normalized.faits[0].source.url, undefined);
  assert.equal(normalized.faits[0].source.type, 'autre');
  assert.equal(window.EditorValidation.validateCatalog(normalized).length, 0);
});
