# Base de données et import

Room utilise `hors-cadre.db`, au schéma version 2. Toutes les tables sont locales et l’import du catalogue est transactionnel. La migration 1→2 ajoute le marqueur d’archivage et fusionne les auteurs de même nom en conservant leurs associations.

Le namespace Kotlin et l’identifiant Android sont `fr.inillusio`. Les noms persistants historiques (`hors-cadre.db`, `hors_cadre_preferences` et `hors_cadre_developer`) restent intentionnellement inchangés pour ne pas confondre un renommage de code avec une migration de données. Comme `applicationId` devient `fr.inillusio`, Android isole toutefois cette installation de l’ancienne application `fr.horscadre` : ces fichiers sont créés dans le nouvel espace privé et ne récupèrent pas les données de l’ancienne installation. Aucun export/import de collection ou de préférences n’est actuellement fourni.

| Table | Rôle et relations |
|---|---|
| `categories` | Identifiant de discipline et nom. |
| `anecdotes` | ID stable, catégorie, titre, accroche, explication, statut éditorial, référence d’illustration optionnelle et état archivé. FK vers `categories`. |
| `authors` | Identifiant numérique et nom d’auteur. |
| `sources` | Type de publication (`livre`, `article`, `rapport` ou `autre`), titre, année, colonne URL historique nullable et détails bibliographiques facultatifs. L’interface ne lit pas l’URL et l’import la remet à `null`. |
| `source_authors` | Association plusieurs-à-plusieurs source/auteur, clé composée. |
| `anecdote_sources` | Association plusieurs-à-plusieurs anecdote/source, clé composée. |
| `illustrations` | Métadonnées de média, séparées du contenu d’anecdote. |
| `delivery_history` | Anecdote, date de tentative, résultat et erreur facultative ; index par anecdote et date. Non visible dans l’UI. |
| `collection` | Une ligne par anecdote dont l’application au verrouillage a réussi, horodatage de première découverte et FK catalogue. |

L’import lit `faits_sciences_humaines_v1_16.json` depuis le dossier `data/`, configuré directement comme source d’assets. Le JSON racine est l’unique source éditoriale. Il est entièrement analysé avant toute écriture, puis synchronisé dans une transaction. Les IDs de sources sont déterministes (`<id-anecdote>-source-1`). Les auteurs sont réutilisés par nom et les relations de doublons historiques sont fusionnées. Les anecdotes absentes du JSON sont archivées : elles ne peuvent plus être sélectionnées pour une nouvelle diffusion, mais restent accessibles si elles sont déjà collectionnées. Leurs sources ne sont conservées que pour les cartes collectionnées. Les erreurs de lecture ou d’import laissent la transaction précédente intacte ; l’interface affiche l’erreur et permet de réessayer. Le catalogue actuel n’a qu’une source par fait, mais les tables de jonction permettent plusieurs sources et auteurs. Le JSON ne contient plus de champ URL ; la colonne historique reste au schéma version 2 pour éviter une migration et n’est pas projetée dans les requêtes d’affichage.

Toute modification de schéma doit incrémenter `@Database(version = ...)`, ajouter une migration Room explicite, conserver les entrées de collection et archiver le nouveau schéma sous `app/schemas/`. Ne pas utiliser `fallbackToDestructiveMigration`.

L’import fourni résout les auteurs sous forme de chaîne unique telle que présente dans le JSON. Si le corpus évolue vers plusieurs auteurs structurés, migrer le format JSON au préalable plutôt que de deviner le découpage de noms.
## Statut de validation éditoriale

La colonne existante `anecdotes.editorialStatus` conserve le statut individuel `a_valider` ou `validee`. Le JSON porte ce statut sur chaque anecdote. Les entrées sans statut dans un ancien catalogue sont importées comme `a_valider` ; toute autre valeur inconnue est rejetée avant la transaction. La sélection quotidienne ne consulte que les lignes validées et non archivées. La requête de collection reste indépendante du statut : changer un statut ne supprime ni ne masque une carte déjà collectionnée. Le schéma reste en version 2, sans migration.
