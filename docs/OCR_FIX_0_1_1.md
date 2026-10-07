# Correzione OCR 0.1.1

La lettura dell’intera foto segnalata produceva venti token, diversi dei quali coprivano colonne intere. Il parser non trovava un’intestazione coerente. I giorni erano presenti nell’immagine: il messaggio che invitava soltanto a includerli nel crop non descriveva la causa osservata.

## Modifiche

Quando la prima lettura non trova nome o sette giorni, oppure lascia celle non riconosciute, l’app rilegge ritagli sovrapposti ingranditi. Riporta i box al bitmap di origine e unisce le letture senza confondere testi uguali in righe vicine. Un solo riconoscitore e un ritaglio alla volta limitano le immagini temporanee.

Il parser ricava inclinazione e separatori dai box del testo. Cerca le date sopra la prima riga dei dipendenti, evitando di interpretare un turno della prima riga come data. Può recuperare un giorno mancante da almeno due date esplicite coerenti, richiedendo conferma del lunedì. La selezione della lettura dà priorità alla copertura dei giorni.

Le celle della riga individuata vengono rilette separatamente. Se due letture producono orari diversi ma sintatticamente validi, l’app conserva entrambe le alternative e blocca quella cella. La revisione indica **Mostra origine** e **Modifica**. Confermare il lunedì non elimina l’ambiguità dell’orario.

Nomi, coordinate, date e turni della foto privata non sono incorporati nel codice dell’app. Non è stata integrata alcuna Vision AI o API cloud. Il modello OCR rimane incluso nell’APK, il manifest non contiene il permesso Internet e il database non cambia versione.

## Verifiche eseguite

Il 7 ottobre 2026 sono passati 28 test JVM e 10 test Android, senza fallimenti o test saltati. Build debug, lint e controllo ktfmt sono completati; lint riporta zero errori e 20 avvisi. I test Android sono stati eseguiti sull’emulatore API 35 x86_64 con Wi-Fi e dati disattivati. Il manifest dell’APK è stato controllato con `aapt dump permissions`.

I controlli includono mapping dei ritagli, deduplicazione senza fusione delle righe vicine, intestazioni inclinate, date non confuse con turni, recupero del giorno da date coerenti e persistenza delle alternative discordanti dopo conferma del lunedì. Restano coperti calendario, Room, crop verticale e persistenza del profilo dopo ricreazione dell’attività.

ML Kit è stato eseguito sul tabellone sintetico da 2400×1800, sulla sua copia da 1000×750 e su un altro layout generato in memoria con nomi, settimana e turni diversi. La copia ridotta richiede la conferma del lunedì perché alcune date sono lette con un anno errato; il test ricostruisce le sette celle dopo questa conferma e verifica i valori o l’alternativa corretta bloccata.

Il controllo locale facoltativo sulla foto segnalata individua nome, sette colonne e settimana corretta. Sei celle corrispondono ai valori attesi; la domenica conserva due orari discordanti e richiede modifica. Anche il lunedì deve essere confermato nella revisione. Il test rifiuta valori sbagliati esportabili: una cella ambigua passa soltanto se conserva l’alternativa corretta e rimane bloccata. La foto non entra negli asset e la copia temporanea sull’emulatore è stata rimossa dopo il controllo.

L’APK ha `versionName=0.1.1` e `versionCode=2`. SHA-256: `47e77f6e02d073363a45b5bdb770170d2ed8fc65fe404a2e0923869bd455059d`. Il formato Room rimane alla versione 1; l’aggiornamento conserva profilo e storico. La firma è debug.

Il benchmark del solo parser, eseguito dopo la chiusura dell’emulatore, usa 254 token sintetici, cento warmup e mille iterazioni: 7729,2826 ms totali, 7,7293 ms medi, checksum 7000. Ambiente: Intel Core i3-10100F, 32 GiB RAM, Windows 11, JDK 21.0.6, un thread. È uno smoke benchmark senza JMH, separato da ML Kit e dalle foto private; non misura la latenza dell’app sul telefono.

## Limiti

Il parser richiede un tabellone settimanale con nomi a sinistra e giorni italiani in colonne ordinate. Supporta un solo intervallo o una sigla per giorno. Una foto può ancora richiedere conferma delle date, correzione delle celle o una nuova acquisizione. La confidenza è euristica; questi controlli non dimostrano un’accuratezza universale.

Le letture aggiuntive aumentano il lavoro OCR. La latenza su smartphone fisico resta da verificare. La foto privata serve a riprodurre l’errore e controllare il risultato; non è usata per benchmark di prestazioni e non viene inclusa nel repository o negli APK.
