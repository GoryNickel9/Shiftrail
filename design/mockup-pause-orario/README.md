# Mockup interattivo — pause non retribuite

Aprire index.html nel browser. Il prototipo usa solo HTML, CSS e JavaScript locali; non modifica l'app, i dati o il calendario.

1. Nel turno del 28 ottobre scegliere Aggiungi pausa. Impostare 12:30–13:00 con le rotelle e salvare: il turno 06:00–14:00 mantiene otto ore di presenza e conta sette ore e trenta nette.
2. Inserire una seconda pausa: gli intervalli sovrapposti o fuori turno vengono rifiutati. Annulla e la chiusura della rotella mantengono i valori precedenti.
3. Sul turno notturno 22:00–06:00 inserire 02:00–02:30: diminuiscono sia il netto sia le ore notturne.
4. La penna nella riga della pausa permette modifica ed eliminazione con conferma. La penna del turno conserva la modifica dell'orario; un nuovo intervallo non può escludere pause già salvate.
5. Provare tema scuro, vista settimanale e straordinari. Azzera demo ripristina i dati di esempio.

La proposta conserva nel calendario l'intervallo completo di presenza. Le pause riducono i conteggi nell'app; non cancellano segmenti degli eventi. La persistenza e il calcolo delle pause non sono ancora implementati nell'app Android.

I controlli si eseguono con node verify.cjs, usando Playwright disponibile nel runtime di sviluppo. Verificano i conteggi netti e notturni, sovrapposizioni, limiti, annullamento, eliminazione, straordinari, temi e larghezza mobile. Le immagini PNG sono generate da questi controlli.
