window.HorsCadrePalettes = (() => {
  const styles = getComputedStyle(document.documentElement);

  function forCategory(categoryId) {
    const safeId = /^[a-z0-9_]+$/.test(categoryId || '') ? categoryId : 'sociologie';
    const read = (id, role) => styles.getPropertyValue(`--discipline-${id}-${role}`).trim();
    const fallback = { primary: read('sociologie', 'primary'), secondary: read('sociologie', 'secondary') };
    return {
      primary: read(safeId, 'primary') || fallback.primary,
      secondary: read(safeId, 'secondary') || fallback.secondary
    };
  }

  return { forCategory };
})();
