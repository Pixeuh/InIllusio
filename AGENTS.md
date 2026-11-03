# Instructions pour les agents

- Lire `docs/SPECIFICATION.md`, `docs/ARCHITECTURE.md` et `docs/DATABASE.md` avant de modifier les flux de cartes ou le schéma.
- Garder l’app et ses fonctions essentielles hors ligne. Pas de serveur, compte, suivi comportemental ou achats. Les boosters publicitaires récompensés sont une exception explicitement autorisée : demande à l’initiative de l’utilisateur, après UMP, IDs Google de test en DEBUG et production désactivée jusqu’à configuration explicite.
- Ajouter une carte à la collection après une application réussie avec `WallpaperManager.FLAG_LOCK`, ou après confirmation officielle d’une récompense booster. Un aperçu, une publicité fermée sans récompense ou un échec ne déverrouille jamais une carte. Un booster ne touche jamais à WallpaperManager ni à l’historique de diffusion. Ne jamais modifier le fond d’accueil.
- Préserver le consentement explicite au renouvellement et ne pas annoncer l’automatisation tant que WorkManager n’est pas implémenté.
- Ne pas inventer de faits ou références ; maintenir le JSON comme source éditoriale et traiter ses entrées comme à vérifier.
- Conserver une illustration vectorielle partagée par catégorie et ne jamais afficher de numéro individuel de carte ; garder les IDs uniquement pour les relations et la logique métier.
- Garder les règles métier hors des composables. Les évolutions Room doivent ajouter une migration et préserver la collection.
- Mettre à jour `docs/ROADMAP.md` et README lorsque les fonctionnalités ou commandes changent.
- Les boosters sélectionnent uniquement des anecdotes validées, non archivées et absentes de la collection ; l’attribution Room est transactionnelle et idempotente et précède toute animation.
