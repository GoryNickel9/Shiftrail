# Pianificazione intelligente delle ferie — proposta

Questa funzione non è implementata. Il calcolo sarebbe locale e deterministico, senza cloud o modelli AI remoti.

## Dati necessari

L'utente sceglie un intervallo, un budget di giorni oppure ore di ferie e la durata minima desiderata del periodo libero. I turni devono essere confermati nel periodo esaminato. I giorni senza eventi non sono automaticamente riposi: possono essere date per cui il tabellone non è stato ancora importato.

Shiftrail non aggiunge i riposi al calendario e attualmente conserva soprattutto i turni collegati a eventi. Per questa funzione occorrerebbe mantenere anche la copertura delle settimane e i riposi confermati in un registro locale separato dagli eventi. Questo non richiederebbe di ripristinare gli eventi di riposo nel calendario.

## Ricerca delle combinazioni

Per ogni possibile intervallo libero nel periodo richiesto, il motore individua i turni che lo interrompono e calcola il costo necessario a coprirli con ferie. Scarta gli intervalli con giorni sconosciuti o costo superiore al budget. Ordina i risultati prima per durata libera consecutiva, poi per minore consumo di ferie.

Il calcolo usa intervalli effettivi, non soltanto le etichette dei giorni: un turno 22:00–06:00 occupa anche la mattina successiva. Uno stesso turno non deve essere addebitato due volte. Le festività non sono considerate automaticamente libere per chi lavora a turni. Un budget espresso in ore richiede una regola esplicita sulle ore di ferie consumate; non si presume che ogni turno valga otto ore.

## Esempio

| Date di novembre | Programma confermato |
|---|---|
| 6–7 | Riposo |
| 8–9 | Lavoro |
| 10–11 | Riposo |

Con due giorni di ferie l'app proporrebbe l'8 e il 9 novembre, ottenendo sei giorni liberi consecutivi dal 6 all'11. Non usa il normale fine settimana come presupposto: utilizza i riposi effettivi del tabellone.

## Interfaccia

Una proposta mostra il periodo libero, le date da richiedere, il costo in giorni/ore, il budget residuo e una rappresentazione dei turni coperti. L'utente può confrontare le combinazioni e salvarne una come ipotesi. La semplice consultazione non modifica i turni o il calendario e non equivale all'approvazione delle ferie.

## Verifica prevista

Casi: turno notturno sul confine dell'intervallo, part-time, più turni nello stesso giorno, budget insufficiente, calendario incompleto, ferie già inserite, turni modificati dopo una proposta, cambio d'ora e confronto delle combinazioni con enumerazione esaustiva su piccoli calendari.
