# Inserimento degli orari

Modifiche introdotte nella release 1.0.2, build 19.

La sezione Ore si chiama Orario, sia nella navigazione sia nel titolo. Le etichette che indicano un'unità di misura, come Ore prima e Ore notturne, mantengono il loro significato.

Tutti gli orari di lavoro si inseriscono con lo stesso selettore a rotelle: ore 00–23 e minuti 00, 15, 30, 45. Il componente è condiviso fra modifica dei turni salvati, straordinari e correzione delle celle dopo l'OCR. Aprire il selettore e annullare conserva il valore precedente, anche quando contiene minuti diversi dai quarti d'ora.

La correzione OCR distingue Lavoro, Giorno di riposo e Cella vuota. Lavoro richiede Inizio e Fine; un orario finale precedente a quello iniziale continua a indicare il giorno seguente. Date duplicate, date fuori dalla settimana e intervalli non validi restano soggetti ai controlli esistenti. I riposi continuano a essere esclusi dalle nuove importazioni nel calendario.

L'anticipo delle notifiche usa rotelle per 0–168 ore e 0–59 minuti. Si tratta di una durata, perciò conserva tutti i minuti già supportati. Il totale massimo resta sette giorni; un valore superiore disabilita il salvataggio e mostra l'errore. Il pulsante Salva delle impostazioni applica la scelta.

Non cambiano schema Room, formato dei turni o regole di sincronizzazione. Non sono state aggiunte le funzionalità proposte come sviluppi futuri.

## Verifica

Build debug, test JVM nelle varianti debug e release, lint e formattazione completati. Il lint riporta zero errori e 78 avvisi; il testo del vecchio campo Contenuto è ora una risorsa inutilizzata.

Sull'emulatore Android 15 passano 17 test delle schermate coinvolte. Dopo l'aggiornamento degli helper di scorrimento passano nuovamente cinque test di correzione, notifiche, turni e straordinari. Passano inoltre sei test con caratteri al 160% su una larghezza di 375 dp e sei in orizzontale. Gli helper fanno scorrere i contenitori LazyColumn prima di cercare i controlli non ancora composti.

Non è stata ripetuta la suite OCR completa: estrazione, parser, database e sincronizzazione non sono stati modificati. Nessuna prova su telefono fisico. La release 1.0.2 mantiene lo schema del database e la firma delle versioni precedenti.

Log locali: artifacts/verify-orario-wheels.log, artifacts/verify-orario-tests.log, artifacts/android-orario-wheels.log, artifacts/android-orario-controls.log, artifacts/android-orario-largefont.log e artifacts/android-orario-landscape.log.

Per la release 1.0.2 sono completati build debug e release, 81 casi JVM distinti (con i 12 dell'app eseguiti anche in variante release), lint e formattazione. Passano tutti i 13 test applicativi selezionati sull'APK release non debuggabile. Il test delle impostazioni fa scorrere esplicitamente il campo nome prima di verificarne la visibilità. Firma verificata contro l'impronta delle versioni precedenti; build 19, Android minimo 26.

Log della release: artifacts/verify-1.0.2.log, artifacts/test-build-1.0.2.log e artifacts/android-release-1.0.2.log.
