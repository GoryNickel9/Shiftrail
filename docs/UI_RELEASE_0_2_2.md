# Shiftrail 0.2.2

## Cambiamenti

Ore mostra un mese selezionabile o una delle sue settimane, con totale, ore da turni e ore extra. I turni provengono dai record salvati dopo la sincronizzazione; la semplice analisi di una foto non aggiunge ore. La reimportazione non duplica i turni e il conteggio usa l’ultima versione dello stesso giorno se sono presenti record di più calendari. X non aggiunge ore.

Il lavoro extra richiede data, inizio e fine. Può terminare il giorno successivo, essere modificato o eliminato. Il form di modifica conserva i valori durante la ricreazione dell’attività; dopo il salvataggio la schermata mostra il mese della data inserita e il totale mensile. Gli extra restano locali a Shiftrail. La [specifica del conteggio](WORKING_HOURS.md) definisce confini mensili/settimanali, durate nominali e gestione delle sovrapposizioni inserite manualmente.

PC Sync è l’unica destinazione ammessa, mostrata come Google Calendar. Il filtro controlla nome interno o visibile, accesso e visibilità anche prima della scrittura. I calendari nel provider non vengono rinominati. I turni hanno titolo fisso Lavoro; X genera Giorno di riposo, intera giornata, quando abilitato. Disattivare i riposi non elimina automaticamente eventi già salvati.

Impostazioni contiene nome, preavviso (inizialmente 30 minuti), colori distinti per lavoro/riposo e scelta di esportare le X. Sono disponibili nessun avviso e colore del calendario; questi valori rimuovono rispettivamente promemoria e colore specifico negli aggiornamenti. Le preferenze si applicano alla successiva sincronizzazione; gli aggiornamenti restano selezionabili nell’anteprima.

L’OCR rilegge anche gli intervalli validi ma sotto 90/100. Lo stesso intervallo non viene sostituito con una lettura meno affidabile; letture discordanti restano bloccate. Il passaggio al giorno successivo non introduce penalità nel punteggio. La sola correttezza del formato non alza automaticamente una lettura debole a 90/100.

## Verifica

58 test JVM passati, senza errori o test saltati. I nuovi controlli verificano titolo fisso, due colori, esclusione X, preavviso, punteggio invariato tra intervallo diurno/notturno, confini di mese/settimana, mesi bisestili e intervalli duplicati o nulli. Formattazione, compilazione e lint passati: zero errori e 19 warning preesistenti, 16 sulle dipendenze e tre suggerimenti KTX.

18 test Android distinti passati complessivamente. Le quattro fixture OCR e la regressione privata sono passate nella suite completa; la foto conserva tutti i sette valori attesi con `requireUnambiguous=true`. Un errore nel test delle impostazioni identificava sia il valore selezionato nella schermata sia la stessa voce nel dialogo. Il selettore ora cerca soltanto nel dialogo. Dopo le ultime modifiche della schermata Ore, i 13 test Android non OCR sono stati eseguiti insieme sulla build consegnata, con `OK (13 tests)` e nessun test saltato. Il motore OCR non è cambiato dopo la prova delle cinque immagini.

Le prove sul provider controllano fine al giorno successivo, titoli, colori, modifica/rimozione dei promemoria, ID stabile e filtro PC Sync. Le prove Room migrano due database v1, conservando profilo, turni, ID calendario ed eventuali colori/promemoria personalizzati. Il primo pacchetto di test ometteva lo schema v2: il task degli asset ora dipende dall’esportazione KSP, così lo schema è disponibile anche durante la build.

Ore è verificato per totale mensile, settimana parziale, passaggio tra mesi, esclusione X, deduplicazione tra calendari, aggiunta/modifica/eliminazione di extra notturni, persistenza, rotazione del form e inserimento in un mese diverso. Il test delle impostazioni verifica selezione e salvataggio delle preferenze già configurate, oltre a nome e rendering della home.

I log locali sono `artifacts/android-0.2.2-final.txt` per le cinque prove OCR e `artifacts/android-0.2.2-release-final.txt` per i 13 test finali. La foto temporanea è stata rimossa dall’emulatore; non è inclusa nell’APK o nel repository. Nessun benchmark sulle foto private è stato eseguito.

## Pacchetto e compatibilità

`artifacts/Shiftrail-0.2.2.apk`: application ID `it.turni.app`, nome Shiftrail, versione 0.2.2, versionCode 8, 58.821.345 byte. SHA-256 `76479ee4e9197f5f66f38373ece0cf261523f1e528628adafaf3b66453bdee66`. Certificato SHA-256 `24dd3588ea4ba44f365d2a76d84c406e5d7c358d0d254bd5c748c312553bfaa0`, invariato. Il manifest non richiede Internet o stato della rete.

Room passa da v1 a v2 con migrazione esplicita e senza cancellazione dei dati. La tabella `extra_work` e le due nuove preferenze vengono aggiunte; il titolo è normalizzato a Lavoro, preavviso e colore lavoro ricevono i nuovi default soltanto se mancanti. Gli identificatori del profilo, del calendario e degli eventi rimangono invariati. Storico e diagnostica rimangono assenti dalla navigazione.

La verifica usa Android 15/API 35 su emulatore. Smartphone fisici, TalkBack e la visualizzazione effettiva dei colori/avvisi nel calendario PC Sync richiedono una prova sul dispositivo. L’app scrive le opzioni nel provider Android; non implementa un servizio di notifiche o una connessione Google OAuth.
