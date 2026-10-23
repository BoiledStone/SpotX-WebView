# Construire SpotX WebView

Le dépôt contient les correctifs de l'édition 2.0.0 et un APK prêt à installer.
Le pipeline décode un APK précis de Spotilol, applique les correctifs JavaScript,
ressources et smali, compile les classes Java ajoutées, reconstruit puis signe.

## Environnement utilisé

- Windows, PowerShell, Python 3 et Java 21.
- Android SDK, plateforme `android-37.0`, Build Tools `36.1.0`.
- Apktool `3.0.3`, téléchargé et vérifié par `tools/setup.ps1`.
- Pour les tests navigateur : Node.js 22, Chrome et Playwright Core `1.64.1`.

## APK de référence

Fournir `baseline/spotilol-1.2.4-build23.apk` : version 1.2.4, code 23,
package `com.project.lol`. L'empreinte SHA-256 attendue est :

```text
a48b347b00120ffb223932448fee3a503b22e9b73e399f831f8f512604268077
```

Le pipeline refuse une autre base. Ce fichier n'est pas un export de compte :
il s'agit uniquement de l'APK, sans données privées de l'application.

## Construire

Depuis la racine du dépôt :

```powershell
.\tools\setup.ps1
python tools/build.py --java-home 'C:\Program Files\Java\jdk-21.0.10' --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

Le résultat est `output/SpotX-WebView-2.0.0.apk`. Les vérifications de signature
et d'alignement 16 Kio sont exécutées pendant la construction. Le rapport est dans
`output/build-report.json`. La version de l'application a le code 20005.

La première construction génère une clé et son mot de passe dans `.local/`, exclu
de Git. Conserver ces deux fichiers ensemble pour les mises à jour des APK signés
avec cette clé. La clé du binaire distribué n'est pas incluse : un APK reconstruit
avec une nouvelle clé ne peut pas mettre à jour ce binaire par-dessus. L'identifiant
technique `com.boiled.spotx.webview` est conservé pour la continuité des mises à jour ;
ce nom n'est pas une marque affichée dans l'interface.

## Tester

```powershell
python tools/build_tests.py
npm install --prefix tools/test-runtime --save-exact playwright-core@1.64.1
node tests/bridge.test.mjs
node tests/experience.test.mjs
node tests/optimizations.test.mjs
node tests/native-fetch.test.mjs
node tests/playback-recovery.test.mjs
python tests/packaged_scripts.test.py
New-Item -ItemType Directory -Force build/policy-tests | Out-Null
& 'C:\Program Files\Java\jdk-21.0.10\bin\javac.exe' -d build/policy-tests patches/java/com/boiled/spotx/Policy.java patches/java/com/boiled/spotx/Privacy.java tests/PolicyTest.java
& 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -cp build/policy-tests PolicyTest
```

Les scripts navigateur utilisent le chemin Windows standard de Chrome. L'APK
de test est distinct de l'application et n'est pas nécessaire à son utilisation.

Sur un appareil de développement connecté, après installation de l'application :

```powershell
adb install -r output/SpotX-WebView-tests.apk
adb shell am instrument -w com.boiled.spotx.webview.tests/com.boiled.spotx.tests.SecurityInstrumentation
adb uninstall com.boiled.spotx.webview.tests
```

L'instrumentation arrête et relance le processus testé. Ne pas la lancer pendant
une saisie de compte. Les fixtures utilisent des données fictives et ne sont pas
des pages du catalogue Spotify.
