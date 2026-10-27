# Parser della settimana

## Nome e riga

La normalizzazione rimuove accenti, uniforma maiuscole e spazi. Il confronto ordina le parole e usa `1 - distanzaLevenshtein / lunghezzaMassima`; la soglia è 0,76. Due candidati con distanza di punteggio inferiore a 0,08 richiedono scelta manuale. Non viene usato substring matching. L’app usa soltanto il nome e cognome inseriti; gli alias conservati dalle vecchie versioni non partecipano alla ricerca. L’API del parser mantiene il parametro alias per compatibilità.

I token dei nomi sono quelli a sinistra del confine del primo giorno e sotto l’intestazione. Si raggruppano per coordinata verticale relativa all’altezza dei box. La fascia del dipendente termina al punto medio fra i nomi sopra e sotto. In assenza di vicini si usa l’altezza del nome. Se il match fallisce, la UI propone le righe leggibili nell’area dei nomi.

## Intestazioni, colonne e date

I giorni riconosciuti includono nomi italiani accentati/non accentati, abbreviazioni a tre lettere e piccoli errori con similarità almeno 0,82. Il parser sceglie la fascia di intestazione con più giorni distinti, valutando anche linee inclinate con pendenza normalizzata massima 0,15 e residuo entro 0,7 altezze del token. I separatori delle colonne sono i punti medi fra i centri dei giorni: nessuna coordinata della fixture è hardcoded.

Le date vengono cercate tra l’intestazione e la prima riga dei nomi, entro tre altezze del token del giorno. Questo impedisce di interpretare come data un turno `22-01` subito sotto l’intestazione. La fascia del dipendente segue una linea ancorata al nome, scelta minimizzando la distanza dei token vicini nelle colonne, con pendenza normalizzata limitata a 0,12. I ritagli delle celle includono l’inclinazione stimata. Il crop a quattro angoli resta necessario per prospettive forti; questo adattamento gestisce l’inclinazione residua.

Un giorno illeggibile può essere recuperato dalla data esplicita della sua colonna: servono almeno due date con anno nella zona dell’intestazione, un unico lunedì coerente e un ordine compatibile con i giorni già letti. Il parser ricava il weekday dalla data e la posizione dal suo box. Questo recupero richiede sempre la conferma del lunedì; non inventa una colonna dalla sola spaziatura.

I formati data includono `5-ott-2026`, `05/10`, `05-10`, `5 OCT`, `05 OTT`. Le date esplicite prevalgono sull’inferenza. Date senza anno usano l’anno di riferimento per una proposta, che richiede conferma. I lunedì dedotti dai diversi giorni devono coincidere e il weekday deve essere corretto. Una conferma esplicita del lunedì sostituisce le date OCR in conflitto e assegna le date a tutte le colonne rilevate. Con meno di sette intestazioni la UI richiede conferma; i giorni mancanti possono essere aggiunti manualmente.

## Celle e orari

Si leggono i token nella fascia della riga e nell’intervallo della colonna, ordinati da sinistra a destra. I numeri isolati ausiliari vengono esclusi quando nella stessa colonna esiste già un orario completo o una sigla. Un numero solo resta UNKNOWN e non può essere esportato. Elementi separati `19`, `-`, `01` vengono ricomposti se non c’è un’altra cella valida.

Dalla versione 0.1.2, se il testo ricomposto resta UNKNOWN, il parser verifica anche contatori numerici ai margini. Può rimuovere soltanto numeri di una o due cifre separati da spazi, prima o dopo il contenuto. Deve restare una sola lettura completa e valida. Questo copre sia elementi OCR separati sia un elemento che unisce contatore e orario. Il recupero ha confidenza massima 0,65 e richiede conferma esplicita; confermare le date non conferma l’orario. Due intervalli, note o valori invalidi restano UNKNOWN. Il parser usato per la modifica manuale continua a richiedere l’intero testo valido.

