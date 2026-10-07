# Ricerca open source

Ricerca eseguita il 7 ottobre 2026 prima della creazione del progetto: OCR to calendar, shift/timetable OCR, CameraX/ML Kit, CalendarContract e correzione prospettica.

| Progetto | Licenza verificata / stato | Valutazione |
| --- | --- | --- |
| [googlesamples/mlkit](https://github.com/googlesamples/mlkit) | Apache-2.0 | Riferimento ufficiale per elementi OCR e CameraX; nessun codice copiato. |
| [calendar-snapshot](https://github.com/adamhajari/calendar-snapshot) | README con permesso informale, nessuna licenza SPDX accertata | Usa Google OAuth/API e menziona Gemini; non risolve geometria della riga, non adottato. |
| [SnapShift](https://github.com/rydertetreault/SnapShift) | Licenza permissiva non accertata nella ricerca | React Native/Expo e OCR.space: incompatibile con elaborazione nativa locale richiesta. |
| [CanHub/Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) | Apache-2.0 | Crop Android riutilizzabile, ma non serve per la trasformazione a quattro angoli implementata con Matrix. |
| [DocScan](https://github.com/dm1366631/DocScan) | MIT dichiarata nel repository | Scanner con OpenCV, non parser di turni; dipendenza aggiuntiva senza dataset reale per valutarla. |
| [DocShot](https://github.com/Artemarius/DocShot) | Licenza non accertata nella ricerca | Correzione prospettica/OpenCV/NDK, non adottato senza necessità misurata. |
| [ClearScan](https://github.com/ant-cave/ClearScan) | AGPL-3.0-or-later dichiarata | Scanner più ampio con cloud opzionale; non adottato. |

È stato creato un progetto nuovo per mantenere parser JVM, UI nativa e CalendarContract senza vincoli di un’app centrata su scansione documenti o servizi remoti. Le licenze non accertate sono state escluse dal riuso, non considerate permissive per assunzione.
