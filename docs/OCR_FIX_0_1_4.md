# Correzione OCR 0.1.4

Lo screenshot segnala un contatore seguito da un intervallo con un carattere in più nella seconda ora. Il limite di otto cifre e il divieto di lettere erano già impliciti nel pattern rigoroso degli orari: servivano a rifiutare la lettura, ma non a proporre un recupero. Ora il limite è esplicito in `ShiftParser.MAX_TIME_DIGITS` e viene applicato anche ai candidati di recupero.

`22-1` è un intervallo valido: rappresenta 22:00–01:00, come `22-01`. Lo zero iniziale omesso non cambia l’orario. Il 79% mostrato dalla versione precedente era un punteggio euristico composto da OCR 25%, nome 25%, colonna 20%, data 15% e sintassi 15%. Con questa media, gli altri segnali potevano mascherare una lettura debole delle cifre. Dallo screenshot non si possono ricostruire i singoli componenti del valore 79.

## Proposte di recupero

`OcrShiftRecovery` interviene soltanto su testo invalido. Conserva la rimozione dei contatori ai margini separati da spazi e propone al massimo un cambiamento nel testo dell’intervallo: sostituzione o rimozione di `O`, `I`, `L`, `|`, oppure rimozione di una cifra ripetuta adiacente in un gruppo di esattamente tre cifre. `21I:30` può così proporre `21:30`; `211:30` può proporre `21:30`. I candidati devono essere intervalli completi, con ore 0–23, minuti 0–59 e al massimo otto cifre.

Una sola interpretazione fra quelle generate diventa una proposta con punteggio massimo 65/100 e verifica obbligatoria. Il testo OCR originale rimane visibile nella scheda. Interpretazioni diverse rimangono UNKNOWN: per esempio `I8:30-23:30` può indicare 18:30 oppure 08:30 e richiede modifica. Il limite di otto cifre non autorizza a tagliare numeri arbitrari: `23:30-02:306` rimane UNKNOWN. Due errori nello stesso intervallo non vengono corretti in sequenza.

Il recupero ha un limite di 64 caratteri e non interviene su letture contenenti `/`. La modifica manuale continua a usare soltanto il parser rigoroso. Le sigle `X`, `FE`, `CORSO` e `A.G.` rimangono CODE; il divieto di lettere riguarda gli orari. Le proposte vengono rilette sui pixel prima di completare l’estrazione, con gli stessi controlli sui giorni vicini introdotti nella 0.1.3.

## Punteggio della lettura

Il nuovo punteggio usa il minimo fra qualità dei token OCR, match del nome e associazione alla colonna. Il token meno affidabile limita il risultato. Un box che attraversa un divisore stimato limita l’associazione a 65/100. Le date restano un requisito separato: confermare la settimana non rende più affidabili le cifre. La schermata mostra un punteggio su 100, senza simbolo percentuale. Il valore rimane euristico e non calibrato come probabilità di correttezza.

Un token senza confidenza valida limita il punteggio a 65/100, anche quando gli altri frammenti hanno un OCR alto. Valori deboli presenti, come 12/100, rimangono deboli e non vengono rialzati dal fallback.

Il testo originale delle proposte è un campo facoltativo di `ShiftCell`, usato soltanto durante la revisione. Non viene aggiunto al database Room e non richiede una migrazione dello schema. L’app continua a elaborare foto e testo localmente.

## Verifiche

I nuovi test JVM controllano recuperi unici, interpretazioni concorrenti, limite di cifre, assenza di lettere nei candidati, input lunghi, rifiuto del taglio arbitrario, parser manuale rigoroso, punteggi deboli o assenti, equivalenza fra `22-1` e `22-01` e rifiuto dell’esportazione prima della verifica. Il test UI controlla anche la lettura originale e l’etichetta del punteggio.

Il 7 ottobre 2026 sono passati 42 test JVM e tutti i 12 test Android, senza fallimenti o test saltati. La verifica finale esegue `assembleDebug test lint checkKotlinFormat connectedDebugAndroidTest`, con gli argomenti della foto forniti soltanto al runner e `requireUnambiguous=true`. Build debug e controllo ktfmt sono completati; lint riporta zero errori e 20 avvisi. I test Android usano l’emulatore API 35 x86_64 con Wi-Fi e dati disattivati.

La foto privata mantiene sette valori corretti, senza UNKNOWN; le date richiedono ancora conferma. Nell’emulatore, la cella finale ha punteggio 65/100 perché il box attraversa il divisore stimato e richiede verifica, pur avendo un orario valido. La sigla `X` richiede anch’essa verifica per il punteggio OCR sotto 70/100. Questo risultato riguarda l’emulatore e non conferma il comportamento sul telefono fisico.

Gli altri tabelloni sintetici restano coperti. Quello con contatori adiacenti produce sei celle corrette e una UNKNOWN per il contatore fuso nei minuti: `23:30-02:306`. Il test del martedì segnalato verifica sia il carattere `I` sia la cifra duplicata `1`, senza adattare nomi o geometria della foto. La copia temporanea della foto nell’emulatore è stata rimossa. Non sono stati eseguiti benchmark sulle immagini private.

Il benchmark del solo parser, eseguito dopo la chiusura dell’emulatore, usa 254 token sintetici, cento warmup e mille iterazioni: 7633,8016 ms totali, 7,6338016 ms medi, checksum 7000. Ambiente: Intel Core i3-10100F, 31,94 GiB RAM visibili, Windows 11 Home, JDK 21.0.6, Kotlin 2.1.20 con target JVM 17, un thread. La configurazione è fissa e non usa sampling. È uno smoke benchmark su token validi; non misura ML Kit, tutti i percorsi di recupero o la latenza sul telefono. Non viene dichiarato un miglioramento delle prestazioni.

## APK

`versionName=0.1.4`, `versionCode=5`. SHA-256: `6f808b8a767a8d6027545a3fc6c71e2d8ab53c39a24ea3d53cd736f74dea87a9`. La firma debug è la stessa delle versioni precedenti; l’aggiornamento mantiene profilo e storico. Il manifest verificato con `aapt` non contiene il permesso Internet. I valori di confidenza già salvati nello storico restano quelli della versione che li ha generati.
