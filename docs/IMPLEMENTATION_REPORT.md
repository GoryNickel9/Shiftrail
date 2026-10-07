# Report implementazione

App Android nativa implementata nel repository `app-turni`, branch `codex/android-local-ocr`. Nessun fork e nessuna copia di codice applicativo. Le dipendenze e i progetti valutati sono in `OPEN_SOURCE_RESEARCH.md` e `THIRD_PARTY_NOTICES.md`.

## Componenti consegnati

| Milestone | Implementazione |
| --- | --- |
| M1 | Gradle wrapper, Kotlin/Compose, CameraX con focus al tocco e flash, Photo Picker, permessi runtime. |
| M2 | ML Kit latino incluso, token con geometria/confidenza, confronto OCR colore e contrasto, overlay debug. |
| M3 | Nome e alias locali, matching Levenshtein, candidati ambigui e scelta manuale, fascia della riga. |
| M4 | Intestazioni italiane, separatori relativi, date esplicite e conferma del lunedì. |
| M5 | Parser orari, overnight, mezzanotte, sigle, numeri ausiliari e confidenza deterministica. |
| M6 | Revisione, modifica/eliminazione/aggiunta, conferma delle celle incerte e immagine di origine. |
| M7 | Room, schema versionato, profilo, impostazioni, legenda, storico e prossimi turni. |
| M8 | Calendari scrivibili, eventi, fuso, all-day UTC, titolo/luogo/descrizione/promemoria/colore. |
| M9 | Chiavi stabili, confronto vecchio/nuovo, update senza duplicati, scelta per turni mancanti e recupero tramite marker. |
| M10 | Test JVM/Android, fixture sintetica, lint, ktfmt, CI, documentazione e checksum wrapper. |

Il crop manuale corregge prospettiva con quattro angoli. Il bitmap originale resta in memoria durante la revisione e può essere salvato soltanto tramite scelta esplicita. Database e immagini temporanee sono esclusi da backup e trasferimento Android; il manifest finale dell’APK non contiene INTERNET né ACCESS_NETWORK_STATE.

## Architettura

`core` contiene Kotlin JVM indipendente da Android; `app` contiene UI e adattatori. `ScheduleExtractor` separa estrazione e UI; `TableParser` accetta token sintetici; `EventFactory` valida gli eventi; `SyncPlanner` definisce le modifiche; `CalendarRepository` applica un batch provider; `SyncRepository` salva associazioni Room e recupera i marker. La preparazione non scrive eventi.

## Verifiche eseguite

La prima esecuzione ha individuato un errore di smart cast fra moduli in `SyncRepository`, corretto con variabile locale. Su Windows le socket AF_UNIX di Java impedivano l’avvio di Gradle; il workaround in `scripts/verify-windows.ps1` è limitato all’ambiente del processo. Il controllo manuale ha inoltre trovato pulsanti del crop fuori schermo con immagini verticali: il layout è stato corretto e coperto da test di regressione.

Verifica finale dopo la correzione del crop: `assembleDebug test lint checkKotlinFormat connectedDebugAndroidTest` completata con `BUILD SUCCESSFUL`, 19 test JVM e 7 test Android, zero fallimenti. Lint: zero errori, 19 avvisi (16 segnalazioni di versioni più recenti delle dipendenze e 3 suggerimenti KTX). Lo schema Room e il manifest dell’APK sono stati controllati; `git diff --check` non segnala errori.

L’APK finale è stato installato con `adb install -r` sull’emulatore. SHA-256: `b35088b31fdcbb957a4de84ca537e9e64e926a4261add42d55e78a6b6e464a2c`. Questo checksum identifica l’artefatto prodotto in questa sessione; una build con altra chiave debug può differire.

