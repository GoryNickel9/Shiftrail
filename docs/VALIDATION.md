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

Per la versione 0.1.2: 31 test JVM e 11 test Android distinti passati. Il nuovo test della revisione è stato rieseguito dopo aver corretto lo scorrimento della lista nel test. Risultati, recupero dei contatori e limiti sono in [OCR_FIX_0_1_2.md](OCR_FIX_0_1_2.md).

Per la versione 0.1.3: 35 test JVM e 12 test Android passati nella stessa verifica finale, senza test saltati. La regressione privata usa `requireUnambiguous=true` e richiede tutte le sette celle corrette. Il nuovo tabellone sintetico verifica intestazioni decentrate e contatori adiacenti: sei celle corrette, una ancora UNKNOWN e bloccata. Dettagli delle prove, tentativi falliti e limiti sono in [OCR_FIX_0_1_3.md](OCR_FIX_0_1_3.md). Non sono stati eseguiti benchmark di prestazioni sulle foto private.

Per la versione 0.1.4: 42 test JVM e 12 test Android passati nella verifica finale, senza test saltati. I sette nuovi test JVM verificano proposte di recupero con vincoli, punteggi deboli o mancanti e blocchi prima della verifica esplicita; il test UI verifica anche la lettura OCR originale e il punteggio su 100. La regressione privata mantiene sette valori corretti con `requireUnambiguous=true`. Il cambiamento del punteggio può richiedere conferma per letture che prima erano esportabili. Regole, risultati e limiti sono in [OCR_FIX_0_1_4.md](OCR_FIX_0_1_4.md).

Per la versione 0.2.0: 47 test JVM e 14 test Android passati sulla build finale, senza test saltati. I nuovi controlli verificano la soglia inclusiva di 90/100 anche fuori dalla UI, la conversione dei punteggi Float di ML Kit, la prosecuzione senza conferma, il blocco appena sotto la soglia e il footer con caratteri al 160% a 320 dp. Il test del profilo verifica salvataggio, persistenza e rendering dell’illustrazione senza scorrere. La regressione privata mantiene sette valori corretti; non misura i tempi della foto. I risultati Android finali, i primi tentativi UI e i limiti della verifica sono documentati in [UI_RELEASE_0_2_0.md](UI_RELEASE_0_2_0.md).
