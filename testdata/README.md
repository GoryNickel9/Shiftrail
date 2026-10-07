# Fixture sintetica

`synthetic-roster.png` contiene trenta dipendenti fittizi, sette giorni del 5–11 ottobre 2026, turni notturni, sigle e colonne numeriche ausiliarie. Il nome da configurare è `Luca Baldeddu`.

Rigenera la foto su Windows con `./scripts/generate-fixture.ps1`. I test JVM costruiscono token geometrici equivalenti; il test strumentato esegue ML Kit sul PNG. Non inserire foto di colleghi nel repository: `testdata/private/` è esclusa da Git.

I test Android usano anche una copia ridotta a 1000×750 e un tabellone generato in memoria con altri nomi, date e turni. La fixture sintetica non misura l’accuratezza generale su foto reali.

`smallTextWithOffsetHeadersAndAttachedCountersIsReadSafely` genera un ulteriore tabellone a 920×180 con intestazioni decentrate e contatori accanto agli orari. Richiede sei celle corrette e che l’ultima sia corretta oppure resti UNKNOWN con revisione obbligatoria quando ML Kit fonde il contatore con una cifra dei minuti. Nomi e valori sono fittizi; il test non usa il layout della foto privata.

`PrivatePhotoTest` è un controllo locale facoltativo. Legge la foto da `/data/local/tmp/turni-regression.png` tramite la shell dell’emulatore; nome, lunedì e sette valori attesi arrivano dagli argomenti `employee`, `monday`, `shifts`, insieme a `privatePhoto=true`. La foto non entra negli asset, nell’APK o nel repository. Il controllo accetta un valore corretto oppure una cella UNKNOWN che conserva l’alternativa corretta e richiede revisione; rifiuta un orario sbagliato esportabile. Non incorpora coordinate di un tabellone specifico.

Il parametro facoltativo `requireUnambiguous=true` richiede che tutte le sette celle siano leggibili e corrette, senza accettare UNKNOWN. Serve per verificare un recupero completo quando i valori attesi della foto sono disponibili. Dopo l’esecuzione, rimuovere la sola copia temporanea con `adb shell rm /data/local/tmp/turni-regression.png`.