Il controllo manuale ha configurato Luca Baldeddu, importato il PNG attraverso il Photo Picker Android, aperto il crop e lanciato l’analisi. La UI ha mostrato la settimana corretta, `19:00 → 01:00`, `23:45 → 05:45`, gli altri due turni e le tre sigle. Sono stati verificati overlay OCR, permission flow, scelta del calendario locale di test e conferma finale: quattro modifiche applicate, quattro eventi letti dal provider, durate corrette e sigle senza legenda escluse. Gli screenshot in `docs/images/` usano soltanto dati sintetici.

La seconda importazione dalla galleria ha mostrato l’avviso di foto già importata e quattro azioni UNCHANGED. La conferma ha applicato zero modifiche; la query provider ha restituito gli stessi quattro ID (4, 5, 6, 7) e gli stessi intervalli. Il test manuale ha quindi verificato la deduplicazione anche attraverso il percorso UI completo.

## Misure

Hardware: Intel Core i3-10100F, circa 32 GiB RAM, Windows 11, Oracle JDK 21.0.6, Gradle 8.12, AGP 8.9.2, Kotlin 2.1.20. Emulatore Android 15/API 35, x86_64, due CPU virtuali, 2048 MiB RAM e rendering SwiftShader.

Il benchmark JVM del parser, eseguito separatamente dalla build, ha elaborato 254 token per mille iterazioni dopo cento warmup: 3375,5869 ms totali, 3,3756 ms medi, checksum 7000. È uno smoke benchmark senza JMH. ML Kit ha elaborato la fixture da 2400×1800 pixel con 465 token in 10.530 ms nell’ultima esecuzione e 11.932 ms nella precedente. La prima esecuzione del test OCR sull’emulatore ha richiesto circa 67,6 secondi, includendo decodifica e inizializzazione. Non sono misure di latenza su smartphone fisico; il target di pochi secondi su telefono resta da verificare.

## Limiti residui

La foto reale citata nella richiesta non era presente nell’allegato. I risultati OCR sono verificati su un tabellone sintetico, non su un dataset di foto reali. Non viene dichiarata un’accuratezza percentuale. Il crop dei bordi è manuale; non ci sono edge detection o deskew automatici. Una cella può produrre un solo evento per giorno: turni spezzati richiedono correzione e un’estensione del modello.

Gli orari DST ambigui o inesistenti bloccano l’esportazione e richiedono correzione; non c’è ancora un selettore esplicito dell’offset. La confidenza è euristica. Morte del processo o annullamento perdono la revisione in memoria. Il fingerprint identifica pixel decodificati identici, non fotografie differenti dello stesso foglio; nella settimana viene mantenuto il fingerprint dell’ultima sincronizzazione.

CalendarContract e Room non condividono una transazione. Il marker consente il recupero alla successiva importazione, ma un conflitto con marker duplicati o alterati richiede controllo. Colori e promemoria dipendono dalle capacità del provider; un’opzione rifiutata produce un errore visibile. Le sigle ignorate non diventano eventi nello storico. Non sono stati verificati cattura e flash su hardware fisico, calendario Google sincronizzato con account reale, né dispositivi senza Play Services.

L’APK usa firma debug. Prima della distribuzione pubblica servono firma release, verifica dei termini delle dipendenze, controllo del target SDK richiesto dal canale di distribuzione e una prova su foto reali autorizzate. Il repository non ha remote Git configurati: il push non è disponibile.

## Generazione APK e file principali

```sh
./gradlew assembleDebug test lint checkKotlinFormat
./gradlew connectedDebugAndroidTest
```

Su Windows: `scripts/verify-windows.ps1 -Jdk "C:\Program Files\Java\jdk-21"`. Output: `app/build/outputs/apk/debug/app-debug.apk`. Lo schema Room è `app/schemas/it.turni.app.data.TurniDatabase/1.json`. Il parser è `core/src/main/kotlin/it/turni/core/TableParser.kt`; sincronizzazione in `app/src/main/java/it/turni/app/calendar/`; stato UI in `ui/AppViewModel.kt`; fixture in `testdata/`.

La prossima verifica prioritaria è una prova completa su smartphone fisico con la foto reale autorizzata, confrontando celle estratte ed eventi del calendario prima di usare dati di lavoro.
