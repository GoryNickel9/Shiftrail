# Mockup UI Shiftrail

Aprire `index.html` nel browser. Il prototipo è autonomo e non richiede connessione o installazioni.

La prima tavola mostra Home, Ore, Impostazioni e modifica dell’orario. La seconda mostra Calendario, Sfondo, Notifiche e Aggiornamenti.

## Scelte della proposta

- Aggiunge in Sfondo le opzioni Segui il sistema, Chiaro e Scuro.
- Mantiene la palette esistente: blu `#2255C7`, verde `#386452`, sfondo `#F4F7F6`, testo `#162A35`.
- Usa la stessa navigazione Home / Ore / Impostazioni e icone con tratto uniforme.
- Divide le Impostazioni in quattro pagine. Mantiene visibile il profilo esistente; la sua modifica non è definita da questa proposta.
- Posiziona penna e cestino in alto a sinistra nella scheda del lavoro extra. I turni ordinari mostrano solo la penna.
- Mostra inizio e fine nello stesso pannello. Due ruote selezionano ore 00–23 e minuti 00, 15, 30, 45.

I nove testi indicati per la rimozione non compaiono. I dettagli di notifica e il calendario non introducono nuove spiegazioni estese.

## Interazioni dimostrative

I menu portano alle schermate di dettaglio. Gli interruttori cambiano stato. Le ruote scorrono, accettano clic e frecce della tastiera. I campi Inizio e Fine selezionano l’orario da modificare. Salva orario aggiorna soltanto il prototipo.

Le altre azioni mostrano messaggi dimostrativi. Nessuna modifica raggiunge profilo, calendario, notifiche, foto o aggiornamenti reali. Nomi, ore e versione sono esempi.

## Verifica

Renderizzate e controllate visivamente otto schermate con Chromium tramite Playwright. Verificati elenco minuti, selezione dell’orario finale 18:30, conferma del salvataggio dimostrativo e interruttore notifiche. A larghezza 390 px la pagina non presenta overflow orizzontale.

Non sono stati modificati file Android né eseguiti test dell’app. Tema scuro, TalkBack, font di sistema ingranditi e comportamento dei controlli nativi restano da verificare durante l’implementazione Android.
