# Historique

## 2.0.2 — 23 octobre 2026

- Galerie de thèmes avec recherche, aperçu avant application et annulation ; les
  outils avancés, imports, exports et thèmes personnalisés restent accessibles.
- Appliquer une palette désactive les couleurs système qui pouvaient masquer le choix.
- Recherche des thèmes utilisable avec le clavier ouvert ; les actions restent
  visibles et l'aperçu se rétablit à la fermeture du clavier.
- Dépôt, versions et historique reliés à SpotX WebView ; l'ancien lien Discord
  est remplacé par les signalements GitHub.
- Version cohérente sur l'accueil, dans les paramètres, le mode hors-ligne et les exports.
- Retrait du réglage de télémétrie inopérant et des modes d'interface indisponibles.
- Restauration des profils : cookie de connexion HttpOnly et validation des caractères de contrôle avant toute modification de session.
- Spotify Connect : une nouvelle confirmation du même appareil reste prioritaire
  sur une réponse réseau ancienne qui pourrait réactiver la lecture locale.

## 2.0.1 — 23 octobre 2026

- Le lecteur compact s'efface devant les conditions d'utilisation, les choix de
  cookies, la connexion et les fenêtres modales ; aucun consentement automatique.
- Suppression des clics automatiques qui reprenaient la lecture sur le téléphone
  et retrait du réglage de prise de contrôle.
- Spotify Connect : identifiant local conservé, suivi de l'appareil actif à partir
  des échanges existants, commandes de morceaux envoyées à l'appareil choisi.
- Protection contre les réponses tardives, la lecture automatique et la reprise
  après veille lorsqu'un appareil distant est actif ou que son état est inconnu.
- Tests de régression sur le consentement, le transfert Connect et la reprise.

## 2.0.0 — 23 octobre 2026

- Nouvelle identité SpotX WebView et retrait du nom personnel de l'en-tête.
- Suppression des interrupteurs du service dans l'en-tête et les accès rapides ;
  le service est activé automatiquement au démarrage.
- Roue dentée pour ouvrir directement les paramètres ; suppression du panneau déroulant.
- Récupération d'une commande bloquée après veille ramenée de douze à six secondes.
- Lecteur compact, libellés français, gestes et bibliothèque corrigés.
- Reprises de pochettes bornées et réduction des mises à jour d'interface inutiles.
- Pont WebView, réseau, permissions, composants média et profils durcis.
- Dossier de diffusion contenant l'APK, son empreinte, les correctifs et la documentation.

Base : Spotilol 1.2.4 / code 23 par lyssadev, dérivé de Spotifuck.
