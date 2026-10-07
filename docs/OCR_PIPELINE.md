# Pipeline OCR

La decodifica controlla il formato e legge immagini fino a 4096 pixel sul lato maggiore tramite subsampling. Applica rotazioni e riflessioni EXIF. La cattura CameraX richiede una risoluzione vicina a 2560×1920 con fallback del dispositivo, autofocus al tocco e flash quando disponibile.

L’utente seleziona quattro angoli in senso orario. `setPolyToPoly` costruisce una trasformazione proiettiva; quadrilateri incrociati o degeneri vengono rifiutati. Si mantengono il bitmap originale e quello rettificato durante la revisione. Il fingerprint SHA-256 usa dimensioni e pixel RGB dell’originale decodificato: riconosce reimportazioni identiche, non foto diverse dello stesso foglio.

ML Kit usa `com.google.mlkit:text-recognition:16.0.1`, che include il modello latino nell’app. Ogni elemento diventa un token con bounding box normalizzato al bitmap analizzato. Il parser riceve tutti i token e non una stringa appiattita. Si esegue OCR sul rettificato e su una versione grigia con contrasto moderato; la selezione deterministica confronta copertura delle colonne, celle valide, match nome, date e problemi. La copertura dei giorni pesa più del numero di celle valide: una lettura parziale non deve vincere soltanto perché contiene meno celle ambigue. Le coordinate restano identiche tra questi due bitmap.

Quando mancano nome o sette intestazioni, oppure una cella resta UNKNOWN, si esegue una lettura per ritagli sovrapposti. Larghezza e altezza dipendono dalle dimensioni del bitmap; l’ingrandimento arriva a 3×. Ogni ritaglio riceve un bordo bianco, i token tagliati ai bordi vengono esclusi e le coordinate vengono riportate al bitmap analizzato. Si mantiene un solo riconoscitore e si ricicla un ritaglio alla volta. Le letture sovrapposte dello stesso testo vengono deduplicate geometricamente, preservando parole uguali su righe diverse.

Dopo la localizzazione della riga, il percorso per testo fitto rilegge le celle del dipendente separatamente, fino a 4×. Due letture valide con orari diversi non vengono risolte tramite una durata presunta del turno: il testo conserva entrambe le alternative, separate da ` / `, e diventa UNKNOWN. La conferma delle date mantiene questo blocco. Il codice non contiene nomi, orari attesi o coordinate della foto segnalata.

Se l’intestazione resta incompleta, si rilegge una fascia calcolata dai box dei giorni trovati. Questa fascia usa ritagli orizzontali ingranditi con meno righe di testo. Il confronto finale conserva anche le letture discordanti tra l’immagine originale e quella con contrasto.

Debug mostra token blu, celle verdi e separatori rossi, insieme all’indicazione del percorso per ritagli. **Mostra origine** ritaglia la cella dal bitmap rettificato senza enhancement. L’originale completo è visibile nel crop e può essere esportato soltanto con scelta esplicita. Nessuna foto è inclusa nel backup.

Le letture aggiuntive aumentano latenza e RAM. L’app misura il tempo OCR+parser con il clock monotono, mostrato in Debug. Un target di pochi secondi su smartphone fisico deve ancora essere misurato. Foto sfocate, ombre marcate e prospettiva residua richiedono nuova acquisizione o correzione manuale.

Un tabellone compatibile dispone i nomi a sinistra e i giorni della settimana in colonne ordinate da lunedì a domenica. Il parser cerca giorni e date italiani e gestisce un solo intervallo o una sigla per cella. Dimensioni e coordinate vengono ricavate dal testo riconosciuto; non si usa un modello del foglio specifico. Altri ordinamenti, più settimane affiancate e più intervalli nello stesso giorno richiedono estensioni del parser.
