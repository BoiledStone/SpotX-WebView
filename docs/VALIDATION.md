# Validation de l'édition 2.0.0

Les scénarios sont ciblés et reproductibles. Cette page distingue les fonctions
observées sur un appareil des options encore héritées sans essai complet.

| Contrôle | Résultat |
|---|---|
| URL, en-têtes et confidentialité | 158 assertions JVM réussies |
| Protocole du pont asynchrone | 15 assertions JavaScript réussies |
| WebView Android : origines, permissions, réseau, média, annulations et reprise | 49 contrôles réussis |
| Scripts réellement compilés dans les DEX, y compris les copies intégrées par R8 | 50 contrôles réussis |
| Reprise automatique | 16 scénarios : veille, progression, pause, réseau, appareil distant, limites et position |
| Lecteur | Quatre tailles : 320 × 740, 360 × 800, 412 × 915, 740 × 360 ; modes normal, réduit et plein écran |
| Optimisations | Pas d'écritures DOM inutiles sur 30 rafraîchissements identiques ; reprises de pochettes bornées |
| Adaptateur réseau | Request, en-têtes, corps, clonage, UTF-8, annulation et réponses vides |
| APK | Signature v3 et alignement 16 Kio contrôlés |

## Essais sur appareil

Les essais ont été effectués sur un téléphone sous Android 16 : connexion Spotify,
lecture et progression, commandes de la session média Android, pochettes web et
natives, arrière-plan et fenêtre PiP. Le lecteur normal mesure environ 163 px CSS,
le mode réduit 59 px ; les commandes principales gardent une cible de 44 px.

Le rechargement automatique a été provoqué par une commande volontairement bloquée.
Le délai de surveillance est de six secondes ; le chargement de la page et la
reconnexion réseau s'y ajoutent. Dans le livrable final, le rechargement a été
observé après environ sept secondes, puis la lecture a repris près de sa position
précédente, avec les pochettes et la session conservées. Le morceau et sa position
sont mémorisés pour la tentative de reprise. Une validation précédente du même mécanisme a utilisé
un passage réel de plus de deux minutes en arrière-plan ; les tests automatisés
accélèrent uniquement le vieillissement de leurs scénarios.

L'en-tête sans nom personnel, les commandes du service retirées et l'accès direct
aux paramètres par roue dentée ont fait l'objet d'une revue sur appareil.

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

Les instructions d'exécution des tests figurent dans [BUILDING.md](BUILDING.md).
