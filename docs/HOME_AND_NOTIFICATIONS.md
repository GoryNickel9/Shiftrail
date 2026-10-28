# Home e notifiche — 1.0.4

La Home non mostra più lo slogan o il nome del profilo sopra i pulsanti di importazione. I pulsanti Scatta foto e Importa foto rimangono disponibili dopo la configurazione del profilo.

## Prossimo turno

Il vecchio filtro del riepilogo considerava la data del giorno e sceglieva il primo turno di lavoro; non escludeva quelli già iniziati. Ora upcomingWorkShifts seleziona soltanto turni con inizio strettamente successivo all’ora locale attuale e li ordina per data, ora e ID. I turni già iniziati vengono esclusi sia dalla scheda principale sia dalla lista dei prossimi turni, anche se terminano il giorno successivo. Orario continua a mostrare i turni passati e in corso.

Mentre la Home è visibile, un aggiornamento è programmato per il prossimo inizio o entro un minuto, scegliendo l’intervallo più breve. Il ciclo si sospende quando la schermata non è ripresa e rivaluta subito l’ora al ritorno. Non vengono registrati allarmi o servizi per aggiornare la Home.

Alle 21:49 del 28 ottobre, 20:00–08:00 del 28 è un turno in corso e non può essere Il prossimo turno. Esattamente all’ora di inizio un turno viene rimosso dai prossimi. Se non rimangono turni futuri, la scheda principale non viene mostrata e compare il messaggio di elenco vuoto.

## Layout e navigazione

Le schede di Orario condividono una riga di intestazione con data e azioni allineate al centro in verticale. La data usa lo spazio disponibile a sinistra; matita e cestino restano a destra.

Le sottosezioni delle Impostazioni mostrano soltanto il titolo, senza la freccia per tornare al menu principale. Il tap sulla scheda Impostazioni e Indietro di Android conservano il ritorno al menu. La scelta del calendario mantiene la propria navigazione dedicata.

## Contenuto delle notifiche

Notifiche normali e sveglia usano lo stesso formato HH:mm – HH:mm, calcolato dagli istanti del turno nel fuso del dispositivo. Non mostrano date, Lavoro, testo secondario o testo espanso. L’ora di pubblicazione dell’avviso è nascosta; Android conserva il nome dell’app e le icone di sistema. La sveglia conserva il comando Ferma e il proprio schermo dedicato.

## Verifica

I 90 casi JVM distinti passano, compresi il caso delle 21:49 con turno notturno in corso, il confine esatto dell’inizio, mezzanotte, l’ordinamento e il formato orario senza date. La suite Android completa in debug passa con 70 test eseguiti e due esclusi perché richiedono una foto privata o il controllo esplicito online della release.

Il test della Home lascia la schermata aperta mentre inizia un turno e verifica il passaggio automatico al successivo. Gli altri controlli verificano l’allineamento verticale di data e matita, il ritorno dalle sottosezioni senza frecce e l’assenza dei testi rimossi. Le notifiche normali e quelle della sveglia hanno controlli sul titolo orario, sull’assenza di testo secondario e sulla disattivazione dell’ora di pubblicazione. Passano anche i 16 test selezionati sull’APK release firmato.

Log locali: artifacts/verify-1.0.4.log, artifacts/android-debug-1.0.4.log e artifacts/android-release-1.0.4.log. Nessuna prova su telefono fisico.
