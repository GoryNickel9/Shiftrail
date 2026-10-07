# Pipeline OCR

La decodifica controlla il formato e legge immagini fino a 4096 pixel sul lato maggiore tramite subsampling. Applica rotazioni e riflessioni EXIF. La cattura CameraX richiede una risoluzione vicina a 2560×1920 con fallback del dispositivo, autofocus al tocco e flash quando disponibile.

L’utente seleziona quattro angoli in senso orario. `setPolyToPoly` costruisce una trasformazione proiettiva; quadrilateri incrociati o degeneri vengono rifiutati. Si mantengono il bitmap originale e quello rettificato durante la revisione. Il fingerprint SHA-256 usa dimensioni e pixel RGB dell’originale decodificato: riconosce reimportazioni identiche, non foto diverse dello stesso foglio.

ML Kit usa `com.google.mlkit:text-recognition:16.0.1`, che include il modello latino nell’app. Ogni elemento diventa un token con bounding box normalizzato al bitmap analizzato. Il parser riceve tutti i token e non una stringa appiattita. Si esegue OCR sul rettificato e su una versione grigia con contrasto moderato; la selezione deterministica confronta numero di celle valide, match nome, date e problemi. Le coordinate restano identiche tra questi due bitmap. Una trasformazione non viene selezionata soltanto perché aumenta la quantità di testo.

Debug mostra token blu, riga verde e separatori rossi. **Mostra origine** ritaglia la cella dal bitmap rettificato senza enhancement. L’originale completo è visibile nel crop e può essere esportato soltanto con scelta esplicita. Nessuna foto è inclusa nel backup.

Il confronto fra due OCR aumenta latenza e RAM. L’app misura il tempo OCR+parser con il clock monotono, mostrato in Debug. Un target di pochi secondi su smartphone fisico deve ancora essere misurato. Foto sfocate, ombre marcate e prospettiva residua richiedono nuova acquisizione o correzione manuale.
