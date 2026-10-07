# Shiftrail 0.2.1

## Cambiamenti

Storico e la schermata di diagnostica sono rimossi dalla navigazione. Impostazioni contiene soltanto Nome e cognome, con Salva fisso in basso. Il campo accetta da uno a 160 caratteri dopo il trim. L’app cerca soltanto il nome inserito; gli alias delle versioni precedenti non partecipano alla ricerca. Le vecchie opzioni degli eventi rimangono conservate, per evitare cambiamenti involontari ai dati già configurati.

`X` è l’unica lettera accettata nella cella di un giorno. Anche `x` viene normalizzata a `X`; FE, CORSO, A.G., XX e altre lettere diventano UNKNOWN. Le proposte di recupero OCR possono ancora correggere un carattere confuso con una cifra, ma producono soltanto orari validi e richiedono controllo esplicito. Il limite degli orari resta otto cifre.

Una X valida produce un evento intera giornata intitolato **Giorno di riposo**. Il provider Android riceve mezzanotte UTC e fine esclusiva al giorno successivo. La regola è fissa: le vecchie legende non possono ignorare X o trasformarla in un turno con orari. La reimportazione e il passaggio turno–riposo mantengono l’identità dell’evento. La soglia di conferma resta 90/100 inclusivo.

Il nome dell’app rimane Shiftrail. Il pacchetto consegnato si chiama `Shiftrail-0.2.1.apk`.

## Verifica

La prima formattazione ha rilevato una virgola superflua nel ramo CODE della scheda di revisione. La virgola è stata rimossa prima di rieseguire la build.

49 test JVM passati, senza errori o test saltati. I controlli coprono X e le lettere rifiutate, vecchie legende, eventi intera giornata e date nei cambi DST. Formattazione, compilazione dell’app e del pacchetto dei test passate. Lint: zero errori e 19 warning preesistenti, 16 sulle dipendenze e tre suggerimenti KTX.

La suite Android completa è passata con `OK (15 tests)`, senza test saltati. Il test sul provider verifica titolo Giorno di riposo, flag intera giornata, estremi UTC, reimportazione invariata e passaggio turno–riposo–turno con lo stesso ID. Il test UI verifica la scritta Giorno di riposo, la prosecuzione a 90/100 e il blocco di FE. La home non presenta Storico; Impostazioni ha esattamente un campo modificabile e mantiene il nome dopo salvataggio e ricreazione dell’attività.

Le quattro fixture OCR e la regressione privata sono passate. Le sigle non ammesse nelle fixture rimangono UNKNOWN, mentre la regressione privata conserva sette valori corretti con `requireUnambiguous=true`. La foto viene letta da un file temporaneo dell’emulatore ed è rimossa dopo la verifica; gli input attesi sono argomenti del runner, non dati incorporati nell’app. Il log completo resta negli artifact locali ignorati da Git.

Durante una cattura successiva ai test, l’emulatore ha mostrato un avviso di mancata risposta di System UI. Dopo Attendi, la navigazione è ripresa e le schermate sono state ricatturate. L’avviso riguardava System UI, non il processo Shiftrail; nessun errore Android Runtime dell’app è stato rilevato nella verifica.

Il manifest verificato riporta Shiftrail, `versionCode=7` e `versionName=0.2.1`, senza permessi Internet o stato della rete. Il pacchetto è 57.880.922 byte; SHA-256 `0efd76cdada247f4325ffa51b0955c3711823640699bb462cf1c619a7d1e6419`. Il certificato conserva SHA-256 `24dd3588ea4ba44f365d2a76d84c406e5d7c358d0d254bd5c748c312553bfaa0`.

## Compatibilità

Application ID `it.turni.app`, schema Room versione 1 e chiave di firma rimangono quelli delle versioni precedenti. Le tabelle delle settimane e delle legende e il campo alias del profilo sono mantenuti per compatibilità; rimuovere una sezione UI non cancella gli eventi del calendario o i dati necessari per evitare duplicati. Il motore OCR rimane locale e usa lo stesso modello incluso nell’APK. Nessuna foto privata è inclusa nel pacchetto o nel repository.

La verifica usa un emulatore Android 15/API 35. Smartphone fisici e TalkBack richiedono una prova separata. Non sono stati eseguiti benchmark sulle foto private.
