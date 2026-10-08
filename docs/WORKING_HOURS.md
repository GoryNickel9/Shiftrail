# Ore

La schermata conta il programma di lavoro importato e salvato con successo nel calendario, più gli intervalli extra inseriti manualmente. Una foto analizzata o una sincronizzazione annullata non aggiunge ore. X e celle vuote non producono intervalli di lavoro. Le modifiche applicate al calendario aggiornano il turno già registrato; una reimportazione invariata non aggiunge un secondo turno.

Il calcolo conserva minuti interi. Inizio e fine sono orari locali; se la fine è precedente all’inizio, l’intervallo termina il giorno successivo. Orari uguali sono rifiutati: non significano 24 ore. Non sono detratte pause, né calcolati compensi o maggiorazioni. Il totale rappresenta ore di programma, non presenze verificate.

Un intervallo di ricerca va dalla mezzanotte del primo giorno alla mezzanotte esclusiva dopo l’ultimo. Gli intervalli sono tagliati a questi confini: 31 ottobre 22:30–1 novembre 01:30 contribuisce 1 h 30 min a ottobre e 1 h 30 min a novembre. La lettura da Room include anche il giorno precedente al mese, per conteggiare le ore che entrano dal mese passato.

Le settimane iniziano lunedì e finiscono domenica. La prima e l’ultima settimana del mese vengono limitate ai giorni del mese selezionato. I totali settimanali sono quindi disgiunti e la loro somma coincide con il totale mensile. Mese e anno sono selezionabili; la navigazione copre 1900–2100.

`WorkingHours` usa `LocalDateTime` e durate nominali tra gli orari riportati. Un turno 22:00–06:00 vale otto ore anche nella notte di cambio dell’ora legale; il calendario conserva invece gli istanti reali del fuso. Questo conteggio non è un sistema di timbratura e non deduce la durata effettiva di presenza durante il cambio DST.

La tabella `extra_work` conserva ID, dipendente, data, inizio, fine e data di creazione. Gli extra sono locali e non generano eventi nel calendario. Intervalli identici per dipendente/giorno/inizio/fine sono rifiutati, mentre l’utente può modificare o eliminare quelli esistenti. Dopo il salvataggio la schermata mostra il mese della data inserita e azzera il filtro settimanale, così il nuovo extra rimane visibile. La modifica conserva ID e valori del form durante la ricreazione dell’attività. Il calcolo somma gli extra ai turni: eventuali sovrapposizioni inserite dall’utente non vengono sottratte automaticamente.

Un cambio di calendario può lasciare più record importati per lo stesso giorno. Il conteggio usa il record con `updatedAt` più recente; l’ID risolve i pareggi in modo deterministico. Il modello mantiene un turno importato per dipendente/giorno, più un numero variabile di extra manuali. Non legge eventi personali o di altre app per attribuirli automaticamente al lavoro.
