# Sincronizzazione calendario

`CalendarRepository` elenca calendari visibili con accesso almeno CONTRIBUTOR. I permessi vengono richiesti al passaggio dalla revisione. Non è necessario Google OAuth: la destinazione è un calendario già presente nel provider Android.

La preparazione converte date/orari locali nel fuso del dispositivo. Per gli eventi intera giornata usa mezzanotte UTC e fine esclusiva al giorno successivo, come richiesto dal provider. Nei cambi DST `getValidOffsets` deve produrre esattamente un offset; zero o due offset bloccano l’esportazione. Il programma non sceglie silenziosamente l’ora di un overlap.

Room usa ID logici basati su dipendente, data e calendario. Ogni evento include un marker SHA-256 nella descrizione. Prima di sincronizzare si legge l’evento associato e, se necessario, si cerca il marker. L’evento deve appartenere ancora al calendario e mantenere il marker; altrimenti la scrittura viene bloccata. Marker duplicati richiedono controllo manuale.

`SyncPlanner` restituisce INSERT, UPDATE, UNCHANGED, MISSING o RECREATE. Il confronto usa tempi, contenuto, titolo, luogo, descrizione e opzioni. UPDATE mantiene l’ID esistente. MISSING viene mostrato e non selezionato automaticamente. Un evento rimosso nel calendario può essere ricreato, previa conferma.

Le operazioni selezionate e i promemoria sono applicati con un singolo `applyBatch`. La transazione Room segue il batch. Un’interruzione dopo il provider e prima di Room viene recuperata tramite marker alla successiva importazione; il recupero forza un aggiornamento alle impostazioni confermate. La preparazione non modifica i due archivi. Un mutex evita commit concorrenti nella stessa istanza.

I promemoria associati agli eventi gestiti vengono sostituiti; non si modificano eventi senza marker. Il supporto a colori e promemoria dipende dal calendario scelto: un provider può rifiutare opzioni non supportate, facendo fallire il batch con errore visibile. Non viene eliminato automaticamente un evento perché non compare nella foto.
