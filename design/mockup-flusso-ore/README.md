# Mockup interattivo: importazione, Ore e calendari

Aprire `index.html` nel browser. Il prototipo usa HTML, CSS e JavaScript locali, senza librerie scaricate o connessioni esterne. Non legge le foto allegate e non accede a un calendario reale. Nomi, indirizzi e turni sono fittizi.

## Percorso di prova

1. Premere Avvia analisi. L’analisi simulata passa al controllo del tabellone. Un orario richiede conferma: aprire la penna del giovedì e premere Conferma orario. Chiudere il pannello lascia l’orario da verificare.
2. Confermare i turni e scegliere il calendario. L’elenco raggruppa i calendari per servizio e account. Le destinazioni di sola lettura non sono selezionabili.
3. Il riepilogo contiene soltanto i cinque turni di lavoro; le due X sono escluse. Si possono deselezionare i turni prima del salvataggio simulato.
4. Il risultato mostra soltanto Aggiunti nel calendario e Calendario di destinazione, oltre alla testata comune dell’app. Ripetere la stessa importazione senza azzerare la demo produce zero aggiunti.
5. Passare a Ore e aprire Settimana. Il pannello mostra intervalli, giorni e anteprima delle ore; la conferma applica il periodo, mentre Chiudi o Esc annullano la nuova scelta.

In Ore, penna e cestino sono in alto a destra. Il cestino compare soltanto sugli Straordinari e richiede conferma. Modifica ed eliminazione aggiornano i totali dimostrativi. Il selettore degli orari usa ore 00–23 e minuti 00, 15, 30, 45.

Le Impostazioni mostrano quattro menu senza sottotesti. Calendario non propone il salvataggio o il colore dei riposi. Sfondo consente di provare tema chiaro, scuro e di sistema. La scelta della foto e del colore è illustrativa, con messaggi espliciti; non è collegata alle impostazioni Android.

## Calendario, servizio e app

La prima schermata allegata mostra già l’account, ma alcune voci ripetono lo stesso indirizzo come nome e account. La proposta mostra l’account una sola volta nell’intestazione del gruppo e distingue il calendario principale dagli altri calendari.

Il Calendar Provider di Android espone nome calendario, nome e tipo dell’account. L’attuale repository legge anche il tipo, ma lo usa soltanto per distinguere i calendari locali. Per implementare la proposta occorre conservare quel dato nel modello e assegnare un’etichetta del servizio soltanto quando il tipo è riconosciuto. Un indirizzo email da solo non identifica il servizio.

Il servizio di sincronizzazione non equivale all’app che visualizza gli eventi. Più app possono accedere allo stesso calendario. Il nome dell’app predefinita va eventualmente rilevato a parte, tramite la gestione dell’intent per aprire il calendario; se Android richiede di scegliere un’app non c’è un nome unico da mostrare. Il prototipo mostra Google Calendar come app di apertura di esempio. Il controllo esterno Simula l’app predefinita consente di provare anche Calendario Huawei o nessuna app predefinita: sono scenari fittizi, non un rilevamento del telefono. Nell’implementazione il nome va mostrato soltanto se realmente risolto, separatamente dal servizio e dall’account.

Fonti: [Calendar Provider e account Android](https://developer.android.com/identity/providers/calendar-provider), [PackageManager.resolveActivity](https://developer.android.com/reference/android/content/pm/PackageManager#resolveActivity(android.content.Intent,int)).

## Dati e convenzioni

La settimana di esempio va dal 26 ottobre al 1° novembre 2026. In Ore il mese iniziale è ottobre: il 1° novembre appare attenuato e non entra nel totale di ottobre. L’esempio comprende 32 ore di turni, 2 di straordinari e 6 notturne, per un totale di 34 ore. Le ore notturne sono una quota del totale. Gli straordinari del giovedì sono contigui al turno ordinario e non si sovrappongono.

Il conteggio degli aggiunti è simulato con una chiave per calendario e data. Il prototipo non implementa aggiornamenti del provider, migrazioni, OCR, permessi o sincronizzazione Android. Non rimuove eventi di riposo eventualmente già presenti nella versione installata; quella decisione richiede una gestione esplicita nell’implementazione, senza cancellare eventi estranei all’app.

## Verifica eseguita

`verify.cjs` controlla il percorso completo, la conferma obbligatoria dell’orario incerto, l’annullamento della modifica, i minuti ammessi, il calendario di sola lettura, la destinazione locale, il riepilogo senza riposi, cinque eventi aggiunti, la seconda importazione senza duplicati, la selezione e l’annullamento della settimana, il confine ottobre/novembre, la posizione delle icone, la modifica e la cancellazione degli straordinari, l’assenza di sottotesti, i temi, l’assenza di overflow orizzontale a 390 px e l’assenza di errori JavaScript.

Esecuzione con Node e Playwright già disponibili nell’ambiente: `node design/mockup-flusso-ore/verify.cjs`. Se Playwright è installato fuori dal progetto, impostare `NODE_PATH` alla sua cartella `node_modules`.

Le immagini PNG vengono generate dal test nel percorso completo. Sono anteprime del prototipo, non screenshot di una nuova release Android. I file dell’app Android e la release 1.0.0 non sono stati modificati.
