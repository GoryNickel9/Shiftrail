# Architettura

Il modulo `core` espone modelli immutabili e funzioni deterministiche. `OcrToken` conserva testo, box normalizzato e confidenza disponibile. `TableParser` produce candidati, fascia della riga, colonne, celle e problemi espliciti. `EventFactory` trasforma soltanto celle valide con punteggio almeno 90/100 o confermate esplicitamente in eventi. `SyncPlanner` confronta nuovi eventi e associazioni salvate.

Il modulo Android usa adattatori distinti: `ImageProcessor`, `LocalScheduleExtractor`, `CalendarRepository`, `SyncRepository` e Room. `ScheduleExtractor` consente un futuro adattatore alternativo senza modificare il parser; non è presente alcuna implementazione cloud. Le operazioni provider e I/O eseguono coroutine su IO; parsing e trasformazioni bitmap usano Default. `AppViewModel` coordina lo stato con StateFlow. I composable gestiscono input e rendering; le regole sono fuori dalla UI.

Room v2 esporta lo schema nel repository e impone una relazione settimana→turni con cancellazione a cascata e unicità dipendente/data/calendario. La migrazione 1→2 conserva gli identificatori e aggiunge colore riposo, opzione di esportazione X e tabella `extra_work`. I nuovi default sono titolo Lavoro, preavviso 30 minuti, lavoro blu e riposo verde; un preavviso o colore già configurato rimane invariato. Le date sono ISO, gli orari ISO locali; le conversioni istante/fuso avvengono alla preparazione degli eventi. Non si usa destructive migration.

La navigazione è Home, Ore, Impostazioni, Camera, Crop, Review, Calendari, Sync e Risultato. Impostazioni modifica nome, preavviso, due colori e inclusione dei riposi. `WorkingHours` calcola intervalli e totali senza Android; il ViewModel osserva turni salvati ed extra tramite Flow e distingue l’ultima versione dello stesso giorno in calendari diversi. Il risultato OCR non sopravvive alla morte del processo: l’utente deve importare nuovamente la foto. Profilo, impostazioni e turni sincronizzati restano in Room. Le tabelle precedenti restano compatibili, ma gli alias e le legende non partecipano alle nuove importazioni.

## Decisioni

Kotlin e Android nativo rispettano le API richieste e consentono test JVM indipendenti. Compose riduce la duplicazione dei form; non viene introdotto un framework di dependency injection per cinque adattatori. Il crop a quattro angoli usa la trasformazione proiettiva Android: evita una dipendenza OpenCV prima di avere foto reali su cui misurare il beneficio.

Una cella al giorno è un limite esplicito. Cambiare il modello per più eventi richiederà chiavi con slot e una migrazione Room. I nomi sono conservati localmente; cambiare il nome del profilo mantiene l’ID, quindi le associazioni calendario restano valide.
