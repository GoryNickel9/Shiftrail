# Controllo del cartellino presenze

La funzione si apre da **Orario → Controlla cedolino**. Usa schede giornaliere, con un filtro per i giorni da controllare e uno per l’intero mese. Non modifica turni, straordinari, pause o eventi nel calendario.

## Formato e lettura

La prima versione supporta il cartellino mensile **INAZ RP7000**, come il documento fornito: una pagina PDF verticale, tabella di 28–31 giorni, dieci colonne alternate E/U e campo TOTLAV. Il limite è 20 MiB. Non legge il cedolino economico privo di dettaglio giornaliero e non propone un riconoscimento generico per altri tracciati.

Il selettore Android concede accesso al solo documento scelto. L’app ne conserva una copia privata con nome casuale e SHA-256; non conserva l’URI del provider come unica fonte. PdfRenderer genera l’immagine sul dispositivo. ML Kit, già incluso nell’APK, legge intestazione, riepilogo e ciascuna riga. Il riconoscimento delle righe e delle colonne usa le linee della tabella. Una griglia non verificabile viene rifiutata.

La lettura non invia PDF, immagini o testo a un server. Il normale controllo degli aggiornamenti dell’app rimane indipendente. I file originali e i testi di documenti personali non devono essere aggiunti a fixture, log o repository.

## Revisione

Prima del confronto l’utente conferma periodo e dipendente. Un nome diverso dal profilo genera un avviso; l’app non associa automaticamente la lettura a un altro profilo. Periodo e totale mensile sono correggibili, con accesso all’intestazione e al riepilogo originali.

Il totale stampato deve coincidere con la somma dei TOTLAV giornalieri. Una cifra illeggibile rimane assente e impedisce la certificazione del totale. La coerenza della somma verifica un’invariante contabile; non dimostra da sola che l’OCR sia corretto.

Ogni riga conserva testo OCR, posizione nel PDF, timbrature e problemi della lettura. Un giorno richiede revisione se data, totale, orari o confidenza non superano i controlli. La soglia OCR è una regola euristica, non una probabilità calibrata. Le correzioni richiedono **Ho controllato questa riga nel documento** e non cambiano il PDF originale.

I valori leggibili ma non confermati partecipano al confronto come proposte: gli scarti sono indicati come **da verificare**, mantenendo il problema della lettura. Il filtro **Differenze** consente di concentrarsi su questi scarti senza nascondere le altre richieste di verifica. Un giorno incerto non riceve mai l’esito **Coincide** solo perché i numeri sono uguali.

Uno stesso giorno può avere sia uno scarto proposto sia problemi della lettura: i contatori delle differenze e delle verifiche possono sovrapporsi.

Le timbrature si correggono nell’ordine della riga, usando `E 18:30; U 21:30`. Una riga può iniziare con un’uscita appartenente al turno del giorno precedente. `24:00` conserva il significato di mezzanotte al termine della data riportata. La vista originale dispone di ingrandimento e scorrimento.

Una ripresa dopo la pausa notturna può apparire sulla riga del giorno seguente. Quando alla chiusura del turno notturno seguono più entrate, l’attribuzione resta da verificare: l’app non applica un orario di cambio giornata implicito. Nell’editor, ogni entrata della riga può essere attribuita esplicitamente al turno del giorno precedente. Questa scelta conserva la data effettiva della timbratura e cambia il solo giorno di lavoro a cui appartiene l’intervallo. La conferma rimane separata dal trattamento dell’ora solare.

## Convenzioni del confronto

Le unità sono minuti interi. La differenza è `ore nette app - TOTLAV del giorno`: un valore positivo indica meno ore nel cartellino rispetto al programma dell’app. Non si applicano tolleranze o arrotondamenti impliciti.

Il giorno di lavoro possiede il turno iniziato in quella data, inclusa l’uscita successiva a mezzanotte. Le uscite sulla riga seguente vengono collegate all’entrata aperta. Entrate sostituite, uscite isolate, intervalli non positivi e turni di almeno 24 ore richiedono verifica; non vengono inventate timbrature. Ai confini del mese può mancare una timbratura del documento vicino.

Le ore dell’app usano `WorkingHours`: pause sottratte e sovrapposizioni conteggiate una sola volta, con priorità ai turni e poi agli straordinari. Il confronto raggruppa il risultato per data d’inizio. **Orario** conserva la propria ripartizione per data civile: un turno a cavallo del mese può quindi produrre totali differenti fra le due viste, per il diverso periodo di attribuzione dichiarato.

Gli orari si confrontano separatamente dalle ore riconosciute. Per esempio, timbrature 18:29–21:32 e TOTLAV 03:00 possono corrispondere alle tre ore programmate e avere estremi diversi. La funzione segnala la differenza senza dedurre la regola aziendale di arrotondamento.

Maggiorazioni, HH_LAV, MDom, M25 e MORE+ non si sommano nuovamente al TOTLAV. La voce 502 / ora solare richiede una verifica distinta e non aggiunge automaticamente un’ora. Il conteggio esistente dell’app usa durate nominali locali.

Un giorno senza turno o riposo esplicito nell’app è **Dati mancanti**, non zero ore. Un calendario non disponibile, una query fallita o una pausa invalidata producono avvisi; non possono generare un esito complessivo senza riserve. Il risultato confronta un programma con un cartellino: non certifica presenze effettive né retribuzioni.

## Salvataggio e compatibilità

Room 6 aggiunge `payroll_reports` con profilo, mese, hash del PDF, date di creazione/aggiornamento e payload JSON versione 1. La migrazione 5→6 aggiunge la sola tabella e i suoi indici, conservando i dati precedenti. La chiave unica profilo/hash impedisce duplicati della stessa importazione. Ricaricare lo stesso documento conserva le correzioni; se la copia privata manca o è corrotta, il PDF originale può ripristinarla.

Le letture vengono salvate già come bozze, prima della conferma. Le correzioni sono scritture SQLite e sopravvivono alla ricreazione dell’attività e alla riapertura. Il confronto usa gli orari correnti osservati dall’app e si ricalcola quando cambiano calendario, straordinari o pause. La verifica dell’hash precede l’apertura dell’origine.

L’eliminazione richiede conferma e rimuove copia privata e letture. Il file selezionato dall’utente, esterno all’app, resta intatto. Le copie temporanee vengono rimosse dopo errori o annullamenti e all’avvio successivo in caso di interruzione del processo. PDF e database non partecipano al backup Android.

## Verifica

I test JVM controllano parser, unità, corrispondenza del nome, unione degli intervalli, pause, straordinari, passaggi di mezzanotte, cambio d’ora, totali e dati mancanti. I test Android generano un PDF con dipendente fittizio e verificano la lettura dai pixel, la persistenza, la migrazione, l’hash, il limite di dimensione e la rimozione dei file temporanei.

`PrivatePayrollPdfTest` è una regressione locale facoltativa: richiede il parametro di instrumentation `payrollPdfPath`. Il PDF personale non viene incorporato nell’APK di test. `PayrollUiTest` verifica navigazione, schede, origine, correzione, riapertura ed eliminazione con calendario sintetico.

I risultati effettivamente eseguiti, i tempi e i limiti della verifica vengono riportati in `VALIDATION.md`.
