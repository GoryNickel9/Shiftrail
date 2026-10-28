# Pause non retribuite

Disponibili dalla 1.0.3 in Orario, per turni importati e straordinari. Aggiungi pausa apre un pannello con inizio e fine a rotelle, con minuti ogni cinque. Salva pausa applica il cambiamento; Annulla lo scarta. Ogni pausa ha modifica e cestino, con conferma prima dell’eliminazione.

## Conteggio

Il calendario conserva gli estremi originali del turno. Orario mostra Ore nette e, quando ci sono pause, Presenza e Pause non retribuite. Il calcolo sottrae le pause da ciascun intervallo prima di unire i segmenti retribuiti: sovrapposizioni fra turni e straordinari non vengono contate due volte. Se un altro intervallo retribuito copre una pausa, quel lavoro continua a contribuire al totale. La quota delle pause nel riepilogo è la differenza fra presenza e netto senza duplicati.

Esempio: 22:00–06:00 con pausa 02:00–02:30 vale otto ore di presenza, sette ore e trenta nette e cinque ore e trenta notturne. Il 02:00 appartiene al giorno successivo. Le pause a cavallo di mezzanotte, settimana o mese vengono ripartite fra i periodi corrispondenti. Restano valide le durate nominali locali già adottate dal conteggio: non è un sistema di timbratura.

Le pause devono avere durata positiva, rientrare nel turno e non sovrapporsi. Due pause adiacenti sono ammesse. Per eventi di almeno 24 ore il pannello richiede anche la data di inizio della pausa. La modifica di un turno o straordinario viene rifiutata prima di scrivere nel calendario se escluderebbe una pausa salvata.

## Persistenza e modifiche esterne

Room 5 aggiunge unpaid_breaks con ID, profilo, ID del turno e inizio/fine completi di data. La migrazione 4→5 crea soltanto la tabella e l’indice; 3→4 conserva la nuova preferenza sveglia e normalizza l’anticipo. Le installazioni precedenti non ricevono pause predefinite.

Una modifica esterna può lasciare una pausa fuori dal nuovo intervallo: l’app la conserva con un avviso e non la sottrae. Un turno eliminato o sostituito non riceve automaticamente pause da un altro turno; le pause rimaste senza turno sono mostrate per la revisione nel periodo della loro data e possono essere eliminate. Eliminare uno straordinario dall’app elimina anche le pause collegate, nello stesso aggiornamento locale.

Non occorre scrivere nel calendario per salvare o eliminare una pausa. La modifica non cambia allarmi, orario di inizio o sincronizzazione degli eventi. Nessuna trasmissione di dati.

## Verifica

La suite completa in debug passa su emulatore Android 15. I test del motore coprono passaggio di mezzanotte/mese, deduplicazione con altri intervalli retribuiti, pause adiacenti o sull’intero turno, sovrapposizioni e pause invalidate da modifiche esterne. Un conteggio indipendente minuto per minuto verifica l’unione dei segmenti e la quota notturna.

I test Android verificano migrazione 4→5, persistenza dopo chiusura/riapertura del database, modifica/eliminazione, durata netta e notturna, rifiuto delle modifiche incompatibili per straordinari e turni importati e assenza di modifiche al calendario quando si salva una pausa. Eliminare un turno o straordinario rimuove le pause collegate.

Passano anche i 24 test selezionati sull’APK release firmato e il test delle pause con testo al 160% su schermo da 375 dp. Le schermate di riferimento sono in docs/images/release-1.0.3/. Log: artifacts/android-debug-1.0.3.log, artifacts/android-release-1.0.3-final.log e artifacts/android-breaks-largefont-1.0.3.log.

Nessuna prova su telefono fisico. Le prove di interfaccia usano dati sintetici e non misurano presenze reali.
