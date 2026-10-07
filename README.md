# Turni

App Android nativa che legge la tua riga da una foto del tabellone settimanale e prepara eventi nel calendario del dispositivo. L’utente verifica i risultati prima di salvarli. Il modello OCR latino è incluso nell’APK; foto e testo OCR vengono elaborati localmente.

Android 8.0 o successivo, Kotlin, Compose Material 3, CameraX, ML Kit, Room, coroutine, ViewModel/StateFlow e Navigation Compose. Nessun account dell’app, backend o API AI a pagamento.

<img src="docs/images/home.png" alt="Home su Android 15" width="240"> <img src="docs/images/crop.png" alt="Crop della fixture sintetica" width="240"> <img src="docs/images/review.png" alt="Revisione dei turni riconosciuti" width="240">

## Utilizzo

1. Apri **Impostazioni** e inserisci nome e alias, uno per riga. Configura titolo, luogo, descrizione e promemoria degli eventi.
2. Scatta una foto o scegli un’immagine con il Photo Picker. Trascina i quattro angoli del crop mantenendo visibili nome, giorni e date.
3. Controlla la riga scelta e le date. Se il nome è ambiguo, seleziona la riga; se l’anno manca, conferma la data del lunedì.
4. Correggi, elimina o aggiungi celle. Usa **Mostra origine** per confrontare il contenuto. Le celle con confidenza inferiore al 70% richiedono conferma esplicita; quelle non riconosciute richiedono correzione o eliminazione.
5. Scegli il calendario e verifica il piano di sincronizzazione. Le nuove celle sono preselezionate; aggiornamenti ed eliminazioni richiedono una scelta.

`19-01` produce un evento dalle 19:00 alle 01:00 del giorno seguente. Numeri isolati come `6` non diventano turni. Le sigle non hanno un significato predefinito: la legenda locale decide se ignorarle, creare eventi intera giornata o usare orari personalizzati.

## Build

Richiede JDK 17 o 21, Android SDK 35 e build-tools 35.0.0. Apri il repository in Android Studio oppure configura `ANDROID_HOME` / `local.properties` con `sdk.dir`.

```sh
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew checkKotlinFormat
```

Su Windows usa `gradlew.bat`. L’APK è `app/build/outputs/apk/debug/app-debug.apk`. Per installarlo su un dispositivo con debug USB: `./gradlew installDebug`.

In questo ambiente Windows Java restituisce `Unable to establish loopback connection` sulle socket AF_UNIX. `scripts/verify-windows.ps1 -Jdk "C:\Program Files\Java\jdk-21"` applica un workaround soltanto ai processi figli, esegue build/test/lint e ripristina le variabili d’ambiente. Non serve su un ambiente sano.

L’APK debug è firmato con una chiave di sviluppo. Una distribuzione pubblica richiede una chiave release privata e la verifica delle policy del canale di distribuzione.

## Architettura e test

`core/` è Kotlin JVM: modelli OCR normalizzati, parser geometrico, matching nomi, parser date/orari, factory degli eventi, confronti e chiavi stabili. Non dipende da Android. `app/` contiene adattatori ML Kit, immagini, Room, CalendarContract e schermate Compose. Il parser accetta token sintetici senza avviare OCR.

La CI esegue build debug, test JVM e lint. I test strumentati richiedono un dispositivo o emulatore:

```sh
./gradlew connectedDebugAndroidTest
```

`testdata/synthetic-roster.png` usa dipendenti fittizi e verifica il flusso OCR. Configura `Luca Baldeddu` per provarlo. I test verificano anche la versione ridotta e un altro tabellone con layout, dipendente, settimana e turni diversi. Abilita **Developer** per vedere token, celle e divisori.

La versione 0.1.1 rilegge i tabelloni fitti tramite ritagli sovrapposti ingranditi, riconosce intestazioni inclinate e cerca le date sopra la prima riga dei dipendenti. Le letture discordanti degli orari diventano celle da correggere: **Mostra origine** consente il confronto con la foto e **Modifica** inserisce il valore corretto. Nomi, coordinate e orari delle foto segnalate non sono incorporati nel codice dell’app.

La versione 0.1.2 recupera anche orari frammentati dall’OCR o uniti a contatori numerici. Queste celle richiedono **Ho verificato questa cella** dopo il controllo dell’origine; gli orari discordanti richiedono ancora modifica o eliminazione.

La versione 0.1.3 rilegge le celle usando i box del testo completo, con margini e ingrandimento adattati alla dimensione dei caratteri. Questo evita di tagliare cifre quando le intestazioni sono decentrate rispetto alle celle. Se la lettura resta illeggibile, prova più contesto; i contatori uniti alle cifre senza separatori possono ancora richiedere modifica manuale.

## Privacy e permessi

CAMERA serve soltanto alla cattura. Il Photo Picker non richiede accesso all’intera galleria. READ_CALENDAR e WRITE_CALENDAR sono richiesti quando si continua dalla revisione. Le catture temporanee vengono rimosse dopo la lettura e all’avvio successivo in caso di interruzione. L’immagine viene conservata in memoria durante la revisione; l’utente può salvarne una copia tramite il selettore Android.

Room conserva profilo, alias, legenda, impostazioni, fingerprint e turni sincronizzati. Le foto, il testo OCR degli altri dipendenti e i token non vengono salvati nel database. Backup Android disattivato. Il manifest rimuove INTERNET e ACCESS_NETWORK_STATE dalle dipendenze: l’app non carica le immagini. Il provider del calendario può sincronizzare gli eventi con Google o altri account secondo le impostazioni del dispositivo.

## Calendario e limiti

Una chiave stabile per dipendente/data/calendario e l’ID provider evitano i duplicati. Il marker nella descrizione permette di recuperare eventi dopo un’interruzione tra CalendarContract e Room. Gli aggiornamenti modificano l’evento esistente; le cancellazioni non vengono decise dal parser. Il batch provider è atomico; i due database non condividono una transazione.

Questa versione gestisce una cella e un evento per giorno. Più turni separati nello stesso giorno richiedono una revisione del modello. Gli orari che cadono in un intervallo DST inesistente o duplicato vengono rifiutati, con richiesta di correzione. Il crop prospettico è manuale; rilevamento automatico dei bordi e deskew automatico non sono implementati. La confidenza è un punteggio euristico, non una probabilità calibrata.

## Ricerca e attribution

È stato scelto un progetto nuovo, senza fork né copia di codice applicativo. La ricerca è documentata in [docs/OPEN_SOURCE_RESEARCH.md](docs/OPEN_SOURCE_RESEARCH.md). I [sample ML Kit](https://github.com/googlesamples/mlkit) (Apache-2.0) sono stati consultati come riferimento; [Calendar Provider](https://developer.android.com/identity/providers/calendar-provider) e [ML Kit Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) definiscono le API usate. AndroidX e Kotlin sono Apache-2.0; ML Kit e le dipendenze Google sono distribuiti secondo i termini Google. Vedi [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Documentazione: [architettura](docs/ARCHITECTURE.md), [pipeline OCR](docs/OCR_PIPELINE.md), [parser](docs/SHIFT_PARSER.md), [sincronizzazione](docs/CALENDAR_SYNC.md), [report verifiche](docs/IMPLEMENTATION_REPORT.md).

La procedura di validazione e il benchmark sono descritti in [docs/VALIDATION.md](docs/VALIDATION.md). Le correzioni OCR sono documentate per [0.1.1](docs/OCR_FIX_0_1_1.md), [0.1.2](docs/OCR_FIX_0_1_2.md) e [0.1.3](docs/OCR_FIX_0_1_3.md).
