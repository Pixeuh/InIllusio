(function () {
  function downloadJson(catalog, filename) {
    const blob = new Blob([JSON.stringify(catalog, null, 2) + '\n'], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename || 'faits_sciences_humaines.json';
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function openFile(onLoaded, fallbackInput) {
    if (!window.showOpenFilePicker) {
      fallbackInput.click();
      return;
    }
    try {
      const [handle] = await window.showOpenFilePicker({
        multiple: false,
        types: [{ description: 'Catalogue JSON', accept: { 'application/json': ['.json'] } }],
      });
      const file = await handle.getFile();
      const catalog = JSON.parse(await file.text());
      await onLoaded(catalog, file.name, handle);
    } catch (error) {
      if (error.name !== 'AbortError') throw error;
    }
  }

  async function save(catalog, filename, handle) {
    if (handle && typeof handle.createWritable === 'function') {
      const writable = await handle.createWritable();
      await writable.write(JSON.stringify(catalog, null, 2) + '\n');
      await writable.close();
      return 'direct';
    }
    downloadJson(catalog, filename);
    return 'download';
  }

  window.EditorIO = { openFile, save, downloadJson };
})();