Il pattern accetta due ore separate da trattino, con minuti opzionali separati da `:` o `.`. Ore 0–23, più 24:00 come alias di 00:00, minuti 0–59, al massimo otto cifre e nessuna lettera nell’intervallo. Il limite riguarda le cifre, non trattini, punti, due punti o spazi. `22-1` e `22-01` rappresentano entrambi 22:00–01:00 del giorno successivo. Il match riguarda l’intera cella; testo extra e più intervalli non vengono ignorati. Inizio uguale a fine è ambiguo e viene rifiutato. Fine inferiore a inizio significa giorno seguente; fine `00:00` segue la stessa regola. `19-01` e `23:45-5:45` durano sei ore nel calendario locale in assenza di cambio DST.

Dalla versione 0.1.4, `OcrShiftRecovery` propone anche un solo cambiamento su una lettura OCR invalida: sostituzione o rimozione di `O`, `I`, `L`, `|`, oppure rimozione di una cifra ripetuta adiacente in un gruppo di esattamente tre cifre. Le proposte devono soddisfare l’intera grammatica. Se le trasformazioni producono valori diversi, rimangono alternative UNKNOWN; se producono un solo valore, la cella conserva il testo OCR originale e richiede verifica esplicita. Non si tagliano cifre arbitrarie per rientrare nel limite di otto. Input oltre 64 caratteri e letture con `/` non vengono recuperati. Il parser manuale non applica questi cambiamenti.

Solo `X`, normalizzata anche da `x`, diventa CODE con significato fisso di riposo. FE, CORSO, A.G., XX e altre lettere diventano UNKNOWN e richiedono modifica o esclusione. Vuoto diventa EMPTY. `X` produce un evento di intera giornata intitolato Giorno di riposo, con mezzanotte UTC e fine esclusiva del giorno successivo. Le vecchie legende non possono alterare questa regola.

## Confidenza

Fino alla versione 0.1.3 il punteggio era `0,25 OCR + 0,25 nome + 0,20 colonna + 0,15 data + 0,15 sintassi`. Soltanto un quarto dipendeva dalla qualità OCR e gli altri fattori potevano aumentare il punteggio di una lettura debole.

Dalla 0.1.4 il punteggio è il minimo fra qualità dei token OCR, match del nome e associazione alla colonna. Si considera il token meno affidabile; un box che attraversa il separatore stimato limita l’associazione a 0,65. Le celle recuperate hanno un massimo di 0,65; UNKNOWN un massimo di 0,5. La conferma delle date viene gestita dai blocchi della settimana e non aumenta il punteggio dell’orario. La UI mostra `Punteggio lettura: N/100`, senza simbolo percentuale. Il formato abbreviato di un’ora non introduce una penalità.

Ogni token con confidenza mancante, negativa o non finita usa un valore prudente di 0,65 prima di calcolare il minimo. Quindi un frammento senza punteggio non viene nascosto dai frammenti con OCR alto. Un OCR esplicitamente debole rimane debole: 0,12 non viene aumentato dal fallback.

Da 0,90 incluso, una cella valida prosegue senza conferma individuale. Sotto 0,90 il calendario resta bloccato finché l’utente controlla e conferma oppure modifica la cella. UNKNOWN richiede correzione o eliminazione e non può essere confermato come valido. I punteggi non finiti richiedono verifica. La soglia è definita in `ShiftCell.AUTO_ACCEPT_CONFIDENCE` e viene applicata anche dal modello del calendario; non dipende soltanto dalla UI. Le date mancanti o incoerenti richiedono un controllo separato. Il punteggio non è una probabilità di correttezza e non è calibrato su un dataset reale.

La conversione della confidenza ML Kit conserva la rappresentazione decimale canonica del `Float`: `0.90f` diventa `0.90`, senza il residuo `0.899999976…` prodotto dall’allargamento diretto a `Double`. Non si usa una tolleranza per accettare valori inferiori alla soglia. `ShiftCell.readingScore` tronca le cifre decimali su 100 senza arrotondare verso l’alto: 0,899999 resta 89/100 e richiede verifica; 0,58 mostra 58/100 invece di 57 per un residuo binario della moltiplicazione.
