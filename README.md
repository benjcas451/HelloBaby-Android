# HelloBaby! – Android (nativ)

Natives Android-Pendant der früheren Flutter-App **HelloBaby!** – ein
Schwangerschafts- und Entwicklungstagebuch mit Fotos/Videos, wahlweise
komplett lokal oder gegen eine eigene Server-API.

- **Sprache/UI:** Kotlin, Jetpack Compose (Material 3, eigenes Baby-Grün-Theme)
- **applicationId:** `ch.tschir.HelloBaby` (identisch zur Flutter-App → Installation ist ein Update)
- **Version:** 3.0.0, versionCode lokal 8, in CI `100 + run_number`
- **Verteilung:** signiertes APK über GitHub-Releases + App Bundle in den Play-Track `alpha`

## Funktionsumfang

- Zwei Tagebücher (Schwangerschaft/Entwicklung) mit dynamischen Feldern
- Tages-, Monats-, Favoriten- und Galerie-Ansichten, zufälliger Tag
- Eintrag erstellen mit Fotos/Videos (Galerie-Picker, Kamera), Upload-Fortschritt
- Datenquellen: lokal (SQLite + Medienordner) oder Server-API
  (API-Key, mTLS-Client-Zertifikat aus SAF-Ordner oder Cloudflare
  Service Token)
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

## Architektur (`:app`)

```
Models.kt                      Diary/Entry/Feld-Definitionen, Medien-Helfer
data/ApiService.kt             zentrale Datenquelle: lokal oder Server-REST
data/LocalStorageService.kt    SQLite + Medienordner (Flutter-kompatibel)
data/LocalBackupService.kt     ZIP-Backup (Format kompatibel zur Flutter-App)
data/LocalApiImportService.kt  einmaliger Upload lokaler Einträge zum Server
data/ClientCertificates.kt     PEM (crt/key) -> SSLSocketFactory, inkl. PKCS#1->#8
data/CertSource.kt             SAF-Ordner mit client.crt/client.key
data/AppSettings.kt            Prefs + Flutter-Migration
ui/…                           Compose-UI (Home, Create, Listen, Medien, Settings)
```

`ApiService` ist die einzige Schnittstelle der UI: je nach eingestelltem
Modus reicht es an `LocalStorageService` durch oder spricht die REST-API an.

## Datenablage & Datenmodell

Im lokalen Modus liegt alles unter `app_flutter/HelloBaby/` — derselbe Pfad
wie bei der Flutter-App (`context.getDir("flutter", …)`), damit das Update
Bestandsdaten übernimmt:

```
app_flutter/HelloBaby/hello_baby.sqlite     Datenbank (Schema v2)
app_flutter/HelloBaby/media/<diary>_<id>/   Fotos und Videos je Eintrag
```

Tabelle `entries`:

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | INTEGER | Primärschlüssel (Auto-Increment) |
| `diary` | TEXT | `schwangerschaft` oder `entwicklung` |
| `kalender_datum` | TEXT | Tag des Eintrags, `YYYY-MM-DD` |
| `bilder` | TEXT | **absoluter** Pfad zum Medienordner; leer, wenn ohne Medien |
| `von_name` | TEXT | wer den Eintrag angelegt hat |
| `favorit` | INTEGER | 0/1 |
| `created_at` | TEXT | Erstellzeitpunkt |
| `fields_json` | TEXT | tagebuchspezifische Felder als JSON — neue Felder brauchen **keine** Schemaänderung |

Tabelle `remote_imports` (`local_id`, `diary`, `server_base`, `remote_id`,
`imported_at`): merkt sich pro Server, welcher lokale Eintrag schon
hochgeladen wurde, damit ein zweiter Import keine Dubletten anlegt.

**Zu `bilder`:** Die Spalte enthält absolute Pfade — Erbe der Flutter-App.
Liegt das Datenverzeichnis nach einer Wiederherstellung unter einem anderen
Präfix (etwa `/data/user/10/…` in einem zweiten Nutzerprofil), zeigten
sonst *sämtliche* Einträge ins Leere. `LocalStorageService.rebaseMedienordner`
sucht den Ordner deshalb beim Lesen anhand seines Namens im aktuellen
`media`-Verzeichnis — aber nur, wenn der gespeicherte Pfad wirklich fehlt.

## REST-API

Basis-URL konfiguriert der Nutzer; alle Endpunkte liegen unter `<base>/api`,
alle Antworten sind JSON. Fehler als `{"error": "..."}` mit passendem Status.

| Endpunkt | Zweck |
|---|---|
| `GET /api/stats.php?diary=<id>` | erster/letzter Eintrag + zufälliges Datum |
| `GET /api/entries.php?date=YYYY-MM-DD&diary=<id>` | Einträge eines Tages |
| `GET /api/entries.php?year=&month=&diary=<id>` | Einträge eines Monats |
| `GET /api/entries.php?favorites=1&diary=<id>` | Favoriten |
| `GET /api/entries.php?images=1&diary=<id>` | Einträge mit Medien |
| `POST /api/entries.php` | Eintrag anlegen (**multipart/form-data**, `images[]`) |
| `DELETE /api/entries.php?id=<id>&diary=<id>` | Eintrag löschen |
| `POST /api/favorite.php` | Favorit umschalten, Body `{"id":…, "diary":"…"}` |
| `GET /api/gallery.php?folder=uploads/<ordner>` | Dateien einer Galerie |

