# Notifiche, navigazione e sveglia — 1.0.3

Funzioni introdotte nella 1.0.3. Le pause sono integrate in Orario; la pianificazione ferie rimane una proposta.

## Anticipo delle notifiche

Il massimo è 24 ore (1440 minuti); i minuti sono multipli di cinque. La UI e il salvataggio applicano lo stesso vincolo del motore dei promemoria. A 24 ore è valido soltanto 00 minuti.

La migrazione Room 3→4 aggiunge fullScreenAlarm, disattivato per tutte le installazioni precedenti. Riduce gli anticipi superiori a 24 ore al nuovo limite e arrotonda quelli non multipli di cinque per difetto. Gli altri dati, compresi straordinari e destinazione calendario, vengono conservati. Il limite riguarda i promemoria dell'app; le notifiche gestite dalle app calendario rimangono separate.

## Navigazione

La sottosezione delle Impostazioni è mantenuta nel contenitore di navigazione. Toccare Impostazioni, anche quando è già selezionata, torna al menu principale senza salvare automaticamente le bozze. Tornare dalla scelta del calendario con Indietro mantiene invece la sottosezione Calendario.

Un nuovo tap su Home scorre all'inizio. Un nuovo tap su Orario torna alla vista mensile e all'inizio della lista, mantenendo il mese scelto. Senza profilo, Configura il profilo apre il menu principale delle Impostazioni sia da Home sia da Orario. La barra inferiore appare quando il caricamento iniziale è terminato, insieme al grafo di navigazione.

## Sveglia a schermo intero

Si abilita in Impostazioni → Notifiche e si applica con Salva. Usa lo stesso anticipo dell'avviso e le preferenze Suono/Vibrazione. Non è una nuova sezione della navigazione.

Un allarme preciso consegna il promemoria al receiver esistente. Se la modalità sveglia è abilitata e l'accesso agli allarmi precisi è disponibile, il servizio foreground mediaPlayback pubblica una notifica ALARM ad alta importanza. L'intent a schermo intero viene aggiunto solo quando Android lo consente. In caso contrario rimangono notifica e comando Ferma; l'app non avvia attività in background per aggirare il permesso. Se manca l'accesso agli allarmi precisi o il servizio non può partire, viene usato il promemoria normale.

Il servizio rilegge le preferenze salvate prima di riprodurre l'audio, evitando di suonare con un consenso superato durante il passaggio asincrono dal receiver. Il suono di sveglia del dispositivo è ripetuto tramite MediaPlayer e rispetta audio focus e impostazioni del sistema. La vibrazione è finita anche senza suono; audio e servizio hanno uno spegnimento di sicurezza dopo due minuti. Ferma è disponibile sia nella notifica sia nello schermo dedicato. Disabilitare gli avvisi o modificare il turno invalida la sveglia attiva durante l'aggiornamento della pianificazione.

Lo schermo può apparire sopra il blocco del dispositivo senza sbloccarlo. Il pulsante Ferma resta visibile anche con testo grande o schermo orizzontale. Indietro chiude soltanto lo schermo: la notifica continua a offrire Ferma.

Il receiver di avvio ricostruisce la pianificazione; non avvia il servizio di riproduzione al boot. La prima versione non aggiunge rinvio, scelta personalizzata della suoneria o un orario indipendente dall'avviso.

Riferimenti Android: https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi e https://developer.android.com/develop/background-work/services/alarms . Lo schermo intero richiede un accesso speciale da Android 14. L'apertura effettiva rimane una decisione del sistema.

## Verifica

Verifica della 1.0.3 del 28 ottobre 2026: build debug/release, 87 casi JVM distinti, lint senza errori bloccanti e controllo della formattazione completati. La suite completa Android 15 in debug riporta 69 test superati e due esclusi, che richiedono una foto privata o l’attivazione del controllo online della release. Il runner include gli esclusi in OK (71 tests).

Sono verificati limiti e normalizzazione delle notifiche, migrazioni con conservazione dei dati, navigazione con e senza profilo, consegna reale degli avvisi, riproduzione audio, arresto e apertura automatica sopra il blocco schermo. I test della sveglia passano anche con accesso allo schermo intero negato; le precedenti verifiche su testo al 160% e schermo orizzontale sono conservate nei log di sviluppo.

I test che dipendono da attività o metodi interni della variante debug non sono eseguibili sull’APK release. Sul pacchetto firmato passano 24 test selezionati delle funzioni effettive dell’app.

Log locali: artifacts/verify-1.0.3.log, artifacts/android-debug-1.0.3.log e artifacts/android-release-1.0.3-final.log.

Non è stato utilizzato un telefono fisico. Restano da verificare le restrizioni dei produttori, il risparmio energetico e le impostazioni Non disturbare sui dispositivi reali.
