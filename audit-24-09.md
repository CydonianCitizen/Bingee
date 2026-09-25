# Audit globale Bingee — 24 settembre 2026

## Sintesi e metodo

**2 rilievi P1, 5 P2, 4 P3. Nessun P0 osservato.** Le prime correzioni riguardano backup ripristinabili e file condivisi stabili. Non esiste un backend proprietario: Room è la fonte locale; TMDB e GitHub sono accessi remoti isolati.

Audit del working tree corrente, già modificato prima di questa attività. Inventario: 486 file pertinenti, 210 Kotlin di produzione, 82 file di test JVM e 47 file di test strumentali. Letti AGENTS.md, roadmap, architettura, ADR pertinenti, configurazione, manifest, CI, risorse e percorsi rappresentativi di dati, rete, import, worker e UI. Applicati Ponytail Audit e Impeccable Audit nativo. Sei catture UI locali del 23 settembre sono state osservate come riferimento, senza attribuirle automaticamente allo stato odierno.

**Verifiche:** gradlew.bat spotlessCheck test lint assembleDebug --offline riuscito; 451 test JVM, zero fallimenti o test saltati. gradlew.bat assembleDebugAndroidTest --offline riuscito. Lint: 0 errori, 44 warning. Nessun dispositivo collegato: test strumentali e controllo TalkBack non eseguiti. Ricerca statica di pattern di segreti: nessun valore sospetto individuato nei file inventariati. Nessuna verifica CVE online o misura di runtime su hardware. I problemi non riprodotti su Android sono indicati come deduzioni dal sorgente.

## 1. Dati, Room, backup e restore

### A01 — P1 — L’app può esportare un backup che non sa ripristinare

