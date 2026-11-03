# Éditeur du catalogue Hors Cadre

Cet éditeur fonctionne localement dans Chrome ou Edge. Il n’utilise ni serveur, ni compte, ni dépendance externe.

## Ouvrir l’éditeur et le catalogue

1. Ouvrez `editor/index.html` dans Chrome ou Edge (double-clic dans l’Explorateur Windows).
2. Cliquez sur **Ouvrir une base JSON**.
3. Sélectionnez `data/faits_sciences_humaines_v1_16.json`.

Le catalogue fourni contient huit catégories et vingt-quatre anecdotes. Le navigateur conserve les données uniquement dans la page ouverte; aucune synchronisation n’a lieu.

## Ajouter une anecdote

Cliquez sur le bouton **+** au-dessus de la liste. Un identifiant technique disponible est attribué tout de suite. Complétez la catégorie, le titre, l’accroche, l’explication et la source. Les champs obligatoires sont signalés par un astérisque. L’identifiant d’une anecdote existante n’est jamais changé automatiquement.

## Modifier une anecdote et sa source

Sélectionnez une anecdote dans la liste puis éditez les champs. L’aperçu smartphone et son compteur d’accroche suivent la saisie. Utilisez le sélecteur Clair/Sombre pour contrôler le contraste. La longue accroche déclenche un avertissement, sans bloquer l’enregistrement.

Le format actuel accepte un objet `source` par anecdote. Le type de publication est choisi parmi `livre`, `article`, `rapport` et `autre`. Les auteurs, titres, années et propriétés bibliographiques complémentaires restent modifiables. Les anciennes URL sont supprimées au chargement et ne sont pas exportées ; l’ancien type `lien` est converti en `autre`. L’éditeur ne transforme pas la source en tableau, ce qui modifierait le contrat lu par l’importeur Android. Le champ racine `statut` décrit le catalogue dans son ensemble et ne valide pas les anecdotes individuellement.

## Enregistrer les modifications

Cliquez sur **Enregistrer**. Si le navigateur a ouvert le fichier avec son sélecteur de fichiers, il demande l’autorisation d’écriture et enregistre dans le fichier d’origine. Sinon, l’éditeur télécharge un nouveau JSON dans le dossier de téléchargements; le message de confirmation l’indique. La validation bloque l’écriture si des champs requis manquent, si un identifiant est dupliqué, si une catégorie n’existe pas ou si une source est mal formée.

La suppression demande une confirmation et ne touche au fichier qu’après **Enregistrer**. Une indication signale les changements non enregistrés; le navigateur prévient également avant la fermeture lorsque cette fonction est disponible.

## Réintégrer le catalogue dans Android

Si le navigateur a téléchargé le JSON, vérifiez le résultat puis remplacez `data/faits_sciences_humaines_v1_16.json` par le fichier exporté. La configuration Gradle actuelle utilise déjà le dossier `data/` comme source d’assets Android. Recompilez et réinstallez l’application : le catalogue est embarqué dans l’application, qui l’importe au démarrage; elle ne lit pas directement le fichier éditorial de votre ordinateur.

L’aperçu est une simulation graphique de téléphone et ne prétend pas reproduire exactement le rendu Compose natif.
## Validation éditoriale individuelle

Chaque anecdote possède une case **Anecdote validée**. Les nouvelles anecdotes commencent à `a_valider` ; une ancienne entrée sans statut est normalisée ainsi et doit être enregistrée dans le JSON. Le filtre de statut permet de consulter les anecdotes à valider ou validées. Le champ racine `statut` reste le statut général du catalogue et ne valide pas toutes les anecdotes. Android ne diffuse que les anecdotes marquées `validee` ; changer ce statut ne retire pas une carte déjà enregistrée dans la collection.
