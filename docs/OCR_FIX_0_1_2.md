# Correzione OCR 0.1.2

Gli screenshot del telefono mostrano un contatore numerico prima dell’orario nella cella di martedì. La revisione lo classifica correttamente come contenuto non riconosciuto, ma richiede di riscrivere un orario già leggibile.

Il filtro della versione 0.1.1 rimuoveva numeri isolati soltanto se un altro elemento OCR conteneva già l’intero orario o una sigla. Non copriva un orario diviso in più elementi né contatore e orario uniti nello stesso elemento. Il nuovo test ha riprodotto questo limite prima della correzione.

Il parser ora prova il recupero dopo aver ricomposto il testo della cella. Rimuove soltanto numeri di una o due cifre separati da spazi ai margini; accetta il risultato se contiene una sola lettura completa. Non deduce gli orari dalla durata del turno. Il recupero ha confidenza massima 0,65 e richiede conferma esplicita dopo il confronto con l’origine. La conferma del lunedì mantiene questo requisito.

Il parser per la modifica manuale rimane rigoroso: valori invalidi, testo extra e più intervalli vengono rifiutati. Gli orari discordanti rimangono UNKNOWN e richiedono modifica o eliminazione. **Continua** richiede date confermate, assenza di celle UNKNOWN e verifica delle celle con bassa confidenza.

La modifica riguarda solo il parser locale e il numero di versione dell’app. Foto e testo non vengono inviati a servizi esterni. Non cambiano lo schema Room, i permessi, la pipeline di ritagli OCR o la sincronizzazione del calendario. La correzione non incorpora nomi, coordinate, date o turni della foto segnalata.

L’APK ha `versionName=0.1.2` e `versionCode=3`, con la stessa firma debug della versione precedente. SHA-256: `92e0a8a9590887b4c84fdf6dc497e71107f612572651434ebea4a9c26a752e67`. L’aggiornamento mantiene profilo e storico. Il manifest verificato non contiene il permesso Internet.

## Verifiche eseguite

Il 7 ottobre 2026 sono passati 31 test JVM. I nuovi casi coprono contatori prima e dopo l’orario, contatori e orario nello stesso elemento, orari frammentati, sigle e contenuti che devono restare UNKNOWN. Il controllo include il rifiuto dell’esportazione prima della verifica della cella recuperata e la persistenza del blocco dopo conferma del lunedì.

Sono passati tutti gli 11 test Android distinti, eseguiti sull’emulatore API 35 x86_64 con Wi-Fi e dati disattivati. La prima esecuzione ha superato dieci test e ha individuato un errore nello scorrimento del nuovo test UI: cercava il pulsante fuori schermo prima che la lista lo componesse. Dopo la correzione del solo test, la riesecuzione di `ReviewUiTest` è passata. Il test verifica separatamente il blocco per date non confermate, cella recuperata non verificata e orari discordanti, quindi il passaggio al calendario quando tutti i requisiti sono soddisfatti.

La regressione privata facoltativa individua sette colonne e la settimana corretta; la domenica resta UNKNOWN con due alternative. Il valore letto dal telefono, con contatore prima dell’orario, è coperto dai test geometrici sintetici. Non si dichiara che il nuovo APK sia già stato provato sul telefono fisico. La copia temporanea della foto sull’emulatore è stata rimossa dopo il controllo; la foto non entra negli asset o nel repository.

Build debug, lint e controllo ktfmt sono completati. Lint riporta zero errori e 20 avvisi. Il recupero non garantisce lettura automatica di qualsiasi tabellone: i formati compatibili e le conferme manuali rimangono quelli descritti in [SHIFT_PARSER.md](SHIFT_PARSER.md).

Il benchmark del solo parser, dopo la chiusura dell’emulatore, usa 254 token sintetici, cento warmup e mille iterazioni: 7171,6522 ms totali, 7,1717 ms medi, checksum 7000. Ambiente: Intel Core i3-10100F, 32 GiB RAM, Windows 11, JDK 21.0.6, un thread. È uno smoke benchmark senza JMH; non misura ML Kit o le foto inviate e non dimostra un miglioramento delle prestazioni.
