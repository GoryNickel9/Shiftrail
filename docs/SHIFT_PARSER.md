# Parser della settimana

## Nome e riga

La normalizzazione rimuove accenti, uniforma maiuscole e spazi. Il confronto ordina le parole e usa `1 - distanzaLevenshtein / lunghezzaMassima`. Nome e alias competono; la soglia è 0,76. Due candidati con distanza di punteggio inferiore a 0,08 richiedono scelta manuale. Non viene usato substring matching: un cognome condiviso non identifica il dipendente senza un alias esplicito.

I token dei nomi sono quelli a sinistra del confine del primo giorno e sotto l’intestazione. Si raggruppano per coordinata verticale relativa all’altezza dei box. La fascia del dipendente termina al punto medio fra i nomi sopra e sotto. In assenza di vicini si usa l’altezza del nome. Se il match fallisce, la UI propone le righe leggibili nell’area dei nomi.

## Intestazioni, colonne e date

I giorni riconosciuti includono nomi italiani accentati/non accentati, abbreviazioni a tre lettere e piccoli errori con similarità almeno 0,82. Il parser sceglie la fascia di intestazione con più giorni distinti, valutando anche linee inclinate con pendenza normalizzata massima 0,15 e residuo entro 0,7 altezze del token. I separatori delle colonne sono i punti medi fra i centri dei giorni: nessuna coordinata della fixture è hardcoded.

Le date vengono cercate tra l’intestazione e la prima riga dei nomi, entro tre altezze del token del giorno. Questo impedisce di interpretare come data un turno `22-01` subito sotto l’intestazione. La fascia del dipendente segue una linea ancorata al nome, scelta minimizzando la distanza dei token vicini nelle colonne, con pendenza normalizzata limitata a 0,12. I ritagli delle celle includono l’inclinazione stimata. Il crop a quattro angoli resta necessario per prospettive forti; questo adattamento gestisce l’inclinazione residua.

Un giorno illeggibile può essere recuperato dalla data esplicita della sua colonna: servono almeno due date con anno nella zona dell’intestazione, un unico lunedì coerente e un ordine compatibile con i giorni già letti. Il parser ricava il weekday dalla data e la posizione dal suo box. Questo recupero richiede sempre la conferma del lunedì; non inventa una colonna dalla sola spaziatura.

I formati data includono `5-ott-2026`, `05/10`, `05-10`, `5 OCT`, `05 OTT`. Le date esplicite prevalgono sull’inferenza. Date senza anno usano l’anno di riferimento per una proposta, che richiede conferma. I lunedì dedotti dai diversi giorni devono coincidere e il weekday deve essere corretto. Una conferma esplicita del lunedì sostituisce le date OCR in conflitto e assegna le date a tutte le colonne rilevate. Con meno di sette intestazioni la UI richiede conferma; i giorni mancanti possono essere aggiunti manualmente.

## Celle e orari

Si leggono i token nella fascia della riga e nell’intervallo della colonna, ordinati da sinistra a destra. I numeri isolati ausiliari vengono esclusi quando nella stessa colonna esiste già un orario completo o una sigla. Un numero solo resta UNKNOWN e non può essere esportato. Elementi separati `19`, `-`, `01` vengono ricomposti se non c’è un’altra cella valida.

Dalla versione 0.1.2, se il testo ricomposto resta UNKNOWN, il parser verifica anche contatori numerici ai margini. Può rimuovere soltanto numeri di una o due cifre separati da spazi, prima o dopo il contenuto. Deve restare una sola lettura completa e valida. Questo copre sia elementi OCR separati sia un elemento che unisce contatore e orario. Il recupero ha confidenza massima 0,65 e richiede conferma esplicita; confermare le date non conferma l’orario. Due intervalli, note o valori invalidi restano UNKNOWN. Il parser usato per la modifica manuale continua a richiedere l’intero testo valido.

Il pattern accetta due ore separate da trattino, con minuti opzionali separati da `:` o `.`. Ore 0–23, minuti 0–59. Il match riguarda l’intera cella; testo extra e più intervalli non vengono ignorati. Inizio uguale a fine è ambiguo e viene rifiutato. Fine inferiore a inizio significa giorno seguente; fine `00:00` segue la stessa regola. `19-01` e `23:45-5:45` durano sei ore nel calendario locale in assenza di cambio DST.

Sigle alfabetiche e punti diventano CODE senza significato dedotto. Vuoto diventa EMPTY; il resto UNKNOWN. Gli eventi per CODE dipendono soltanto dalla legenda salvata.

## Confidenza

Il punteggio è `0,25 OCR + 0,25 nome + 0,20 colonna + 0,15 data + 0,15 sintassi`, limitato a [0,1]. OCR mancante vale 0,75. Un box che attraversa il separatore riduce il fattore colonna a 0,65; anno non esplicito e non confermato riduce data a 0,6; sintassi non valida vale zero. UNKNOWN ha un massimo di 0,5.

Da 0,90 la UI mostra una cella affidabile secondo l’euristica; da 0,70 a 0,89 invita al controllo; sotto 0,70 blocca l’esportazione fino a conferma esplicita. UNKNOWN richiede correzione o eliminazione anche dopo un tentativo di conferma. Il punteggio non è una probabilità di correttezza e non è calibrato su un dataset reale.
