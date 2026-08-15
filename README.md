# HelloBaby! – Android (nativ)

Natives Android-Pendant der früheren Flutter-App **HelloBaby!** – ein
Schwangerschafts- und Entwicklungstagebuch mit Fotos/Videos, wahlweise
komplett lokal oder gegen eine eigene Server-API.

- **Sprache/UI:** Kotlin, Jetpack Compose (Material 3, eigenes Baby-Grün-Theme)
- **applicationId:** `ch.tschir.HelloBaby` (identisch zur Flutter-App → Installation ist ein Update)
- **Version:** 3.0.0, versionCode lokal 8, in CI `100 + run_number`
- **Kein Play Store:** Verteilung als signiertes APK über GitHub-Releases

## Funktionsumfang

- Zwei Tagebücher (Schwangerschaft/Entwicklung) mit dynamischen Feldern
- Tages-, Monats-, Favoriten- und Galerie-Ansichten, zufälliger Tag
- Eintrag erstellen mit Fotos/Videos (Galerie-Picker, Kamera), Upload-Fortschritt
- Datenquellen: lokal (SQLite + Medienordner) oder Server-API
  (API-Key oder mTLS-Client-Zertifikat aus SAF-Ordner)
- ZIP-Backup/-Wiederherstellung (Format kompatibel zur Flutter-App)
- Einmaliger Import lokaler Einträge zur Server-API (mit Duplikatschutz)

## Datenübernahme von der Flutter-App

Beim ersten Start werden vorhandene Flutter-Daten übernommen:

- **SQLite:** dieselbe Datei `app_flutter/HelloBaby/hello_baby.sqlite`
  (Schema v2 inkl. `remote_imports`) wird direkt weiterverwendet, ebenso
  der Medienordner `app_flutter/HelloBaby/media/`.
- **Einstellungen:** Schlüssel aus `FlutterSharedPreferences` (Präfix
  `flutter.`) werden einmalig kopiert (Marker `migriert_von_flutter`).
  String-Listen liegen dort je nach Plugin-API als Präfix +
  Base64-kodierte Java-Serialisierung (Legacy) **oder** Präfix + JSON
  (Async-API) – beide Formate werden dekodiert.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Release-Builds erwarten `key.properties` im Projekt-Root (siehe
`key.properties.example`, Familien-Upload-Keystore; niemals einchecken).

## CI

`.github/workflows/build-apk.yml` (manuell auslösbar) baut ein signiertes
APK und hängt es an ein GitHub-Release (`v3.0.0-<run>`), Play-Upload gibt
es bewusst nicht. Benötigte Secrets (Namen wie bei den Geschwister-Apps):
`PLAY_KEYSTORE_BASE64`, `PLAY_KEYSTORE_PASSWORD`, `PLAY_KEY_ALIAS`,
`PLAY_KEY_PASSWORD`.
