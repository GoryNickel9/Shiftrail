# UI e revisione — Shiftrail 0.2.0

## Flusso

Home, Storico e Impostazioni sono raggiungibili dalla navigazione inferiore. La prima apertura presenta la configurazione del nome; con un profilo salvato, la home mostra cattura e importazione, seguite dai prossimi turni. L’immagine comunica il passaggio dal tabellone al calendario.

L’importazione indica tre passaggi: Foto, Controllo, Calendario. Crop e OCR restano locali. La revisione mostra dipendente, problemi delle intestazioni e schede ordinate per data. Gli stati sono testuali: Pronto, Verificato, Da controllare, Da correggere. Il colore rafforza lo stato, senza essere l’unico segnale.

## Conferme

Una lettura valida con punteggio almeno 90/100 non mostra il pulsante di conferma. Sotto 90/100 occorre controllare la foto e confermare oppure modificare; UNKNOWN richiede modifica o esclusione. I valori non finiti non vengono accettati automaticamente. `ShiftCell.AUTO_ACCEPT_CONFIDENCE` definisce la soglia, applicata anche da `EventFactory` e dal ViewModel.

Il controllo del lunedì compare soltanto quando le date sono incomplete o incoerenti. Non viene eliminato in base al punteggio di un orario. La selezione del calendario e la scelta delle modifiche sono le azioni che autorizzano il salvataggio; nessun evento viene creato dalla sola lettura OCR.

Il valore Float di ML Kit mantiene la sua rappresentazione decimale durante la conversione, così 0,90 non diventa un valore appena inferiore alla soglia. Il punteggio mostrato tronca le cifre decimali senza arrotondare una lettura sotto soglia a 90/100.

I dettagli del punteggio rimangono visibili nelle schede incerte; per quelle pronte sono nel menu «Dettagli della lettura». Il testo originale delle proposte OCR resta disponibile. Il punteggio è euristico, non una probabilità calibrata.

## Layout e accessibilità

Continua, Salva impostazioni e Applica al calendario sono in un footer fisso mentre il contenuto scorre. Le azioni Modifica e Mostra origine usano `FlowRow`, così vanno a capo invece di uscire dalla scheda. Eliminazione e dettagli sono nel menu del giorno; l’eliminazione chiede una conferma per evitare un’esclusione accidentale. Il pulsante di ritorno ha un’etichetta accessibile. Le azioni principali hanno altezza minima di 48–52 dp.

Le impostazioni separano profilo, eventi, legenda e opzioni avanzate. Colore e debug sono espandibili. Il promemoria usa una tastiera numerica. La modalità scura segue le preferenze di Android. I dettagli tecnici degli identificatori calendario non occupano le schede dello storico.

## Verifica

I test del core coprono il confine 0,90, i valori inferiori, UNKNOWN e i valori non finiti. Un test usa direttamente `EventFactory`, senza UI, per verificare lo stesso blocco. I test Compose verificano prosecuzione a 90/100 senza conferma, blocco appena sotto la soglia, date e letture discordanti, footer visibile e azioni con larghezza 320 dp e caratteri al 160%. Le schermate di verifica usano dati sintetici. Il test della home controlla anche i pixel dello schermo: l’illustrazione deve comparire senza scorrere all’apertura, al ritorno dalle impostazioni e dopo la ricreazione dell’attività.

I risultati eseguiti sono nel report della versione. Smartphone fisici, TalkBack e combinazioni ulteriori di dimensioni e caratteri richiedono verifica separata.