[BackupModels.kt](app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupModels.kt#L12) impone 50 MiB alla lettura e ammette 500.000 episodi. [BackupJsonCodec.kt](app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupJsonCodec.kt#L25) codifica l’intero documento in memoria senza controllare la dimensione; la lettura rifiuta oltre 50 MiB alla riga 47. [BackupDataStore.kt](app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupDataStore.kt#L389) salva quel risultato. Anche [TV Time](app/src/main/java/com/cydoniancitizen/bingee/data/imports/tvtime/TvTimeImportLimits.kt#L12) ammette 500.000 record episodio.

Un episodio con campi null occupa circa 340 byte nel JSON indentato attuale: circa 155.000 episodi bastano a superare 50 MiB, prima di descrizioni reali. Scenario dedotto, senza database sintetico di quella dimensione. L’utente potrebbe conservare un file che Bingee rifiuta con TOO_LARGE; la codifica in ByteArrayOutputStream aumenta il picco di memoria. **Azione:** allineare limiti di import/export/TV Time; usare streaming o rifiutare chiaramente un export non ripristinabile. Aggiungere round-trip vicino ai limiti di byte, stagioni ed episodi.

### A02 — P2 — Il JSON non porta tutte le preferenze personali

[BackupPreferences](app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupModels.kt#L99) include solo anticipo e categorie delle notifiche. Tema e lingua sono in [AppearancePreferences.kt](app/src/main/java/com/cydoniancitizen/bingee/data/settings/AppearancePreferences.kt#L27), spoiler in [SpoilerPreferences.kt](app/src/main/java/com/cydoniancitizen/bingee/data/settings/SpoilerPreferences.kt#L12), vista raccolta in [ProfileDisplayModePreferences.kt](app/src/main/java/com/cydoniancitizen/bingee/data/settings/ProfileDisplayModePreferences.kt#L40). Dopo il restore queste scelte vanno rifatte. I documenti escludono intenzionalmente alcune impostazioni del dispositivo, ma AGENTS.md richiede export JSON completo delle preferenze.

**Azione:** decidere in ADR quali preferenze siano portabili. Se queste lo sono, aggiungerle a una nuova versione compatibile del formato e testare default v1/v2. Permessi, abilitazione notifiche e token restano specifici del dispositivo.

**Aspetti sani:** schema Room v7 esportato, migrazioni esplicite, identità TMDB tipizzata, validazione prima della transazione e rollback del restore.

## 2. Privacy, sicurezza e condivisione

### A03 — P1 — Due condivisioni riusano lo stesso URI e possono cambiare contenuto

[BackupFileGateway.kt](app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupFileGateway.kt#L25) elimina il file precedente e scrive sempre backup_exports/bingee-backup-share.json. La seconda condivisione può sostituire i byte del primo URI prima che la prima app destinataria li legga. Il primo invio può quindi fallire o consegnare un backup diverso, con cronologia personale in chiaro. Scenario dedotto dal ciclo di vita del file; non riprodotto con app riceventi.

**Azione:** usare file e URI unici per invio; eliminare solo file sufficientemente vecchi. Testare due share consecutive con lettura ritardata del primo URI.

**Aspetti sani:** token cifrato con Keystore in noBackupFilesDir; FileProvider non esportato e con permesso di sola lettura; UI segnala che il backup è in chiaro; nessuna chiave fissa trovata nei sorgenti.

## 3. WorkManager e notifiche

### A04 — P2 — Una notifica pubblicata può riapparire dopo errore Room

[DefaultNotificationDispatchCoordinator.kt](app/src/main/java/com/cydoniancitizen/bingee/data/notification/DefaultNotificationDispatchCoordinator.kt#L94) pubblica prima della scrittura nel registro. Se la scrittura fallisce, il retry non trova l’identità e pubblica ancora. Due worker concorrenti possono leggere entrambi assenza prima della pubblicazione. [Il test esistente](app/src/test/java/com/cydoniancitizen/bingee/data/notification/DefaultNotificationDispatchCoordinatorTest.kt#L143) verifica i fallimenti separati, non il secondo giro. setOnlyAlertOnce limita l’avviso se la stessa notifica è ancora visibile; non garantisce unicità dopo che l’utente l’ha scartata.

**Azione:** prenotazione atomica con stato recuperabile oppure contratto esplicito di consegna almeno una volta. Testare errore dopo notify, retry, due worker e riavvio. Non segnare consegna prima della pubblicazione senza recupero.

**Aspetti sani:** lotti limitati, retry solo temporanei, lavoro unico, controllo permessi, valutazione notifiche senza rete.

## 4. Frontend Compose e UI — Impeccable

**Punteggio provvisorio da sorgente: 15/20.** Accessibilità 3/4, prestazioni 2/4, tema 3/4, conformità Android 4/4, adattività 3/4. Material 3, insets, Back di sistema, tema semantico, risorse inglesi/italiane, barra compatta e rail oltre 600 dp sono punti forti. Le catture recenti di Your Bingee e Statistiche mostrano gerarchia leggibile. Mancano prova aggiornata su tablet, tema scuro, font scale 2,0 e TalkBack.

### A05 — P2 — Una stagione espansa compone tutti gli episodi insieme

[TvSeriesSection.kt](app/src/main/java/com/cydoniancitizen/bingee/feature/details/TvSeriesSection.kt#L240) inserisce tutti gli episodi in una Column dentro un singolo item della [LazyColumn di Dettagli](app/src/main/java/com/cydoniancitizen/bingee/feature/details/MediaDetailsScreen.kt#L372). Anche le righe fuori schermo vengono composte. Su stagioni lunghe apertura, scroll e toggle possono rallentare. Nessun frame time misurato.

**Azione:** appiattire header e episodi nella singola lista lazy, con chiavi stabili e semantica preservata. Misurare memoria e frame su stagione lunga. Comando pertinente: $impeccable optimize.

### A06 — P2 — Le proiezioni della libreria possono lavorare sul main thread

[DefaultLibraryRepository.kt](app/src/main/java/com/cydoniancitizen/bingee/data/library/DefaultLibraryRepository.kt#L85) mappa e ordina tutte le entry dopo combine; alle righe 127–148 raggruppa tutte le attività episodio. Non c’è dispatcher esplicito per queste trasformazioni, raccolte dai ViewModel. Rischio dedotto di jank con cronologia grande; non è un ANR misurato.

**Azione:** misurare con fixture ampia e trace. Se il costo emerge, spostare la proiezione a monte con flowOn e il dispatcher già disponibile, senza nuova infrastruttura.

### A07 — P3 — Il radar limita il contenitore a font scale 1,5

[StatisticsScreen.kt](app/src/main/java/com/cydoniancitizen/bingee/feature/profile/StatisticsScreen.kt#L1043) limita l’aumento del frame a 1,5 mentre le etichette restano in sp, con tre righe massime ed ellissi. Oltre 1,5 i nomi lunghi possono comprimersi; la classifica testuale mantiene le informazioni. **Azione:** provare scala 2,0 in entrambe le lingue su telefono stretto; adattare il grafico o favorire la classifica. Comando: $impeccable adapt.

## 5. Test, CI e qualità

### A08 — P2 — I test UI Android non girano in CI

[android-ci.yml](.github/workflows/android-ci.yml#L66) filtra connectedDebugAndroidTest sui package dati. Il repository contiene 47 file strumentali, inclusi test Home, Search, Details, widget, impostazioni e navigazione. L’APK viene compilato, ma quei test non risultano eseguiti da un altro workflow. Regressioni UI possono quindi passare con CI verde.

**Azione:** aggiungere un job o una selezione piccola e stabile dei flussi UI ad alto valore; mantenere separati i test Room. Coprire avvio offline, ricerca/dettaglio, backup/restore e apertura da notifica.

Lint produce 44 warning: soprattutto aggiornamenti disponibili, suggerimenti plurali, launcher icon e KTX. Un warning Compose ModifierParameter è in [StatisticsScreen.kt](app/src/main/java/com/cydoniancitizen/bingee/feature/profile/StatisticsScreen.kt#L208). Non aggiornare SDK o dipendenze solo per azzerare warning senza verificare compatibilità.

## 6. Codice morto e complessità — Ponytail Audit

- **A09 — P3 — delete:** [NotificationDeliveryDao.contains](app/src/main/java/com/cydoniancitizen/bingee/data/library/local/NotificationDeliveryDao.kt#L18) è usato solo dal test DAO, mai dal repository di produzione. Il test può verificare tramite findBetween. Taglio stimato: 15–20 righe tra query e helper di test.
- **A10 — P3 — delete:** dieci PNG launcher in mipmap-mdpi…mipmap-xxxhdpi duplicano le [icone adattive](app/src/main/res/mipmap-anydpi/ic_launcher.xml) su minSdk 33. Circa 69 KiB di asset sorgente. Verificare la risoluzione su launcher OEM prima del taglio.

**net Ponytail: −15/20 righe, −10 asset possibili, −0 dipendenze identificate.** Non propongo di dividere parser TV Time o restore soltanto perché lunghi: la complessità osservata tutela limiti, identità e transazioni. Nessuna correzione Ponytail applicata.

## 7. Documentazione

### A11 — P3 — Roadmap e architettura hanno intestazioni 1.2.0

[README.md](README.md#L5) dichiara 1.2.2; [roadmap.md](docs/roadmap.md#L3) e [architecture.md](docs/architecture.md#L3) si presentano come 1.2.0. Il contenuto include capacità attuali, ma l’intestazione ne rende meno chiara la validità. **Azione:** aggiornare le intestazioni dopo verifica del contenuto. temp_ui.xml era già non tracciato all’inizio: verificare se serve, senza cancellarlo automaticamente.

## Priorità e tempi

| Ordine | Intervento | Stima |
|---|---|---:|
| 1 | A01: export sempre ripristinabile e prova al limite | 1–2 giorni |
| 2 | A03: URI unici e pulizia sicura | 0,5–1 giorno |
| 3 | A04: consegna notifiche e prove di retry | 0,5–1 giorno |
| 4 | A02: contratto preferenze e versione backup | 1–2 giorni |
| 5 | A05–A08: lista episodi, misura libreria, font scale, CI UI | 1–2 giorni |
| 6 | A09–A11: tagli verificati e documenti | 0,5 giorno |

**Correzioni stimate: 4–8 giorni sviluppatore**, esclusi test su più dispositivi e review del formato backup. **Audit richiesto: stima 60–120 minuti** in base a emulatori e cache Gradle. Nessun codice è stato modificato per questo audit; modifiche utente preesistenti lasciate intatte.
