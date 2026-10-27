# Sfondo e notifiche

Queste funzioni sono modifiche sorgente successive alla 0.4.1. Il nome dell’app, versionCode, tag e release restano invariati.

## Sfondo personale

Impostazioni usa il Photo Picker Android, senza accesso all’intera galleria. La foto viene decodificata fuori dal thread principale con il decoder esistente, correggendo l’orientamento EXIF, e ridimensionata a un massimo di 2048 pixel sul lato maggiore. Una copia JPEG viene scritta con `AtomicFile` in `filesDir/app-background.jpg`. Non dipende dalla disponibilità futura dell’immagine originale e non viene caricata in rete o inclusa nel backup Android.

La scelta e il ripristino si applicano subito, anche prima di premere Salva. Home, Ore e Impostazioni mostrano lo sfondo con ritaglio proporzionale e una velatura nel colore del tema all’88%; card, navigazione e dialoghi mantengono superfici leggibili. La foto sostituisce anche l’illustrazione standard della home. Un’immagine illeggibile lascia intatta la copia precedente.

## Preferenze dei promemoria

Le notifiche dell’app sono disattivate inizialmente. Le preferenze si salvano con il profilo: abilitazione, anticipo da 0 a 10080 minuti, suono e vibrazione. I campi Ore prima e Minuti prima compongono un unico valore; 0 e 0 significa all’inizio del lavoro. Android 13+ richiede `POST_NOTIFICATIONS`. Android 12+ può richiedere l’accesso speciale `SCHEDULE_EXACT_ALARM`; l’interfaccia mostra il collegamento e avverte quando manca.

Un avviso riepiloga data, inizio e fine del lavoro, con un solo promemoria per data di inizio. Usa l’intervallo dal primo inizio all’ultima fine di turno ed extra, come l’evento complessivo nel calendario. Le pause nell’intervallo non diventano ore lavorate. Gli extra senza turno ricevono un avviso; le X senza extra non lo ricevono. Gli eventi personali non collegati all’app sono esclusi. Il tocco apre la home.

Il preavviso sottrae minuti reali all’istante di inizio nel fuso corrente, anche durante un cambio DST. Questo differisce intenzionalmente dalle durate nominali del riepilogo Ore. Un promemoria il cui anticipo è già trascorso al momento della prima abilitazione non viene recuperato retroattivamente.

## Pianificazione e consegna

`AlarmManager` registra fino ai 64 prossimi avvisi. `setExactAndAllowWhileIdle` viene usato con l’accesso speciale; altrimenti `setAndAllowWhileIdle` consente ritardi decisi dal sistema. Un worker WorkManager locale aggiorna la pianificazione ogni 15 minuti quando le notifiche sono abilitate. Il periodo è un intervallo minimo, non una garanzia di esecuzione puntuale. Non richiede rete. La versione 2.10.1 di WorkManager è scelta per la compatibilità con compileSdk 35 e il resto dello stack del progetto.

Il ritorno nell’app, le modifiche a turni ed extra e le preferenze chiedono una riconciliazione immediata. Riavvio del dispositivo, aggiornamento dell’APK, cambio di ora o fuso e concessione degli avvisi precisi ricostruiscono gli allarmi. Gli avvisi già pianificati ma ritardati restano recuperabili finché il lavoro non è terminato. La ricezione rilegge impostazioni, dati e calendario prima di pubblicare: un allarme vecchio non può notificare un turno eliminato o un orario ormai diverso. Un identificatore basato su inizio e anticipo impedisce di ripetere un avviso già consegnato.

La disattivazione annulla gli allarmi registrati, le notifiche visibili e il worker periodico. Il database migra da 2 a 3 con quattro colonne non nulle e default espliciti; la catena 1→2→3 conserva le importazioni esistenti.

## Suono, vibrazione e limiti Android

Quattro canali Android rappresentano le combinazioni di suono e vibrazione. Questo consente di cambiare opzione senza tentare di modificare il comportamento immutabile di un canale già creato. I cambiamenti apportati dall’utente nelle impostazioni Android restano autorevoli. Modalità silenziosa, Non disturbare, volume, hardware e restrizioni del produttore possono limitare suono, vibrazione e puntualità.

I promemoria precedenti del calendario restano separati. Per evitare doppi avvisi, selezionare Nessuno nel preavviso del calendario e aggiornare gli eventi già creati se necessario. Spegnere le notifiche dell’app non rimuove i reminder del calendario.

Le modifiche esterne mentre il processo è chiuso vengono lette dal worker periodico o prima della consegna. Un turno anticipato oltre il nuovo istante di preavviso tra due controlli può perdere l’avviso: il provider non mantiene l’app sempre in esecuzione. Forzare l’arresto dell’app dalle impostazioni Android impedisce il lavoro in background fino alla riapertura. Non sono implementati avvisi ricorrenti di inizio giornata o per eventi personali.

## Riferimenti

- [Allarmi e accesso agli avvisi precisi](https://developer.android.com/develop/background-work/services/alarms).
- [Permesso notifiche Android 13](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- [Canali e preferenze dell’utente](https://developer.android.com/develop/ui/compose/notifications/channels).
- [PeriodicWorkRequest e intervallo minimo](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest).

I risultati delle verifiche di questa modifica sono registrati in `docs/VALIDATION.md`.
