# Base de données et import

Room utilise `inillusio.db`, au schéma version 3. Toutes les tables sont locales et l’import du catalogue est transactionnel. La migration 1→2 ajoute le marqueur d’archivage et fusionne les auteurs de même nom en conservant leurs associations. La migration non destructive 2→3 ajoute le registre d’idempotence des récompenses et laisse intactes les lignes existantes.

Le namespace et l’identifiant Android restent `fr.inillusio` ; BETA utilise son espace séparé `fr.inillusio.beta`. Les migrations de noms décrites ci-dessous s’exécutent dans le stockage privé de chaque installation. Android ne permet pas de reprendre automatiquement le stockage d’une application installée sous un autre identifiant ; ce projet ne fournit pas d’export/import de ces données entre packages.

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
| `collection` | Une ligne par anecdote ajoutée après application réussie au verrouillage ou récompense booster confirmée ; horodatage de première découverte et FK catalogue. |
| `booster_redemptions` | Clé unique par callback de récompense confirmé et horodatage ; empêche d’attribuer deux fois le même événement. |

La table `collection` peut être alimentée soit par une application `FLAG_LOCK` réussie, soit par un booster confirmé. Chaque voie utilise une insertion sans remplacement qui conserve `savedAt` lors d’une nouvelle découverte. Un booster n’écrit jamais dans `delivery_history`.

L’outil développeur DEBUG de réinitialisation supprime seulement les lignes de `collection`, dans une transaction. Il ne supprime ni les anecdotes, ni `delivery_history`, ni `booster_redemptions`, ni les préférences DataStore. Après effacement, les anecdotes validées sont à nouveau candidates aux nouveaux événements de récompense, tandis que les clés déjà utilisées continuent de protéger contre une attribution en double.

L’import lit `faits_sciences_humaines_v1_16.json` depuis le dossier `data/`, configuré directement comme source d’assets. Le JSON racine est l’unique source éditoriale. Il est entièrement analysé avant toute écriture, puis synchronisé dans une transaction. Les IDs de sources sont déterministes (`<id-anecdote>-source-1`). Les auteurs sont réutilisés par nom et les relations de doublons historiques sont fusionnées. Les anecdotes absentes du JSON sont archivées : elles ne peuvent plus être sélectionnées pour une nouvelle diffusion, mais restent accessibles si elles sont déjà collectionnées. Leurs sources ne sont conservées que pour les cartes collectionnées. Les erreurs de lecture ou d’import laissent la transaction précédente intacte ; l’interface affiche l’erreur et permet de réessayer. Le catalogue actuel n’a qu’une source par fait, mais les tables de jonction permettent plusieurs sources et auteurs. Le JSON ne contient plus de champ URL ; la colonne historique reste dans le schéma version 3 pour éviter une migration inutile et n’est pas projetée dans les requêtes d’affichage.

Toute modification de schéma doit incrémenter `@Database(version = ...)`, ajouter une migration Room explicite, conserver les entrées de collection et archiver le nouveau schéma sous `app/schemas/`. Ne pas utiliser `fallbackToDestructiveMigration`.

L’import fourni résout les auteurs sous forme de chaîne unique telle que présente dans le JSON. Si le corpus évolue vers plusieurs auteurs structurés, migrer le format JSON au préalable plutôt que de deviner le découpage de noms.
## Statut de validation éditoriale

La colonne existante `anecdotes.editorialStatus` conserve le statut individuel `a_valider` ou `validee`. Le JSON porte ce statut sur chaque anecdote. Les entrées sans statut dans un ancien catalogue sont importées comme `a_valider` ; toute autre valeur inconnue est rejetée avant la transaction. La sélection quotidienne et les boosters ne consultent que les lignes validées et non archivées. La requête de collection reste indépendante du statut : changer un statut ne supprime ni ne masque une carte déjà collectionnée. La version 3 ajoute `booster_redemptions` par migration non destructive 2→3.

## Migration des noms de stockage

| Ancien identifiant, conservé uniquement pour compatibilité | Identifiant actif |
|---|---|
| `hors-cadre.db` | `inillusio.db` |
| `hors_cadre_preferences` | `inillusio_preferences` |
| `hors_cadre_developer` (DEBUG) | `inillusio_developer` |
| `hors-cadre-daily-lock-wallpaper` | `inillusio_daily_lock_wallpaper` |

`DatabaseFileMigration.prepare` intervient au démarrage, avant l’ouverture de Room ou l’import. Quand seule l’ancienne base existe, elle effectue un checkpoint WAL complet, copie le fichier dans le même répertoire, synchronise l’écriture et vérifie `PRAGMA quick_check` avant de publier la copie par renommage. L’original reste disponible. Un checkpoint occupé, une copie incomplète ou une corruption interrompt la migration sans créer une base vide ; le gestionnaire SQLite de cette opération ne supprime jamais le fichier corrompu. Une copie temporaire interrompue est remplacée au prochain essai. Une base canonique déjà présente n’est jamais écrasée. Room applique ensuite normalement les migrations de schéma 1→2→3 ; aucun schéma ni ID d’anecdote ne change.

`PreferencesFileMigration` importe les valeurs de l’ancien DataStore dans le nouveau via l’API `DataMigration`. Toutes les clés sont conservées, y compris l’onboarding, le consentement, la carte du jour, les deux positions et la sauvegarde développeur. Les valeurs déjà présentes dans le nouveau fichier ont priorité. DataStore écrit atomiquement le résultat et son marqueur de migration ; ce marqueur évite de réimporter une sauvegarde développeur déjà restaurée et consommée. L’ancien fichier n’est pas supprimé. Une corruption remonte en erreur et ne provoque aucun remplacement silencieux par des valeurs par défaut.

WorkManager ne propose pas d’API publique pour renommer un travail unique. Le scheduler attend donc l’annulation du nom historique avant de créer le nouveau ; `KEEP` conserve le même nouveau travail aux appels suivants. La désactivation annule les deux noms. L’échéance technique peut être recalculée lors de cette première reprise ; l’historique et la sélection du jour restent conservés et `DailyWallpaperUpdater` protège toujours contre une deuxième diffusion réussie à la même date. Ces références historiques restent lisibles dans le code et les fixtures de migration, sans concaténation destinée à les masquer.