Medien holt die App über `/api/thumb.php?file=…&w=400` (Vorschaubilder und
Video-Poster) und `/api/media.php?file=…` (die Datei selbst; `&download=1`
erzwingt `Content-Disposition: attachment`).

**Authentifizierung** je nach Modus: `X-API-Key`-Header (in allen
Server-Modi, falls hinterlegt), bei mTLS zusätzlich das Client-Zertifikat im
TLS-Handshake, im Cloudflare-Modus die Header `CF-Access-Client-Id` und
`CF-Access-Client-Secret` (seit 3.1.0; Prefs-Schlüssel
`cf_access_client_id`, `cf_access_client_secret`). Beide Token-Hälften gehen
nur gemeinsam raus — ein halbes Token weist Cloudflare genauso ab wie gar
keines.

**Medien laufen seit 3.1.0 über dieselben Kopfzeilen.** Vorher luden Coil und
ExoPlayer über ihre eigenen Clients und damit an `ApiService` vorbei — ohne
Key, ohne Zertifikat. Solange der Server diese Endpunkte offen auslieferte,
fiel das nicht auf; hinter Cloudflare Access blockiert der Rand jede dieser
Anfragen. `MedienClient` stellt jetzt einen OkHttp-Client bereit, der die
Kopfzeilen per Interceptor bei jeder Anfrage frisch setzt und im mTLS-Modus
dasselbe Client-Zertifikat verwendet; Coil bekommt ihn über
`SingletonImageLoader`, ExoPlayer über `OkHttpDataSource.Factory`.

**Access-Abweisung:** Ohne gültiges Token antwortet Cloudflare nicht mit
einem Fehler, sondern leitet auf die Login-Seite des Teams um. OkHttp folgt
dem, sodass eine HTML-Seite mit Status 200 ankommt. `ApiService` erkennt das
am Host der finalen Anfrage (Subdomain von `cloudflareaccess.com`) bzw. an
einem 403 mit `cf-ray`-Header und meldet es als Token-Problem.

Uploads laufen gestreamt (`asRequestBody`), nicht über den Arbeitsspeicher;
die Timeouts sind auf 5 Minuten gesetzt, weil Videos lange dauern und der
Server nach dem Upload noch verarbeitet.

## Sicherung & Gerätewechsel (Auto-Backup / D2D)

`app/src/main/res/xml/data_extraction_rules.xml` (API 31+) und
`backup_rules.xml` (bis API 30) sind als **Whitelist** gepflegt — sobald ein
`<include>` gesetzt ist, wandert ausschließlich das Aufgeführte mit.

| | Cloud-Backup | Geräte-Transfer (D2D) |
|---|---|---|
| `hello_baby.sqlite` (+ WAL) | ✅ | ✅ |
| `media/` (Fotos, Videos) | ❌ | ✅ |
| `hellobaby_settings.xml` (Server-URL, **API-Key**) | ❌ | ✅ |

- **Medien** sind vom Cloud-Backup ausgenommen, weil Fotos und Videos das
  Kontingent des Auto-Backups sprengen — das Backup würde daran nicht
  wachsen, sondern **ganz scheitern**. Für Medien ist das ZIP-Backup der
  vorgesehene Weg.
- **Einstellungen** bleiben aus der Cloud heraus, weil der API-Key im
  Klartext in den Prefs steht. Der Geräte-Transfer läuft dagegen
  Ende-zu-Ende-verschlüsselt direkt zwischen zwei Geräten.
- Eingebunden wird der **ganze** Ordner `app_flutter/HelloBaby`, nicht nur
  `hello_baby.sqlite`: SQLite läuft im WAL-Modus, ohne `-wal` käme ein
  veralteter Stand zurück.
- `cert_folder_uri` ist nach jedem Wechsel wertlos — die persistierte
  SAF-Leseberechtigung gilt nur auf dem erteilenden Gerät. `CertSource`
  prüft `persistedUriPermissions` und fragt den Ordner neu ab.

Beim Ändern der Regeln daran denken: **jedes neue `<include>` erweitert die
Whitelist, jede neue Datei fehlt sonst stillschweigend.** Die
In-App-Erklärung steht in `DB_INFO_TEXT` (Einstellungen → „Aufbau
Datenbank“) und muss mitgezogen werden.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Release-Builds erwarten `key.properties` im Projekt-Root (siehe
`key.properties.example`, Familien-Upload-Keystore; niemals einchecken).

## CI

`.github/workflows/build-apk.yml` (manuell auslösbar) baut ein signiertes
APK, hängt es an ein GitHub-Release (`v3.0.0-<run>`) und lädt zusätzlich
ein App Bundle in den Play-Track `alpha` (abschaltbar über den
Workflow-Input `play_upload`). Benötigte Secrets (Namen wie bei den
Geschwister-Apps): `PLAY_KEYSTORE_BASE64`, `PLAY_KEYSTORE_PASSWORD`,
`PLAY_KEY_ALIAS`, `PLAY_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`.
