# Instructions pour les agents

- Lire `docs/SPECIFICATION.md`, `docs/ARCHITECTURE.md` et `docs/DATABASE.md` avant de modifier les flux de cartes ou le schéma.
- Garder l’app en français et hors ligne. Ne pas ajouter serveur, compte, publicité, suivi comportemental ou achats.
- N’autoriser la collection qu’après une application réussie avec `WallpaperManager.FLAG_LOCK`. Un aperçu ou un échec ne déverrouille jamais une carte. Ne jamais modifier le fond d’accueil.
- Préserver le consentement explicite au renouvellement et ne pas annoncer l’automatisation tant que WorkManager n’est pas implémenté.
- Ne pas inventer de faits ou références ; maintenir le JSON comme source éditoriale et traiter ses entrées comme à vérifier.
- Conserver une illustration vectorielle partagée par catégorie et ne jamais afficher de numéro individuel de carte ; garder les IDs uniquement pour les relations et la logique métier.
- Garder les règles métier hors des composables. Les évolutions Room doivent ajouter une migration et préserver la collection.
- Mettre à jour `docs/ROADMAP.md` et README lorsque les fonctionnalités ou commandes changent.
