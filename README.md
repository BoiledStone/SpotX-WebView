# SpotX WebView

[English](#english) · [Français](#francais)

## English

A Spotify player for Android built around the web player, with a compact interface,
Android media controls, and automatic recovery after a long time in the background.

**SpotX WebView is a fork of Spotilol, created by lyssadev (deleted account).
Spotilol is itself a fork of Spotifuck, credited to deviato.** This version builds
on Spotilol 1.2.4 with interface, reliability, and security improvements. It is
independent of Spotify and the official SpotX project.

### Download and install

**[Download SpotX WebView 2.0.1](apk/SpotX-WebView-2.0.1.apk)**

1. Download the APK to your phone.
2. If Android asks, allow installation from your browser or file manager, then
   install the APK.
3. Open SpotX WebView and sign in to Spotify directly inside the app.

Requirements: **Android 9 or later**, an up-to-date Android System WebView/Chrome,
a Spotify account, and an Internet connection for the catalog and web playback.
Root access is not required.

The app installs alongside Spotilol. To update an existing SpotX WebView
installation, install the new APK over it: the same package and signing key allow
your session and profiles to be preserved. Updates are installed through APKs;
the original project's automatic update checker is disabled.

The APK checksum is available in [SHA256SUMS](SHA256SUMS).

### Features

| Feature | Description |
|---|---|
| Compact player | Track title, artist, artwork, play/pause, previous/next, seeking, favorites, shuffle, and repeat; minimized and full-screen modes. |
| Spotify Connect | Choose a playback device through Spotify's device button and control that device from the app. Automatic playback and recovery respect the active remote device. |
| Consent windows | The compact player temporarily hides while login, cookie choices, or modal dialogs are visible, so their controls remain accessible. Consent stays under your control. |
| Android integration | Media notification, Android media session controls, background playback, and picture-in-picture (PiP). |
| Automatic recovery | After a long time in the background, a stalled playback request reconnects the player and attempts to resume the track at its previous position. |
| Library | Access to Spotify's catalog, albums, and playlists, with sorting and column controls. |
| Customization | AMOLED/Material You themes, display settings, and options inherited from the original player. |
| Profiles | Saved login profiles in the app's encrypted storage, plus a sleep timer. |
| Inherited features | Ad filtering, lyrics, Android widgets, Android Auto integration, and downloads through the inherited engine. Availability may change with the services they use. |

Account access and playback capabilities also depend on Spotify's web player
rules, your account, and your region.

### Recovery after a long time in the background

The service runs automatically. There is no service switch to turn off and back
on to restart the player.

After **at least two minutes in the background**, playback recovery is monitored
for three minutes. If a playback request shows **no confirmed progress for six
seconds**, SpotX WebView reloads the web engine and attempts to resume the track
at its saved position. Network loading can add time beyond those six seconds.

Pausing intentionally or issuing a new command cancels the pending recovery.
Normal playback progress, an active remote Spotify device, or an offline state
reported by the browser prevent unnecessary reloads. Attempts are limited to two
within ten minutes, with at least two minutes between them, during the player
session. Cookies and profiles are not cleared.

### Improvements

- Compact player, French labels, corrected gestures, and a properly collapsed library.
- Settings open directly from a gear icon, without an intermediate panel.
- Removed inherited automatic device selection and the obsolete takeover setting.
- Separate local and active device identities; incoming Connect events update the
  target, and late network responses cannot override a newer handoff.
- Limited artwork retries, publication of the latest result, and bounded image processing.
- Fewer interface writes when information has not changed; metadata from different
  tracks is no longer mixed.
- WebView bridge restricted to the intended origins and the main document, with
  method filtering and input validation.
- Validation of URLs, redirects, headers, and payload sizes for native HTTP requests.
- Camera/microphone web permissions rejected through this bridge, restricted media
  components, and profile encryption preserved when an error occurs.
- Inherited diagnostic collection disabled and sensitive data masked in logs
  handled by the patches.

### Validation status

Security policies, compiled scripts, player modes, and recovery behavior are
covered by tests. Playback, media controls, artwork, PiP, and recovery from an
intentionally blocked command have also been tried on an Android 16 device.
See [validation results and limitations (French)](docs/VALIDATION.md).

Extended screen-lock sessions, Bluetooth devices, Android Auto, home-screen
widgets, and real downloads have not all been fully validated. The APK's inherited
libraries have not received a general upgrade. Recovery detects a lack of progress;
it does not detect silent audio if the playback position continues advancing normally.

### Repository contents and building

```text
apk/                       Installable APK
patches/                   Java/JavaScript patches and generated resources
tests/                     Security, interface, and recovery tests
tools/                     Extraction, build, and local signing tools
baseline/README.md         Reference APK required to rebuild
docs/                      Build and validation documentation
SHA256SUMS                 APK checksum
THIRD_PARTY_NOTICES.md      Origins and credits
```

This edition uses **reproducible patches against the Spotilol 1.2.4 APK**, whose
checksum is verified. It does not claim to reconstruct that version's complete
Kotlin sources. The original APK required for rebuilding must be supplied
separately. Private signing keys, accounts, cookies, and personal data are not
included in this repository.

Detailed instructions are in [docs/BUILDING.md (French)](docs/BUILDING.md).

### Origins and credits

```text
Spotifuck — deviato
    └── Spotilol — lyssadev (deleted account)
          └── SpotX WebView — this fork
```

Historical links: [Spotilol](https://github.com/lyssadev/Spotilol) and
[Spotifuck_src](https://github.com/lyssadev/Spotifuck_src). Links under lyssadev's
account are no longer accessible at the time of this release. Credits to lyssadev,
deviato, and upstream contributors are preserved. Inherited notices and rights
are described in [THIRD_PARTY_NOTICES.md (French)](THIRD_PARTY_NOTICES.md).

---

<a id="francais"></a>

## Français

Un lecteur Spotify pour Android basé sur le lecteur web, avec une interface
compacte, des commandes média Android et une reconnexion automatique après veille.

**SpotX WebView est une branche de Spotilol, créé par lyssadev (compte supprimé).
Spotilol est lui-même une branche de Spotifuck, attribué à deviato.** Cette version
reprend Spotilol 1.2.4 et y apporte des corrections d'interface, de fiabilité et de
sécurité. Elle est indépendante de Spotify et du projet SpotX officiel.

### Télécharger et installer

**[Télécharger SpotX WebView 2.0.1](apk/SpotX-WebView-2.0.1.apk)**

1. Télécharger l'APK sur le téléphone.
2. Autoriser son installation depuis le navigateur ou le gestionnaire de fichiers
   si Android le demande, puis l'installer.
3. Ouvrir SpotX WebView et se connecter à Spotify directement dans l'application.

Prérequis : **Android 9 ou ultérieur**, Android System WebView/Chrome à jour,
un compte Spotify et une connexion Internet pour le catalogue et la lecture web.
Le root n'est pas nécessaire.

L'application s'installe à côté de Spotilol. Pour mettre à jour une installation
SpotX WebView existante, installer le nouvel APK par-dessus : le même package et
la même signature permettent de conserver la connexion et les profils. Les mises
à jour se font par APK ; le vérificateur automatique de l'ancien projet est désactivé.

L'empreinte de l'APK est fournie dans [SHA256SUMS](SHA256SUMS).

### Fonctions

| Fonction | Description |
|---|---|
| Lecteur compact | Titre, artiste, pochette, lecture/pause, précédent/suivant, progression, favoris, lecture aléatoire et répétition ; modes réduit et plein écran. |
| Spotify Connect | Choix de l'appareil avec le bouton Spotify et commande de cet appareil depuis l'application. La lecture automatique et la reprise respectent le lecteur distant actif. |
| Fenêtres de consentement | Le lecteur s'efface pendant la connexion, les choix de cookies et les fenêtres modales pour laisser leurs commandes accessibles. Le consentement reste votre choix. |
| Intégration Android | Notification média, commandes de la session Android, lecture en arrière-plan et fenêtre flottante PiP. |
| Reprise automatique | Après une longue veille, une commande bloquée déclenche une reconnexion du lecteur et une tentative de reprise du morceau et de sa position. |
| Bibliothèque | Accès au catalogue Spotify, aux albums et playlists, tri et colonnes. |
| Personnalisation | Thèmes AMOLED/Material You, réglages d'affichage et options héritées du lecteur d'origine. |
| Profils | Profils de connexion enregistrés dans le stockage chiffré de l'application et minuterie de sommeil. |
| Fonctions héritées | Filtrage publicitaire, paroles, widgets Android, intégration Android Auto et téléchargements via le moteur hérité. Leur disponibilité peut évoluer avec les services utilisés. |

L'accès au compte et les possibilités de lecture dépendent aussi des règles du
lecteur web Spotify, du compte et de la région.

### Reconnexion après une longue veille

Le service fonctionne automatiquement : il n'y a plus d'interrupteur à couper
et rallumer pour relancer le lecteur.

Après **au moins deux minutes en arrière-plan**, la reprise est surveillée pendant
trois minutes. Si une demande de lecture reste **six secondes sans progression
confirmée**, SpotX WebView recharge le moteur web puis tente de reprendre le morceau
à la position mémorisée. Le chargement réseau peut ajouter du temps à ces six secondes.

Une pause volontaire ou une nouvelle commande annule la reprise en attente.
Une lecture qui progresse normalement, un lecteur Spotify distant ou un état hors
connexion signalé au navigateur empêchent une relance inutile. Les tentatives sont
limitées à deux sur dix minutes, avec au moins deux minutes entre elles, dans la
session du lecteur. Les cookies et profils ne sont pas effacés.

### Corrections apportées

- Lecteur compact, libellés français, gestes corrigés et bibliothèque repliée proprement.
- Paramètres accessibles directement par une roue dentée, sans panneau intermédiaire.
- Suppression de la sélection automatique d'appareil héritée et de son réglage devenu inutile.
- Identifiants du lecteur local et de l'appareil actif séparés ; suivi des événements
  Connect entrants et protection contre les réponses tardives après un transfert.
- Reprises de pochettes limitées, publication du dernier résultat et traitement des images borné.
- Moins d'écritures d'interface quand les informations ne changent pas ; suppression
  du mélange entre les métadonnées de deux morceaux.
- Pont WebView limité aux origines prévues et au document principal ; filtrage des
  méthodes et validation des entrées.
- Contrôle des URL, redirections, en-têtes et tailles des échanges HTTP natifs.
- Refus des permissions web caméra/micro par ce pont, restriction des composants
  média et conservation du chiffrement des profils en cas d'erreur.
- Désactivation de collectes de diagnostic héritées et masquage des données sensibles
  dans les journaux traités par les correctifs.

### État de validation

Les politiques de sécurité, les scripts compilés, les différents modes du lecteur
et le mécanisme de reprise sont vérifiés par des tests. La lecture, les commandes
média, les pochettes, le PiP et la récupération d'une commande volontairement bloquée
ont aussi été essayés sur un appareil Android 16. Voir [les résultats et limites](docs/VALIDATION.md).

Les essais sur plusieurs heures de verrouillage, les appareils Bluetooth,
Android Auto, les widgets sur écran d'accueil et les téléchargements réels ne
sont pas tous validés. Les bibliothèques héritées de l'APK n'ont pas fait l'objet
d'une mise à niveau générale. La reprise détecte l'absence de progression ; elle
ne détecte pas un silence audio si la progression continue normalement.

### Contenu du dépôt et construction

```text
apk/                       APK installable
patches/                   Correctifs Java, JavaScript et ressources générées
tests/                     Tests de sécurité, d'interface et de reprise
tools/                     Extraction, construction et signature locale
baseline/README.md         APK de référence à fournir pour reconstruire
docs/                      Construction et validation
SHA256SUMS                 Empreinte de l'APK
THIRD_PARTY_NOTICES.md      Origines et crédits
```

Cette édition est construite avec des **correctifs reproductibles sur l'APK
Spotilol 1.2.4**, dont l'empreinte est contrôlée. Elle ne prétend pas reconstituer
les sources Kotlin complètes de cette version. L'APK original nécessaire à une
reconstruction est à fournir séparément. Les clés privées de signature, comptes,
cookies et données personnelles ne sont pas dans ce dépôt.

Les étapes détaillées sont dans [docs/BUILDING.md](docs/BUILDING.md).

### Origines et crédits

```text
Spotifuck — deviato
    └── Spotilol — lyssadev (compte supprimé)
          └── SpotX WebView — cette branche
```

Liens historiques : [Spotilol](https://github.com/lyssadev/Spotilol) et
[Spotifuck_src](https://github.com/lyssadev/Spotifuck_src). Les liens du compte
lyssadev ne sont plus accessibles au moment de cette édition. Les crédits à
lyssadev, deviato et aux contributeurs amont sont conservés. Les notices et droits
hérités sont précisés dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
