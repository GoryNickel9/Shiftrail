# Correzione OCR 0.1.3

La rilettura isolata della domenica perdeva una cifra iniziale già presente nella lettura per ritagli sovrapposti. Il sistema conservava correttamente il conflitto come UNKNOWN, ma richiedeva una correzione evitabile. I bordi della cella sono stimati dai centri delle intestazioni: non coincidono sempre con i divisori del foglio o con i margini delle parole.

`CellOcrPlanner` ricava il crop dai box completi degli elementi OCR della cella e aggiunge margini proporzionali all’altezza del testo. Una parola che supera un divisore stimato viene mantenuta intera. Gli elementi riconoscibili come orario o sigla vengono preferiti ai contatori separati. L’ingrandimento usa l’altezza mediana dei box, mira a 24 pixel e rimane tra 1× e 4×. La [documentazione ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android#input-image-guidelines) indica che oltre 24 pixel per carattere generalmente non si ottengono benefici; questo valore è un criterio di preprocessing, non una garanzia di accuratezza.

Un primo tentativo con il solo crop stretto ha recuperato la domenica ma lasciato il separatore del lunedì non riconosciuto. Il test sulla foto ha segnalato la regressione. Ora, se una cella UNKNOWN resta UNKNOWN, il riconoscitore tenta anche la fascia originale con più contesto. La selezione usa la qualità della tabella e mantiene gli orari discordanti da correggere. **Mostra origine** usa i box completi con gli stessi margini, mostrando i pixel senza enhancement.

Nei log del test sintetico, un tentativo scartato cambiava l’inclinazione stimata e associava altri giorni alla riga vicina. `OcrGeometry.preservesOtherCells` ora rifiuta ogni rilettura locale che cambia date, orari, sigle o testo UNKNOWN degli altri giorni. Questo controllo viene applicato sia al crop stretto sia alla lettura con più contesto.

Le regole dipendono dalla geometria riconosciuta e dalle dimensioni dell’immagine. Non incorporano dati attesi della foto, non deducono un orario dalla durata e non inviano immagini a servizi esterni. Non cambiano lo schema Room, i permessi o la sincronizzazione del calendario.

## Verifica della lettura

La regressione locale sulla foto segnalata legge tutte e sette le celle della riga richiesta con i valori attesi, senza celle UNKNOWN. Il lunedì viene recuperato tramite la lettura con più contesto; la domenica mantiene entrambe le cifre iniziali. Le date richiedono ancora la conferma della settimana. Questa verifica riguarda l’emulatore API 35 x86_64, non il telefono fisico, e non costituisce una misura di precisione generale.

Il nuovo tabellone sintetico usa nome, settimana, orari e layout diversi: intestazioni decentrate, testo ridotto a metà risoluzione, righe adiacenti e contatori numerici. Sei celle risultano corrette; nell’ultima ML Kit unisce il contatore all’ultima cifra dei minuti, producendo `23:30-02:306`. Il primo test, che richiedeva sette letture corrette, è fallito. Il controllo finale verifica le sei letture corrette e richiede che l’ultima sia corretta oppure resti UNKNOWN con revisione obbligatoria. Non è stata modificata l’immagine per facilitare il riconoscimento.

I quattro nuovi test JVM verificano parole oltre il divisore stimato, esclusione dei contatori separati, frammenti, alternative discordanti, margini ai bordi, limiti di ingrandimento e rifiuto delle modifiche agli altri giorni. Il test della foto privata accetta il parametro facoltativo `requireUnambiguous=true` per richiedere tutte le celle leggibili; nome, lunedì e valori attesi rimangono argomenti di esecuzione. La foto non entra negli asset o nel repository. Il callback interno delle letture è usato dai test per diagnosticare i passaggi; l’app non lo imposta.

## Build e test finali

Il 7 ottobre 2026 sono passati 35 test JVM e tutti i 12 test Android, senza fallimenti o test saltati. L’esecuzione finale include `requireUnambiguous=true` sulla regressione privata. I test Android usano l’emulatore API 35 x86_64 con Wi-Fi e dati disattivati. Sono passati anche build debug, lint e controllo ktfmt; lint riporta zero errori e 20 avvisi. Il comando completo esegue `assembleDebug test lint checkKotlinFormat connectedDebugAndroidTest`, con gli argomenti della foto passati soltanto al runner.

I controlli esistenti coprono anche gli altri tabelloni sintetici, calendario, Room, persistenza del profilo, crop verticale e blocchi della revisione. Il report Android è in `app/build/reports/androidTests/connected/debug/`; quello JVM è in `core/build/reports/tests/test/`. La copia temporanea della foto nell’emulatore è stata rimossa dopo le verifiche.

## Limiti

Gli orari discordanti restano UNKNOWN e richiedono modifica o eliminazione. I contatori fusi senza spazi non vengono separati mediante supposizioni sui numeri. La pipeline aggiunge una lettura con più contesto soltanto per celle ancora illeggibili; non viene dichiarato un miglioramento dei tempi OCR. Non sono stati eseguiti benchmark sulle immagini private. Sono ancora necessarie prove su altri tabelloni reali autorizzati e sul telefono dell’utente.

La versione usa `versionName=0.1.3` e `versionCode=4`, con la stessa firma debug della 0.1.2. SHA-256: `39d4b799e6b653176dc8f0ace4ff5f7428b99a0b314681ba30426874a4dd2c1c`. L’aggiornamento mantiene profilo e storico. Il manifest verificato con `aapt` non contiene il permesso Internet.
