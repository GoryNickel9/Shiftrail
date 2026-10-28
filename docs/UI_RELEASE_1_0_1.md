# UI e importazione della versione 1.0.1

## Modifiche

La release implementa il prototipo approvato in `design/mockup-flusso-ore/`. Il pannello delle settimane usa `WorkingHours.monthWeeks` e `WorkingHours.summarize`, gli stessi componenti dei totali mensili. La scelta provvisoria resta distinta dal periodo applicato. Titolo, chiusura e conferma rimangono esterni all’elenco scorrevole.

Le schede di Ore allineano le azioni a destra nella prima riga. Solo gli straordinari mostrano il cestino. I tag di test degli orari restano compatibili; le diciture visibili usano Straordinari. Il core conserva i nomi dei tipi esistenti per non cambiare formati e database.

Gli orari si modificano con ruote Compose che si fermano sul valore centrale. I minuti disponibili sono 00, 15, 30 e 45. Confermare applica la scelta; annullare conserva anche un eventuale minuto precedente fuori da questi intervalli. La sostituzione del selettore nativo elimina le rimisurazioni continue osservate nei dialoghi di modifica sovrapposti.

Calendario, Sfondo, Notifiche e Aggiornamenti mostrano soltanto il titolo del menu e l’icona. Le impostazioni interne mantengono i valori utili alla scelta. Durante l’OCR il caricamento è identificato come Analisi immagine senza percentuali simulate.

## Identità dei calendari

`WritableCalendar` conserva anche `ACCOUNT_TYPE`. La UI raggruppa le destinazioni per tipo e nome dell’account. Il servizio è identificato soltanto per i tipi riconosciuti; gli altri sono indicati come Altro account. Se il nome del calendario coincide con l’account, viene mostrato Calendario principale.

L’app di apertura è risolta tramite un intent Android VIEW per gli eventi, indipendentemente dall’account. Un risultato viene mostrato soltanto se coincide con un’app presente fra i gestori effettivi dell’intent. Il resolver di scelta non viene presentato come se fosse un calendario. Le query del manifest sono limitate agli intent del provider calendario; non viene richiesto accesso all’elenco completo delle app.

## Riposi e compatibilità

All’avvio e al salvataggio l’app forza `saveRestDays=false`, anche per preferenze precedenti. La revisione non blocca i riposi, e la generazione dei nuovi eventi li esclude. Le opzioni relative ai riposi non sono più disponibili nella UI.

Il flag e il colore storico restano nel database per compatibilità. Il valore SQL predefinito rimane quello dello schema esistente; la policy dell’app prevale sul flag salvato. Non è necessaria una migrazione Room. Il core conserva il supporto alle vecchie regole per leggere e verificare versioni precedenti.

La preparazione preserva gli eventi di riposo già collegati e non li propone come nuovi eventi. Non esegue cancellazioni automatiche. Se una nuova lettura rende mancante un precedente turno di lavoro, la rimozione continua a richiedere una selezione esplicita.

## Risultato e recupero

`CalendarWriteResult` aggiunge alle identità restituite dal batch il numero di inserzioni effettive. Viene contato il risultato URI dell’operazione sugli eventi, escludendo le operazioni sui promemoria. La precedente API che restituisce la mappa degli ID resta disponibile.

`SyncCommitResult` distingue modifiche applicate e inserzioni. La vecchia API `commit` conserva il conteggio complessivo per i chiamanti esistenti. La UI usa il nuovo resoconto: una modifica di orario aggiunge zero eventi, così come il recupero di un evento già scritto prima di un’interruzione di Room. Verifica del provider e transazione Room continuano a precedere il risultato positivo.

La conclusione mantiene soltanto le due informazioni richieste. La navigazione Indietro nella testata comune dell’app rimane disponibile.

## Validazione

Su Windows con JDK 21 sono completati `scripts/verify-windows.ps1 -Release`, test JVM, lint e controllo della formattazione. I test JVM comprendono 69 casi nel core e 12 nell’app, eseguiti anche nella variante release. Il lint riporta zero errori; restano 77 avvisi.

Sull’emulatore Android 15 la suite completa scopre 59 casi: 57 completati senza errori e due controlli online esclusi perché richiedono un’attivazione esplicita. Sono verificati lo scorrimento reale del selettore minuti, modifica e ripristino degli straordinari, scelta provvisoria della settimana, raggruppamento dei calendari, preferenze storiche dei riposi e conteggio delle inserzioni dopo un’interruzione simulata.

Sull’APK release non debuggabile passano 11 test del flusso dell’app. Dieci test di componenti isolati non possono avviarsi in questa variante perché dipendono da `ui-test-manifest`, disponibile soltanto in debug: il tentativo è registrato separatamente e non viene contato come verifica release riuscita. Questi componenti sono coperti dalla suite completa debug.

Passano inoltre sette test su schermo da 375 dp con caratteri al 160% e sette in orizzontale, comprendenti selezione della settimana, annullamento, ruote orarie e persistenza dei temi. Il test orizzontale scorre il contenitore prima di cercare una riga non ancora composta. Firma dell’APK verificata contro l’impronta già pubblicata; build 18, versione 1.0.1, Android minimo 26.

Le immagini in `docs/images/release-1.0.1/` provengono dall’app e dai componenti Compose con dati sintetici. La verifica non comprende un telefono fisico né tutti i provider calendario dei produttori Android. La sincronizzazione cloud resta responsabilità dell’account e del relativo provider; il nome dell’app di apertura non garantisce che il calendario sia sincronizzato.
