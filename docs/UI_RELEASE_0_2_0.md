# Shiftrail 0.2.0 — report

## Cambiamenti

Nome Shiftrail scelto dall’utente, nuova icona vettoriale e illustrazione raster della home. Il riconoscimento delle foto rimane OCR locale. Il [prompt dell’immagine](BRANDING.md) e il [flusso UI](UI_UX.md) sono documentati separatamente.

La soglia di revisione è 90/100 inclusivo: una lettura valida a 90 non chiede conferma; sotto 90 serve verifica o modifica. UNKNOWN non può essere confermato e le date dubbie hanno un controllo separato. La confidenza ML Kit viene convertita conservando il valore decimale del Float, per evitare che 0,90 diventi 0,899999976. Il punteggio mostrato non arrotonda valori sotto soglia a 90.

La revisione e le impostazioni hanno un’azione principale fissa; la sincronizzazione mantiene visibile il pulsante di applicazione. Le azioni del giorno vanno a capo con caratteri grandi. Eliminazione e dettagli sono nel menu del giorno. Home, Storico e Impostazioni usano la navigazione inferiore. Le impostazioni raggruppano profilo, eventi, legenda e opzioni avanzate. La modalità scura segue Android.

## Verifiche

47 test JVM passati: parser, geometria, recupero OCR, calendario, soglia, conversione Float e visualizzazione decimale. Build debug e APK dei test compilati; `checkKotlinFormat` passato. Lint: zero errori e 20 warning preesistenti, 16 sulle versioni delle dipendenze e quattro suggerimenti KTX. Le risorse inutilizzate introdotte dal cambiamento UI sono state rimosse e il contatore dei giorni usa plurali Android.

Una suite completa di 14 test Android è passata con Gradle prima delle ultime correzioni decimali. Il primo tentativo UI aveva due errori nel test: scorrimento verso Salva già fisso e selezione non univoca del pulsante ripetuto nella lista. Le selezioni sono state corrette e l’intera suite è stata rieseguita. Il test del profilo è stato eseguito anche dopo la modifica dell’anno nello storico.

La build finale è stata installata localmente e verificata con il runner Android, senza build concorrente: `OK (14 tests)`, senza test saltati. I 47 test JVM, build, lint e formato erano già passati sulla stessa versione del codice. La regressione privata mantiene sette valori corretti con `requireUnambiguous=true`; usa la foto soltanto in `/data/local/tmp`, con attese passate al runner. La foto non è inclusa nel repository o negli APK e il test non misura prestazioni. Il log completo del runner resta negli artifact locali ignorati da Git.

Le schermate della revisione usano dati sintetici e verificano sette letture a 90/100 senza conferma, oltre a un layout da 320 dp con caratteri al 160%. Durante una cattura della home è comparso un avviso «System UI isn’t responding» dell’emulatore; l’avviso è stato chiuso con Attendi e la cattura ripetuta. L’emulatore è stato riavviato prima della verifica finale.

Il controllo visivo ha rilevato anche un’illustrazione vuota fino al primo scorrimento in alcune aperture. Il PNG nell’APK era integro; la causa del rendering non è stata isolata. La home ora decodifica un bitmap software ridotto in sRGB su `Dispatchers.IO` e pubblica il risultato nello stato Compose. Il test della home verifica i pixel del framebuffer, senza scorrere, all’apertura, dopo il salvataggio e dopo la ricreazione dell’attività. Il controllo mirato è passato; la suite completa è stata rieseguita dopo questa correzione con `OK (14 tests)`. Le catture definitive verificano anche l’avvio a freddo e il passaggio alla modalità scura senza scorrimento.

## Pacchetto e limiti

`versionName=0.2.0`, `versionCode=6`, etichetta Shiftrail. Application ID `it.turni.app`, schema Room versione 1 e identità degli eventi restano compatibili con i pacchetti precedenti. La firma debug conserva il certificato SHA-256 `24dd3588ea4ba44f365d2a76d84c406e5d7c358d0d254bd5c748c312553bfaa0`. Il manifest verificato non contiene Internet o accesso allo stato di rete.

APK: 58.758.227 byte. SHA-256 `e3ba85cc91c980d5500f03c18f3f6ba226e1ac878e52d4fbbc9f2efd07cecd36`. L’illustrazione PNG è 1.327.734 byte, 1536×1024; non sono state introdotte dipendenze per le icone o servizi remoti. Nessun benchmark di prestazioni sulle immagini private, né miglioramento di velocità dichiarato.

La verifica usa Android 15/API 35 in emulatore offline. Smartphone fisici, TalkBack e ulteriori dimensioni/font restano da verificare. Il punteggio non è una probabilità calibrata. Il layout compatibile resta quello descritto nel parser: nomi a sinistra, giorni italiani ordinati lunedì–domenica, una cella orario o sigla per giorno.
