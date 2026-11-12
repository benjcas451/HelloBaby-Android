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
data/StueckUpload.kt           Medien in Stücken hochladen (Cloudflare-Limit)
data/LocalStorageService.kt    SQLite + Medienordner (Flutter-kompatibel)
data/LocalBackupService.kt     ZIP-Backup (Format kompatibel zur Flutter-App)
data/LocalApiImportService.kt  einmaliger Upload lokaler Einträge zum Server
data/ClientCertificates.kt     PEM (crt/key) -> SSLSocketFactory, inkl. PKCS#1->#8
data/CertSource.kt             SAF-Ordner mit client.crt/client.key
data/AppSettings.kt            Prefs + Flutter-Migration
ui/…                           Compose-UI (Home, Create, Listen, Medien, Papierkorb, Settings)
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
| `POST /api/entries.php?upload=init` | Medium anmelden (`name`, `size`) → `upload_id`, `chunk_size` |
| `POST /api/entries.php?upload=chunk&id=…&offset=…` | ein Stück als Roh-Body (`application/octet-stream`) |
| `GET /api/entries.php?upload=status&id=…` | Stand eines Uploads: `received`, `complete`, `bereit` |
| `POST /api/entries.php` | Eintrag anlegen; Medien als `uploads[]` (Kennungen der fertigen Uploads) |
| `DELETE /api/entries.php?id=<id>&diary=<id>` | Eintrag löschen – der Server legt ihn samt Medien 30 Tage in den Papierkorb |
| `GET /api/entries.php?papierkorb=liste` | Papierkorb: gelöschte Einträge beider Tagebücher |
| `POST /api/entries.php?papierkorb=wiederherstellen` | Eintrag samt Medien zurückholen (`kennung`) |
| `POST /api/entries.php?papierkorb=loeschen` | Eintrag endgültig entfernen (`kennung`) |
| `POST /api/favorite.php` | Favorit umschalten, Body `{"id":…, "diary":"…"}` |
| `GET /api/gallery.php?folder=uploads/<ordner>` | Dateien einer Galerie |

Medien holt die App über `/api/thumb.php?file=…&w=400` (Vorschaubilder und
Video-Poster) und `/api/media.php?file=…` (die Datei selbst; `&download=1`
erzwingt `Content-Disposition: attachment`).

### Medien in Stücken (seit 3.2.0)

Vor dem Server steht Cloudflare, das im Free-Tarif jede Anfrage auf 100 MB
begrenzt und nach 100 Sekunden ohne Antwort abbricht. Ein längeres Video
passt damit weder in eine einzelne Anfrage, noch darf seine Umwandlung
(ffmpeg) in der Anfrage laufen, die den Eintrag anlegt. `StueckUpload` lädt
deshalb jede Datei einzeln hoch, bevor der Eintrag entsteht:

1. **Anmelden** — `?upload=init` mit Name und Größe. Der Server antwortet mit
   der Kennung und der Stückgröße (derzeit 8 MiB); die App richtet sich nach
   dem Server, nicht nach einem eigenen Wert.
2. **Stücke senden** — der Reihe nach, jeweils mit dem Offset des ersten
   Bytes. Passt der Offset nicht zum Stand des Servers, kommt `409` samt
   `received` zurück. So setzt die App nach einem Abbruch dort fort, wo der
   Server wirklich steht, statt von vorn zu beginnen; ob ein Stück noch
   ankam, fragt sie über `?upload=status` ab.
3. **Auf Videos warten** — nach dem letzten Stück wandelt der Server ein
   Video im Hintergrund um. Die App fragt den Stand alle zwei Sekunden ab,
   bis `bereit` wahr ist; die Oberfläche zeigt so lange „Medien werden
   verarbeitet…“.
4. **Eintrag anlegen** — eine kleine Anfrage mit den Feldern und `uploads[]`.

Schritt 4 ist wiederholbar: Der Server merkt sich, in welchen Eintrag ein
Upload eingegangen ist, und liefert bei derselben Anfrage denselben Eintrag
zurück. Die App wiederholt sie deshalb bei einem Verbindungsfehler selbst.

Kennt der Server das Verfahren nicht (älterer Stand), fällt die App auf die
eine multipart-Anfrage mit `images[]` zurück. Erkennbar ist das an der
Antwort auf die erste Anmeldung: `400` ohne das Feld `upload_fehler`, das der
neue Server jeder Ablehnung mitgibt.

### Papierkorb (seit 3.3.0)

Der Server löscht einen Eintrag nicht sofort: Zeile und Medien liegen 30 Tage
in einem Papierkorb und verschwinden erst danach endgültig. Die App zeigt ihn
auf dem Startbildschirm (nur in den Server-Modi – die lokale Ablage hebt
nichts auf) für beide Tagebücher zugleich, mit „Wiederherstellen“ und
„Endgültig löschen“.

