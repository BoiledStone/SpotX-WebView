# Validation de l'édition 2.0.2

Les scénarios sont ciblés et reproductibles. Cette page distingue les fonctions
observées sur un appareil des options encore héritées sans essai complet.

| Contrôle | Résultat |
|---|---|
| URL, en-têtes et confidentialité | 158 assertions JVM réussies |
| Protocole du pont asynchrone | 15 assertions JavaScript réussies |
| WebView Android : origines, permissions, réseau, média, consentement, Connect, cookies de profils, annulations et reprise | 66 contrôles réussis |
| Scripts réellement compilés dans les DEX, y compris les copies intégrées par R8 et les liens des paramètres | 79 contrôles réussis |
| Reprise automatique | 20 scénarios : veille, progression, pause, réseau, transfert distant pendant une reprise, consentement, état inconnu, limites et position |
| Spotify Connect | Transfert entrant, identité locale stable, commande distante, refus de transfert et ancienne réponse opposée à une nouvelle confirmation du même appareil |
| Paramètres natifs | Ouverture par la roue dentée, défilement des onglets, accès À propos, version 2.0.2 et dépôt actuel vérifiés sur Android |
| Thèmes | 46 contrôles Android : recherche, aperçu, annulation, éditeur avancé, clavier, application d'une palette et restauration des réglages d'origine |
| Consentement | Le lecteur s'efface devant les cookies et conditions ; aucun accord automatique ; retour du lecteur après le choix, dans Chrome et Android WebView |
| Lecteur | Quatre tailles : 320 × 740, 360 × 800, 412 × 915, 740 × 360 ; modes normal, réduit et plein écran |
| Optimisations | Pas d'écritures DOM inutiles sur 30 rafraîchissements identiques ; reprises de pochettes bornées |
| Adaptateur réseau | Request, en-têtes, corps, clonage, UTF-8, annulation et réponses vides |
| APK | Signature v3 et alignement 16 Kio contrôlés |

## Essais sur appareil

Les essais ont été effectués sur un téléphone sous Android 16 : connexion Spotify,
lecture et progression, commandes de la session média Android, pochettes web et
natives, arrière-plan et fenêtre PiP. Le lecteur normal mesure environ 163 px CSS,
le mode réduit 59 px ; les commandes principales gardent une cible de 44 px.

Lors de la validation 2.0.0, le rechargement automatique a été provoqué par une commande volontairement bloquée.
Le délai de surveillance est de six secondes ; le chargement de la page et la
reconnexion réseau s'y ajoutent. Dans le livrable final, le rechargement a été
observé après environ sept secondes, puis la lecture a repris près de sa position
précédente, avec les pochettes et la session conservées. Le morceau et sa position
sont mémorisés pour la tentative de reprise. Une validation précédente du même mécanisme a utilisé
un passage réel de plus de deux minutes en arrière-plan ; les tests automatisés
accélèrent uniquement le vieillissement de leurs scénarios.

L'en-tête sans nom personnel, les commandes du service retirées et l'accès direct
aux paramètres par roue dentée ont fait l'objet d'une revue sur appareil.

En 2.0.1, les tests de consentement et de commande Connect ont été rejoués dans
la WebView réelle du téléphone avec des pages et échanges fictifs. Sur la session
Spotify existante, l'application détecte un lecteur distant actif, conserve son
identité locale et désactive les relances automatiques. L'utilisateur a confirmé
qu'un morceau choisi depuis l'APK continue de jouer sur le deuxième appareil,
sans retour du son vers le téléphone.

En 2.0.2, la galerie utilise le catalogue existant de 70 thèmes sur l'appareil,
y compris les thèmes personnalisés disponibles. L'aperçu et l'annulation ne
modifient pas les préférences. Le test d'application active temporairement les
couleurs système, applique une autre palette, vérifie leur désactivation puis
restaure le preset et le mode d'origine dans un bloc de nettoyage. AMOLED et la
couleur secondaire sont vérifiés inchangés. Les captures de revue restent privées.
La recherche est testée avec le vrai clavier Android : les cartes et boutons
restent accessibles au-dessus du clavier et l'aperçu revient à sa fermeture.

La protection HttpOnly est vérifiée dans Android WebView avec un cookie factice
sur un domaine réservé au test, sans effacer ni lire les cookies de la session
Spotify réelle. Le format hérité ne mémorise pas les attributs complets des cookies ;
le correctif protège explicitement le cookie de connexion `sp_dc`.

Les tests Android utilisent un APK d'instrumentation distinct. Il n'est pas inclus
dans le dossier `apk/`. Les outils de diagnostic sont retirés après les essais et
le processus normal ne publie pas de socket de débogage WebView.

## Limites

- La panne après plusieurs heures de veille n'a pas été reproduite naturellement ;
  le blocage de lecture des essais est provoqué.
- Bluetooth, Android Auto, widgets sur écran d'accueil, téléchargements et reprises
  de fichiers volumineux ne sont pas tous validés sur du matériel réel.
- Les connexions Google, Apple et Facebook n'ont pas fait l'objet d'un essai complet.
- Un silence audio avec une progression qui continue n'est pas détecté par ce critère.
- Le client natif couvre les échanges textuels des domaines autorisés ; ce n'est pas
  une implémentation complète du standard Fetch.
- Les bibliothèques natives et tierces héritées restent embarquées ; aucun audit
  exhaustif ni mise à niveau générale n'est revendiqué.

## Références primaires

- [Android : ponts WebView](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).
- [Android : validation des URI](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading).
- [Android : WebView.reload](https://developer.android.com/reference/android/webkit/WebView#reload()).
- [Android : Doze et App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).
- [WHATWG Fetch](https://fetch.spec.whatwg.org/).
- [Spotify : fonctionnement de Connect](https://developer.spotify.com/documentation/web-playback-sdk/concepts/spotify-connect).
- [Spotify : transfert de lecture](https://developer.spotify.com/documentation/web-api/reference/transfer-a-users-playback).
- [Android : CookieManager](https://developer.android.com/reference/android/webkit/CookieManager).
- [RFC 6265 : attribut HttpOnly](https://httpwg.org/specs/rfc6265.html#section-4.1.2.6).

Les références Spotify décrivent le comportement attendu de Connect. Le correctif
observe les échanges déjà utilisés par le lecteur web hérité ; leurs routes
internes ne constituent pas une API publique garantie par ces documents.

Les instructions d'exécution des tests figurent dans [BUILDING.md](BUILDING.md).
