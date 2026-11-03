# Hors Cadre

Hors Cadre est une application Android qui propose une anecdote quotidienne de sciences humaines et peut l’afficher sur l’écran de verrouillage. Le projet est actuellement une **version bêta en développement**.

## Fonctionnalités

- Catalogue éditorial local de 24 anecdotes réparties entre huit disciplines, avec références bibliographiques sans liens Internet.
- Affiche de verrouillage illustrée et adaptée à l’écran, avec thèmes clair et sombre.
- Collection volontaire des anecdotes et préférences conservées localement, consultable en grille ou en carrousel horizontal.
- Renouvellement quotidien facultatif, activé uniquement avec le consentement de l’utilisateur.
- Éditeur graphique local pour consulter et modifier le catalogue JSON.

L’application fonctionne sans compte ni serveur. Le renouvellement quotidien dépend de WorkManager et peut être retardé par Android. Les fonds d’écran sont appliqués exclusivement à l’écran de verrouillage.

## Technologies

Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager et Gradle Kotlin DSL. Le projet cible Android 8.0 (API 26) ou ultérieur, compile avec l’API 36 et utilise Java 17, Gradle 8.13, Android Gradle Plugin 8.13.2 et Kotlin 2.2.20.

## Organisation

- `app/src/main/java/` : application Android, interface, données, sélection quotidienne et rendu du fond d’écran.
- `app/src/test/` et `app/src/androidTest/` : tests unitaires et instrumentés.
- `data/faits_sciences_humaines_v1_16.json` : source éditoriale du catalogue. Gradle utilise directement ce dossier comme source d’assets Android.
- `editor/` : éditeur HTML/CSS/JavaScript, exécuté localement dans un navigateur, sans serveur ni dépendance externe.

Les références complètes sont consultables sur la page d’explication; le fond verrouillé affiche une version abrégée. L’accueil n’affiche pas de référence.
- `docs/` et `AGENTS.md` : spécification, architecture, données et consignes du projet.
- `.vscode/` : tâche de compilation et script Windows pour compiler, installer et lancer l’application.

## Compiler sous Windows

Prérequis : JDK 17, Android SDK déjà configuré, Gradle 8.13 présent dans le cache utilisateur, et un appareil ou un AVD Android. Le projet n’inclut pas de Gradle Wrapper.

Dans VS Code, `Ctrl + Shift + B` exécute la tâche **Compiler et lancer Hors Cadre**. Le script `.vscode/run-android.ps1` réutilise un émulateur connecté ou démarre l’AVD `Medium_Phone_API_37.0`, puis lance Gradle en mode hors ligne, installe la version DEBUG et ouvre l’application.

La compilation DEBUG seule peut être lancée depuis PowerShell si `gradle` est accessible dans le `PATH` :

```powershell
gradle --offline :app:assembleDebug
```

Les tests unitaires existants se lancent avec :

```powershell
gradle --offline :app:testDebugUnitTest
```

L’APK DEBUG est généré dans `app/build/outputs/apk/debug/`.

## Catalogue et éditeur

Ouvrez `editor/index.html` dans Chrome ou Edge, puis chargez `data/faits_sciences_humaines_v1_16.json`. L’éditeur valide les champs et les identifiants avant l’enregistrement. Il conserve le catalogue dans le navigateur jusqu’à export ou écriture autorisée; aucune synchronisation n’a lieu. Pour intégrer un export à l’application, placez le JSON validé dans `data/` puis recompilez. L’import Android est local et transactionnel.

Consultez `editor/README.md` pour le mode d’emploi et `docs/` pour la documentation technique.
Chaque anecdote a un statut éditorial individuel (`a_valider` ou `validee`). Seules les anecdotes validées sont proposées à la diffusion quotidienne ; les cartes déjà collectionnées restent conservées même si leur statut change.
