# Distribuer la version bêta Android

La variante `beta` est construite à partir de la configuration RELEASE, avec un identifiant distinct (`fr.inillusio.beta`), le nom `In Illusio Bêta`, une compilation non débogable et les identifiants officiels de démonstration AdMob. Elle conserve les parcours utilisateur, UMP et l’icône commune. Le code DEBUG et ses tests spécifiques restent limités à DEBUG ; BETA réutilise la façade RELEASE sans menu ni opérations développeur.

## Compiler

Avec Java 17, l’Android SDK déjà configuré et Gradle 8.13 :

```powershell
gradle --offline :app:assembleDebug
gradle --offline :app:assembleBeta
```

Le second résultat est copié dans `dist/InIllusio-v0.1.0-beta.apk` (le nom suit la version Gradle). Le dossier `dist/` et les APK sont ignorés par Git. Cette variante ne signe jamais avec la clé DEBUG.

## Créer et conserver la clé bêta

La clé doit être créée une seule fois et réutilisée pour toutes les mises à jour de la bêta. Perdre cette clé empêchera l’installation d’une mise à jour par-dessus la version précédente ; sauvegarder le keystore dans un emplacement privé et sécurisé.

Dans PowerShell, créer le keystore à un emplacement privé hors du dépôt. `keytool` demande les mots de passe interactivement : choisir et conserver soi-même des secrets robustes, sans les copier dans un fichier suivi.

```powershell
keytool -genkeypair -v `
  -keystore "$env:USERPROFILE\.android\inillusio-beta.jks" `
  -alias inillusio-beta -keyalg RSA -keysize 2048 -validity 10000
```

Créer ensuite, à la racine du dépôt, un fichier local ignoré `beta-signing.properties` avec les valeurs suivantes. Remplacer le chemin par le chemin absolu du keystore et saisir les mots de passe choisis localement :

```properties
storeFile=C:/Users/<compte>/.android/inillusio-beta.jks
storePassword=<mot de passe du keystore>
keyAlias=inillusio-beta
keyPassword=<mot de passe de la clé>
```

Ne pas partager ce fichier ni le keystore. Gradle ne journalise pas leurs valeurs. Sans keystore/configuration complète, la tâche de packaging BETA s’arrête avec une erreur explicite ; elle ne produit pas un APK faussement présenté comme signable.

## Récupérer, installer et mettre à jour

Après une compilation réussie, transmettre `dist/InIllusio-v0.1.0-beta.apk` au testeur par un canal privé. Sur son téléphone, ouvrir le fichier reçu et autoriser l’installation depuis cette source si Android le demande. La version bêta s’installe à côté de DEBUG car son identifiant est différent ; ses données Room et DataStore sont indépendantes.

Pour publier une mise à jour bêta, conserver le même `beta-signing.properties`, le même keystore et l’alias, augmenter `versionCode` dans `app/build.gradle.kts`, puis recompiler et envoyer le nouvel APK. Ne pas réinitialiser les données du téléphone testeur.

## Contrôles

- Vérifier `app/build/outputs/apk/beta/output-metadata.json` ou `aapt dump badging` pour confirmer le package `fr.inillusio.beta`, le nom `In Illusio Bêta` et l’icône launcher.
- Vérifier l’APK avec `apksigner verify --verbose --print-certs <apk>` ; l’empreinte de certificat doit rester identique entre mises à jour.
- Dans les métadonnées fusionnées, vérifier `debuggable=false`, le seul accès wallpaper `SET_WALLPAPER`, et l’identifiant AdMob de démonstration. La configuration RELEASE demeure distincte et n’active pas les annonces sans ses propriétés de production et de confidentialité.
- Dans l’application installée, essayer les sept appuis sur « Sciences humaines » : aucun menu ne doit apparaître. DEBUG conserve son menu et ses simulations.

L’APK ne doit pas être installé automatiquement sur le téléphone du développeur. Les vérifications UI et publicité réelle de démonstration nécessitent un émulateur ou un appareil de test ; leur résultat n’est pas déduit de la seule compilation.
