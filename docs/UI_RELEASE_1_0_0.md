# UI della release 1.0.0

## Struttura

La navigazione principale resta Home, Ore, Impostazioni. Le Impostazioni hanno quattro pagine interne con titolo e pulsante Indietro fissi, anche in orizzontale; il contenuto scorre separatamente e riparte dall’inizio quando si apre un menu. Anche il pulsante Indietro di Android torna al menu. Il nome resta modificabile nella pagina principale delle Impostazioni.

Calendario contiene destinazione, salvataggio dei riposi e colori. Sfondo contiene tema e foto. Notifiche contiene attivazione, anticipo in ore e minuti e controlli Android già presenti. Aggiornamenti mostra versione, adesione beta e controllo manuale.

Il tema segue inizialmente Android. La selezione esplicita Chiaro o Scuro prevale sul sistema. `AppAppearance` conserva la scelta in preferenze locali; `AppViewModel` la pubblica nello stato e `TurniApp` la applica a Material e alle icone delle barre di sistema. Un errore di scrittura non viene ignorato. Non è necessaria una migrazione Room.

## Orari

Le schede dei turni mostrano una penna; quelle degli extra anche un cestino. Le due azioni stanno nella prima riga, a sinistra, con aree di tocco da 48 dp e descrizioni accessibili. L’eliminazione continua a usare la conferma esistente.

I campi Inizio e Fine aprono un selettore nativo con ore 00–23 e minuti 00, 15, 30, 45. La tastiera è disabilitata nelle ruote. Il valore si applica al campo solo con Conferma; il calendario cambia solo salvando la modifica completa. Un orario esistente con altri minuti resta intatto se il selettore viene annullato. Confermare applica il valore visibile nella ruota, senza arrotondamenti nascosti al salvataggio del turno.

Il controllo della data, gli intervalli diversi da zero, i conflitti del calendario, i permessi e il conteggio a mezzanotte restano responsabilità dei componenti esistenti. L’anticipo delle notifiche mantiene la precisione al minuto e il limite di sette giorni: i quarti d’ora riguardano gli orari di lavoro.

## Compatibilità

La release usa il codice build 17, successivo alla 0.5.0 (16). Non cambia identificativo Android, schema Room, marker degli eventi o protocollo degli aggiornamenti. Le preferenze dei promemoria calendario già salvate vengono conservate; il menu Calendario mostra i quattro controlli richiesti.

I mockup HTML sono in `design/mockup-impostazioni/`. Sono una proposta visiva con dati dimostrativi; l’interfaccia Android usa componenti Compose e ruote native.

## Validazione del 28 ottobre 2026

- Build debug e release, test JVM, lint e controllo della formattazione completati con Gradle. Lint segnala 0 errori e 62 warning: dipendenze, risorse inutilizzate, suggerimenti KTX e controlli preesistenti su target SDK e receiver. Non è stato cambiato il target SDK in questa release.
- 81 test JVM distinti passati: 69 nel core e 12 nell’app. I 12 test dell’app passano anche nella variante release.
- Suite Android completa su emulatore Android 15: 52 test passati, 2 esclusi mediante assunzione (foto private e verifica online della release pubblicata). L’esecuzione completa ha richiesto 156,928 secondi. Dopo il controllo in orizzontale è stata fissata l’intestazione delle Impostazioni; le schermate interessate sono state nuovamente verificate con i 16 test sul pacchetto release.
- 16 test passati sull’APK release non debuggabile: menu, profilo, calendario, sfondo, persistenza del tema, modifica e cancellazione degli extra, selettore dell’orario, notifiche, beta, versione, firma e migrazioni. Ultima esecuzione: 52,397 secondi.
- Altri 3 test passati su schermo da 375 dp con font al 160% e 3 in orizzontale da circa 667 × 375 dp. In entrambi i casi sono stati verificati navigazione dei menu e selettore dell’orario.

La prova iniziale ha aggiornato l’installazione 0.5.0 (build 16) tramite installazione con conservazione dei dati. Il certificato SHA-256 coincide con quello registrato in `signing-certificate.sha256`. Le nuove preferenze del tema non richiedono modifiche al database.

Le schermate reali con dati sintetici sono in `docs/images/release-1.0.0/`: impostazioni, tema chiaro/scuro, calendario, aggiornamenti, Home, Ore e selettore dell’orario. I log completi e gli APK sono nella cartella locale `artifacts/`, esclusa da Git.

Restano da provare telefoni fisici, TalkBack e i dispositivi Android 8/9. La verifica grafica e strumentata è stata eseguita su Android 15; non costituisce una prova su tutte le versioni supportate.
