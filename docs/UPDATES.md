# Aggiornamenti e release

Le versioni scaricabili sono nella sezione [GitHub Releases](https://github.com/GoryNickel9/app-turni/releases). Ogni release contiene `Shiftrail-VERSIONE.apk`, `update.json` e `SHA256SUMS.txt`. Il README collega l’ultima release pubblicata.

L’app controlla il manifest dell’ultima release a ogni ritorno in primo piano. Il pulsante **Impostazioni → Aggiornamenti → Controlla aggiornamenti** permette anche un controllo manuale. Il confronto usa `versionCode`; solo una versione successiva compatibile con l’SDK del dispositivo impone l’aggiornamento. Dalla 0.4.0, una schermata obbligatoria sostituisce tutte le schermate dell’app quando non ci sono operazioni locali in corso. Non offre Più tardi e il tasto Indietro non la chiude. Il download resta esplicito e Android chiede conferma per l’installazione.

I metadati dell’aggiornamento noto vengono conservati nelle preferenze private. Riavvio, assenza di rete o una risposta meno recente non rimuovono l’obbligo; il blocco termina dopo l’installazione di una versione almeno equivalente. Le versioni incompatibili non bloccano il dispositivo. Un errore automatico resta silenzioso solo se non è già noto un aggiornamento obbligatorio. Se non sono noti aggiornamenti e la rete manca, le funzioni locali restano disponibili. Le versioni precedenti alla 0.4.0 mantengono il vecchio comportamento: questa prima installazione non può essere imposta retroattivamente dal nuovo codice.

Il download parte dal pulsante del dialogo. L’app accetta solo HTTPS, limita i redirect ai domini GitHub usati dagli allegati e impone limiti di 64 KiB al manifest e 200 MiB all’APK. Prima di abilitare **Installa**, verifica SHA-256, nome del pacchetto, versione, SDK minimo e certificati di firma rispetto all’app installata. Un download incompleto rimane un file temporaneo e non viene proposto all’installer. I file sono nella cache privata; FileProvider espone solo la cartella `updates/` e concede lettura temporanea all’installer.

Android richiede che l’utente autorizzi Shiftrail come origine di installazione e confermi l’aggiornamento. Se manca l’autorizzazione, **Installa** apre la relativa impostazione: dopo averla concessa occorre tornare alla schermata obbligatoria e premere nuovamente **Installa**. Un controllo della stessa release non azzera lo stato dell’APK già verificato. Non sono previste installazioni silenziose, né download in background dopo la chiusura del processo. Un aggiornamento completato conserva Room e le preferenze. La documentazione di riferimento è [PackageManager.canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

Il permesso INTERNET serve a leggere manifest e APK pubblici da GitHub. Non vengono inviati foto, OCR, profilo, turni o eventi. GitHub riceve le normali richieste HTTPS del dispositivo. Il funzionamento di OCR, calendario e conteggio ore rimane locale e non richiede una connessione.

## Pubblicare una versione

### Canale beta facoltativo

**Impostazioni → Aggiornamenti → Partecipa alla beta** salva subito un’adesione locale, disattivata per impostazione predefinita. Non richiede un account e non registra utenti su un server. Le beta restano facoltative e compaiono con **Scarica beta** nella stessa sezione; non attivano la schermata di aggiornamento obbligatorio. La policy delle versioni stabili resta invariata.

Lo switch è disponibile dalla versione stabile 0.5.0. Gli utenti delle vecchie versioni devono prima aggiornare l’app: una prerelease GitHub, da sola, non aggiunge lo switch alle installazioni esistenti. Le prerelease restano pubbliche su GitHub: l’adesione controlla le proposte nell’app, non l’accesso al file tramite un link diretto.

Il controllo stabile continua a usare `releases/latest/download/update.json`, che GitHub riserva alle release pubbliche stabili. Soltanto per gli iscritti l’app legge anche l’API pubblica delle 100 release più recenti, entro 2 MiB. Seleziona la prerelease pubblicata più di recente con tag `vX.Y.Z-beta.N` (`N` positivo), legge il relativo `update.json` e richiede che versione e tag coincidano. Un errore del canale beta non elimina una versione stabile già verificata. I controlli beta automatici sono limitati a uno ogni 15 minuti; il pulsante manuale permette di riprovare subito. La rete e i limiti dell’API GitHub restano necessari per scoprire nuove beta.

I metadati beta vengono conservati separatamente da quelli stabili. Senza adesione, l’app non interroga il canale beta e non propone prerelease. Disattivare l’adesione cancella la proposta beta e lo stato di installazione pronto, conservando profilo, turni, extra, immagini e preferenze. Se una beta è già installata, rimane in uso finché non esce una stabile con `versionCode` superiore: non si tenta un downgrade, che potrebbe richiedere la disinstallazione. I numeri di build devono quindi crescere globalmente, attraversando entrambi i canali.

Il manifest resta `schemaVersion: 1`. Il campo `versionName` ammette anche `X.Y.Z-beta.N`; i percorsi degli allegati restano derivati da questo nome validato. SHA-256, package, versionCode, versionName, SDK minimo e firma vengono controllati anche per le beta. La UI distingue l’APK pronto per una beta da quello richiesto per una stabile, evitando di proporre l’installazione del file sbagliato dopo un cambio di canale.

Per preparare una beta futura:

1. Impostare `versionName`, per esempio `0.6.0-beta.1`, e un `versionCode` maggiore di tutte le build distribuite.
2. Aggiungere le note `docs/releases/0.6.0-beta.1.md`, verificare, fare commit e push.
3. Eseguire `./scripts/publish-release.ps1 -Beta` solo quando la pubblicazione è autorizzata.

Lo script richiede coerenza tra `-Beta` e il nome versione, controlla che il numero di build superi quelli degli ultimi manifest pubblicati nei due canali, pubblica con `--prerelease --latest=false` e mantiene le versioni già pubblicate immutabili. La successiva stabile usa un numero di build ancora maggiore. La 0.5.0 usa versionCode 16 e viene distribuita come stabile; le beta future richiedono una pubblicazione separata.

Riferimenti: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases) e [badge Android](https://developer.android.com/develop/ui/compose/notifications/badges).

### Canale stabile

Modificare `versionCode` e `versionName` in `app/build.gradle.kts`, aggiungere le note in `docs/releases/VERSIONE.md`, verificare i cambiamenti e fare commit/push. Quindi eseguire da PowerShell:

```powershell
./scripts/publish-release.ps1
```

Lo script esegue build, test JVM, lint e controllo della formattazione, prepara l’APK release, controlla il certificato, produce manifest/checksum e crea la GitHub Release sul commit pubblicato. `-SkipBuild` riutilizza gli artefatti già verificati nella sessione; non esegue i test strumentati, che richiedono un emulatore o un dispositivo. I test Android devono essere eseguiti prima di distribuire cambiamenti ai flussi del calendario o dell’installazione.

Il build type release è non debuggabile ma mantiene il certificato delle versioni iniziali, ottenuto dal keystore Android locale, per consentire l’aggiornamento delle installazioni esistenti. La chiave privata non viene pubblicata. `signing-certificate.sha256` contiene solo l’impronta pubblica: lo script rifiuta un APK con una firma diversa. Conservare una copia sicura del keystore originale; una macchina nuova genera una chiave diversa e non può produrre un aggiornamento compatibile. Gli artefatti debug generati dalla CI sono destinati ai test e non vengono pubblicati automaticamente come release.

Una versione pubblicata non viene sovrascritta: per correggerla, incrementare i numeri di versione e pubblicarne una nuova. Il protocollo del manifest usa `schemaVersion: 1`; una versione sconosciuta viene rifiutata. Il controllo dei certificati richiede la stessa firma corrente; la rotazione della chiave non è implementata.