- Wiederhergestellt wird mit der alten Kennung und am alten Medienpfad, sofern
  beide noch frei sind.
- **Medien ohne Eintrag** (`"diary": null`) sind Ordner, deren Text schon
  verloren war, bevor es den Papierkorb gab. Sie lassen sich seit 3.3.1 als
  Eintrag ohne Text wiederherstellen: Die App fragt nach dem Tagebuch
  (`diary_vorschlag` des Servers ist hervorgehoben) und schickt `diary` und
  `von_name` mit. Nur ein leerer Ordner ist nicht wiederherstellbar.
- Bewusst ohne Zwischenspeicher und Warteschlange: Der Papierkorb zeigt den
  Stand des Servers, und beide Aktionen werden nicht später still nachgeholt.
- Ein Server ohne Papierkorb antwortet auf `?papierkorb=liste` mit `400` ohne
  das Feld `papierkorb_fehler`; die App meldet dann „Der Server kennt den
  Papierkorb noch nicht.“

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

## Offline-Betrieb

Bricht die Verbindung weg, bleibt die App benutzbar. Die Logik sitzt im
`ApiService` selbst (er ist die einzige Datenquelle) und greift nur in den
Server-Modi.

**Lesen:** Jede erfolgreiche GET-Antwort landet roh als JSON in
`filesDir/offline/antworten_<zugang>/`, benannt nach der vollständigen URL.
Scheitert eine Abfrage an einem Netzwerkfehler, kommt die Antwort von dort.
Damit funktionieren Tagesansicht, Monatsansicht, Favoriten, Galerie und
Statistik gleichermassen.

**Schreiben:** Anlegen, Löschen und das Umschalten eines Favoriten gehen in
eine Warteschlange, wenn sie den Server nachweislich nie erreicht haben
(`UnknownHostException`, `ConnectException`, `NoRouteToHostException`,
`SSLException`). Ein `SocketTimeout` oder jede andere `IOException` bleibt
mehrdeutig — der Server könnte den Eintrag längst haben, ein zweiter Versuch
legte dann einen zweiten an; dort bleibt es bei der Fehlermeldung.

Eine Ausnahme ist das Hochladen der Medien (seit 3.2.0 in Stücken, siehe
oben): Solange nur Stücke unterwegs sind, existiert der Eintrag nachweislich
noch nicht. Reißt die Verbindung dabei endgültig ab (`UploadUnterbrochen`,
nach fünf Anläufen je Anfrage), wandert der Eintrag deshalb ebenfalls in die
Warteschlange und wird später komplett neu hochgeladen.

**Medien wandern mit.** Ein offline erstellter Eintrag behält seine Fotos und
Videos: Die Dateien werden nach `filesDir/offline/medien_<zugang>/<uuid>/`
kopiert und von dort hochgeladen. Nach erfolgreichem Upload (oder wenn der
Eintrag verworfen wird) verschwindet der Ordner; verwaiste Ordner ohne
zugehörige Aktion räumt das Nachholen auf.

**Ordnung.** Neue Einträge bekommen eine negative lokale Kennung. Eine
Löschung, die einen noch wartenden Eintrag trifft, entfernt dessen Aktion
samt Favoriten-Umschaltungen und Medien. Solange etwas ansteht, geht auch ein
neuer Schreibzugriff hinten dran statt am Stau vorbei.

**Abgearbeitet** wird vor jedem Laden des Startbildschirms und sobald der
`ConnectivityManager` wieder ein Netz meldet. Das Nachholen benutzt die rohen
Aufrufe (`ladeHoch`, `loescheDirekt`, `favoritDirekt`) statt der öffentlichen
Methoden — sonst würde es dieselbe Aktion in einer Schleife erneut vormerken.
Beim ersten Verbindungsfehler bricht der Durchlauf ab, der Rest bleibt in der
Reihenfolge stehen. Vom Server inhaltlich zurückgewiesene Aktionen fliegen
raus und werden einmal gemeldet.

Die Ablage hängt am Zugang (Modus + Server-URL).

Uploads laufen gestreamt (`StueckBody` liest den Ausschnitt direkt aus der
Datei), nicht über den Arbeitsspeicher; die Timeouts sind auf 5 Minuten
gesetzt, weil ein Stück über eine langsame Leitung entsprechend lange braucht.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`PapierkorbTest` prüft, wie die App die Papierkorb-Liste des Servers liest und
beschriftet. `StueckUploadTest` prüft das Hochladen in Stücken gegen einen nachgestellten
Server (MockWebServer): Reihenfolge und Inhalt der Stücke, Fortsetzen nach
verlorener Antwort, Rückfall bei einem Server ohne das Verfahren und die
Einordnung von Verbindungsabbrüchen für die Warteschlange.

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
