# Base de données et import

Room utilise `hors-cadre.db`, au schéma version 2. Toutes les tables sont locales et l’import du catalogue est transactionnel. La migration 1→2 ajoute le marqueur d’archivage et fusionne les auteurs de même nom en conservant leurs associations.

| Table | Rôle et relations |
|---|---|
| `categories` | Identifiant de discipline et nom. |
| `anecdotes` | ID stable, catégorie, titre, accroche, explication, statut éditorial, référence d’illustration optionnelle et état archivé. FK vers `categories`. |
| `authors` | Identifiant numérique et nom d’auteur. |
| `sources` | Type, titre, année, URL et détails bibliographiques facultatifs. |
| `source_authors` | Association plusieurs-à-plusieurs source/auteur, clé composée. |
| `anecdote_sources` | Association plusieurs-à-plusieurs anecdote/source, clé composée. |
| `illustrations` | Métadonnées de média, séparées du contenu d’anecdote. |
| `delivery_history` | Anecdote, date de tentative, résultat et erreur facultative ; index par anecdote et date. Non visible dans l’UI. |
| `collection` | Une ligne par anecdote volontairement enregistrée, horodatage et FK catalogue. |

L’import lit `faits_sciences_humaines_v1_16.json` depuis le dossier `data/`, configuré directement comme source d’assets. Le JSON racine est l’unique source éditoriale. Il est entièrement analysé avant toute écriture, puis synchronisé dans une transaction. Les IDs de sources sont déterministes (`<id-anecdote>-source-1`). Les auteurs sont réutilisés par nom et les relations de doublons historiques sont fusionnées. Les anecdotes absentes du JSON sont archivées : elles ne peuvent plus être sélectionnées pour une nouvelle diffusion, mais restent accessibles si elles sont déjà collectionnées. Leurs sources ne sont conservées que pour les cartes collectionnées. Les erreurs de lecture ou d’import laissent la transaction précédente intacte ; l’interface affiche l’erreur et permet de réessayer. Le catalogue actuel n’a qu’une source par fait, mais les tables de jonction permettent plusieurs sources et auteurs.

Toute modification de schéma doit incrémenter `@Database(version = ...)`, ajouter une migration Room explicite, conserver les entrées de collection et archiver le nouveau schéma sous `app/schemas/`. Ne pas utiliser `fallbackToDestructiveMigration`.

L’import fourni résout les auteurs sous forme de chaîne unique telle que présente dans le JSON. Si le corpus évolue vers plusieurs auteurs structurés, migrer le format JSON au préalable plutôt que de deviner le découpage de noms.
