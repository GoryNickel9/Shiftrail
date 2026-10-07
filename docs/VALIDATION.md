# Verifiche e riproduzione

I test JVM controllano formati orari, durata overnight, sigle senza significato automatico, numeri ausiliari, date italiane, normalizzazione, fuzzy matching, nomi ambigui, geometria, anno mancante, date incoerenti, firme e pianificazione dei cambiamenti. Un test con seed 42 genera mille intervalli e controlla durata positiva, conversione UTC, riproducibilità e reimportazione invariata.

I test Android usano Room in memoria per profilo/upsert e un calendario locale creato e cancellato dal test per controllare eventi reali. Verificano insert, reimportazione, aggiornamento dello stesso ID, recupero del marker senza record Room, turno scomparso lasciato intatto e cancellazione esplicita. Non usano calendari personali.

La fixture OCR misura un caso sintetico con trenta righe. Il test verifica che il risultato appartenga a Luca Baldeddu, riconosca sette colonne e preservi `19-01`, `23:45-5:45` e `FE`. I test delle immagini controllano trasformazione identica, fingerprint e rifiuto di angoli incrociati. Il test Compose configura il profilo, salva e ricrea l’attività per verificare la persistenza.

Il controllo manuale ha individuato un crop troppo alto sulle foto verticali: il pulsante di analisi finiva fuori dalla schermata. Il layout ora adatta l’immagine allo spazio residuo. `CropUiTest` usa una foto con rapporto 1:4 e controlla che il pulsante resti visibile e funzionante.

```sh
./gradlew assembleDebug test lint checkKotlinFormat
./gradlew connectedDebugAndroidTest
./gradlew :core:parserBenchmark
```

`formatKotlin` formatta sorgenti e build Kotlin con ktfmt 0.64. `checkKotlinFormat` verifica la forma senza modificarla. I report sono in `core/build/reports/tests/test/`, `app/build/reports/lint-results-debug.html` e `app/build/reports/androidTests/connected/debug/`.

Il benchmark usa 254 token, trenta righe, sette colonne, cento iterazioni di warmup e mille iterazioni misurate. Il checksum previsto è 7000. Stampa JVM, OS e tempi; è un controllo di throughput del parser, non un benchmark di CameraX o ML Kit. Eseguirlo durante una build o con l’emulatore sotto carico altera i risultati.

Prima di distribuire una release, provare su smartphone fisico una foto reale autorizzata: nome con alias, prospettiva, intestazioni piccole, importazione dalla galleria, cattura/flash, permessi negati, revisione, calendario Google del dispositivo, secondo import e turno modificato. Verificare separatamente un dispositivo senza Google Play Services. Nessuna precisione percentuale su foto reali viene dichiarata.

Per la versione 0.1.1: 28 test JVM e 10 test Android passati, inclusa la regressione privata facoltativa. I dettagli del caso, le conferme manuali ancora necessarie e i limiti della verifica sono in [OCR_FIX_0_1_1.md](OCR_FIX_0_1_1.md). Il controllo su foto privata non misura le prestazioni e non sostituisce la verifica su tabelloni mai usati durante lo sviluppo.
