# Spécification produit — Hors Cadre

## Concept et principes

Hors Cadre est une application Android en français qui diffuse une anecdote de sciences humaines par jour sous forme d’affiche de fond d’écran de verrouillage. L’utilisateur consulte ensuite une explication sourcée et choisit lui-même les cartes qu’il garde. Le ton est rigoureux, accessible et éditorial. L’app fonctionne hors ligne, sans serveur, compte, publicité, suivi comportemental ni achat intégré.

Les états d’une carte sont distincts : entrée du catalogue, sélection du jour, diffusion réellement réussie avec `WallpaperManager.FLAG_LOCK`, puis ajout volontaire à la collection. Une carte n’est jamais collectionnable avant une diffusion réussie. Un aperçu ne constitue pas une diffusion. Une erreur ne débloque rien. Android ne permet pas de savoir si l’utilisateur a réellement regardé son écran de verrouillage.

Les identifiants d’anecdotes restent techniques. Aucun numéro individuel de carte ne doit être rendu dans l’accueil, les fiches, la collection, la carte agrandie ou le fond d’écran. Les années des références et le nombre de cartes dans la collection restent autorisés.

La sélection quotidienne reste stable durant la journée locale. La rotation évite les cartes déjà diffusées jusqu’à épuisement, puis peut recommencer un cycle. L’historique technique des diffusions n’est pas exposé. Une carte oubliée disparaît de l’accueil et n’est pas récupérable depuis un historique utilisateur ; elle pourra revenir dans un cycle ultérieur.

## Identité visuelle

Direction : revue scientifique contemporaine, minimaliste et éditoriale. Fond clair chaud, texte noir bleuté, corail `#FF6347` et turquoise `#00BFAE`. En sombre : fond `#0B141B`, texte `#F7F3E8`, mêmes accents. Les disciplines partagent une identité commune. Préférer une typographie géométrique proche de Sora, des catégories en petites capitales espacées, des textes adaptatifs et compacts. Une illustration vectorielle éditoriale est associée à chacune des huit catégories et réutilisée par ses anecdotes. Les compositions sont construites en Canvas natif avec trames, silhouettes, gravures et formes géométriques ; elles s’adaptent aux couleurs du thème dans l’interface.

## Écrans et comportements attendus

- **Accueil** : carte du jour, discipline, illustration associée à la catégorie, accroche et référence courte ; aucun numéro individuel de carte ; cœur volontaire ; accès à l’explication ; accès discret à la collection. Pas de barre inférieure.
- **Explication** : retour, discipline, titre, développement, références complètes (auteur, titre, année minimum pour un livre) et liens cliquables pour les sources en ligne.
- **Collection unique « Sciences humaines »** : compte et grille à deux colonnes. N’afficher que les cartes volontairement sauvegardées ; la discipline reste visible.
- **Carte agrandie** : recto illustré, verso explicatif avec sources. Animations d’agrandissement et retournement à venir.
- **Paramètres** : thème système/clair/sombre, préférence de renouvellement du verrouillage, prévisualisation, application manuelle et informations de version.
- **Première ouverture** : expliquer la carte quotidienne, le renouvellement et la collection. Le consentement au renouvellement est explicite. Une fois activé, le renouvellement peut appliquer automatiquement l’affiche au verrouillage ; le fond d’accueil n’est jamais modifié.

## Fond d’écran

Un renderer dédié produit une affiche adaptée aux dimensions du téléphone : grande illustration dans la moitié supérieure, catégorie sous l’illustration, anecdote au centre ou dans la moitié inférieure, puis référence courte près du bas. La typographie reste lisible pour les textes longs en adaptant l’espace et, au besoin, la taille de l’illustration. Les thèmes clair et sombre adaptent le fond et les couleurs. L’affiche occupe toute la surface ; l’horloge et les notifications Android peuvent se superposer à l’illustration. Aucun numéro individuel de carte n’est affiché. Ne jamais dessiner l’horloge ou ajouter un logo. L’application native doit employer exclusivement `WallpaperManager.FLAG_LOCK` et ne jamais toucher au fond d’accueil. L’aperçu réutilise le même bitmap et ne débloque pas la carte. Le renouvellement WorkManager est facultatif, persistant et idempotent par date locale ; Android peut retarder l’exécution. L’ouverture de l’application vérifie aussi la date actuelle.

## Données éditoriales

Le catalogue source est `data/faits_sciences_humaines_v1_16.json`, en français, avec 16 anecdotes : deux chacune en sociologie, anthropologie, psychologie sociale, histoire, linguistique, économie, philosophie et géographie humaine. Il est fourni comme corpus de travail à vérifier avant publication. Ne pas inventer d’anecdotes ou de références pour compléter ce corpus. S’il manque, conserver l’importeur et signaler précisément l’absence.

## Contraintes produit

Kotlin, Compose, Material 3, Room, DataStore, Navigation Compose, Coroutines/Flow, ViewModel, WorkManager et Gradle Kotlin DSL. Architecture lisible par responsabilités, sans module superflu. Pas de synchronisation distante dans cette première étape ; prévoir une séparation entre stockage, repository, logique métier et UI. Toute évolution doit maintenir le verrouillage exclusif, le consentement, la collection volontaire et le fonctionnement hors ligne.
