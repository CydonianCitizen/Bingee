# v1.2.5 — Checklist prestazioni

Piano operativo derivato dall'audit indipendente del 10 ottobre 2026 sulla revisione
`522fc7ca3e1d54db7ef3f766c8c485424a920b1d` di v1.2.4.
Questo documento integra la [roadmap canonica](../BINGEE_DEVELOPMENT_ROADMAP_AND_CHECKLIST.md)
per v1.2.5 / versionCode 9, insieme al cleanup e alla sincronizzazione della documentazione già previsti.
Il report completo e gli esperimenti restano artefatti temporanei esterni al progetto.

I rilievi derivano dal codice e da esperimenti SQLite sul PC; non sono misure delle prestazioni Android.
L'ordine indica la priorità di intervento, non una gravità P0/P1 dimostrata.
Creare questa checklist non completa nessuna implementazione o verifica di release.

## 0. Preparazione e baseline

- [ ] Ricontrollare stato Git, codice e chiamanti: riconfermare i rilievi sulla revisione da modificare.
- [x] Impostare v1.2.5 / versionCode 9 all'avvio dell'implementazione della milestone.
- [x] Preparare dati sintetici riproducibili; nessun dato utente, credenziale o API reale nei test.
- [x] Registrare dispositivo fisico, Android, refresh rate, revisione/APK, build release ottimizzata,
      stato ART, temperatura e cache/rete per i confronti temporali.
- [ ] Misurare prima gli scenari interessati usando strumenti esistenti: query/observer per azione,
      CPU/Main, frame, allocazioni/heap e aggiornamenti widget secondo il punto.
- [x] Tenere trace, log e risultati temporanei in `.audit/`, esclusa da Git.
- [x] Separare misure Android, conteggi deterministici ed esperimenti sul PC; se manca hardware,
      registrare il limite senza dichiarare benefici di fluidità o batteria.

Applicare un cambiamento coerente per volta. Le osservazioni della UI, le sessioni Glance e il lavoro
background hanno ownership diverse: non usare una politica unica per tutti.

Il 10 ottobre 2026 è stata eseguita una baseline parziale su HONOR CMA-LX1 / Android 13 a 60 Hz,
con release ottimizzata in un package sintetico separato: 100/1.000/10.000 titoli,
1.000/25.000/100.000 episodi visti, 30 avvii e nove cicli di espansione/collasso.
Ricerca finale e conteggi Statistics corretti sui tre carichi; l'espansione cresce sensibilmente
di costo tra 24, 100 e 300 episodi, confermando il rilievo del punto 4.
Fixture, condizioni, log e trace restano temporanei in `.audit/v1.2.5-synthetic/`.
La baseline completa e i fix restano aperti: non sono ancora misurati tutti gli scenari,
le immagini sono assenti e non è dimostrata una regressione introdotta dal cleanup.

## 1. Widget: evitare lavoro senza istanze e aggiornamenti irrilevanti

Riferimenti: [BingeeApplication](../app/src/main/java/com/cydoniancitizen/bingee/BingeeApplication.kt),
[BingeeWidgets](../app/src/main/java/com/cydoniancitizen/bingee/feature/widget/BingeeWidgets.kt),
[WidgetData](../app/src/main/java/com/cydoniancitizen/bingee/feature/widget/WidgetData.kt).

- [ ] Rilevare le istanze installate tramite le API Glance e sospendere le osservazioni dell'updater
      quando non esiste alcun widget.
- [ ] Riattivare correttamente l'updater se un widget viene aggiunto a processo già vivo;
      gestire anche la rimozione dell'ultima istanza.
- [ ] Confrontare il contenuto effettivamente mostrato prima di `updateAll`, anziché le liste complete.
- [ ] Conservare gli observer delle sessioni Glance attive e il callback MarkNextEpisode.
- [ ] Verificare zero/uno/due tipi di widget, aggiunta/rimozione senza riavvio, cambio tema/giorno,
      cambio visibile e modifica a titoli/eventi non visibili.

**Completato quando:** senza widget non partono observer dati dell'updater; modifiche irrilevanti
non generano aggiornamenti visuali; contenuti rilevanti e callback restano corretti, anche in release minificata.

## 2. ViewModel: sospendere letture e calcoli senza consumatori UI

Riferimenti: [ProfileViewModel](../app/src/main/java/com/cydoniancitizen/bingee/feature/profile/ProfileViewModel.kt),
[ProfileScreen](../app/src/main/java/com/cydoniancitizen/bingee/feature/profile/ProfileScreen.kt),
[BingeeNavHost](../app/src/main/java/com/cydoniancitizen/bingee/core/navigation/BingeeNavHost.kt).

- [ ] Contare gli observer nel percorso Your Bingee → Collection → Details → background → ritorno.
- [ ] Legare le letture/calcoli del Profilo alla presenza di consumatori, con un breve grace period
      e conservazione dello stato già disponibile; riusare job e dispatcher esistenti.
- [ ] Preservare la preview di Your Bingee e la condivisione dell'owner con Statistics,
      inclusi i percorsi aperti dai launcher shortcut.
- [ ] Lasciare terminare le scritture richieste dall'utente anche quando la UI smette di raccogliere lo stato.
- [ ] Verificare stop/ripartenza, assenza di sottoscrizioni duplicate, dati freschi al ritorno,
      contenuto cached, errori, retry e cancellazione con fake repository.
- [ ] Estendere la stessa revisione a Home/Details solo se contatori e misure dimostrano lavoro evitabile.

**Completato quando:** dopo il grace period un producer UI senza consumatori non mantiene letture
costose attive; il ritorno non perde stato, aggiornamenti o operazioni utente.

## 3. Room: ridurre aggregazioni e osservazioni ripetute

Riferimenti: [LibraryDao](../app/src/main/java/com/cydoniancitizen/bingee/data/library/local/LibraryDao.kt),
[DefaultLibraryRepository](../app/src/main/java/com/cydoniancitizen/bingee/data/library/DefaultLibraryRepository.kt),
[ContinueWatchingPolicy](../app/src/main/java/com/cydoniancitizen/bingee/domain/policy/ContinueWatchingPolicy.kt).

- [ ] Dopo i punti 1–2, misurare quali proiezioni rimangono duplicate per invalidazione.
- [ ] Riutilizzare `regular_episode_activity` per runtime totale e runtime mancanti in Personal Viewing,
      evitando le due aggregazioni correlate aggiuntive; validare la candidata sul vero DAO Room.
- [ ] Condividere l'osservazione Continue Watching per giorno se resta raccolta da più consumatori,
      conservando ripartenza, errori e cancellazione corretti.
- [ ] Valutare la selezione unica dell'identità del prossimo/ultimo episodio e l'esclusione di lavoro
      su titoli non eleggibili; adottare solo una query equivalente e più economica.
- [ ] Applicare `distinctUntilChanged` prima dei mapping costosi dove evita riaggregazioni reali:
      documentare che non impedisce il rerun SQL dovuto all'invalidation Room.
- [ ] Conservare la condivisione dei progressi già presente; non aggiungere indici o cache generiche
      senza evidenza dal query plan e dalle misure.
- [ ] Verificare più stagioni/riferimenti, speciali, copertura parziale, runtime null, date future/assenti,
      serie complete/abbandonate, identità TMDB qualificata per tipo, progressi e cambio giorno.
- [ ] Confrontare query count e durata su librerie piccole/grandi, prevalentemente film o serie complete.

**Completato quando:** risultati DAO/domain equivalenti, nessun errore nascosto dagli observer condivisi
e riduzione verificabile di letture/trasformazioni; ogni beneficio temporale dichiarato è misurato su Android.

## 4. Compose: virtualizzare singoli episodi ed eventi

Riferimenti: [TvSeriesSection](../app/src/main/java/com/cydoniancitizen/bingee/feature/details/TvSeriesSection.kt),
[HomeScreen](../app/src/main/java/com/cydoniancitizen/bingee/feature/home/HomeScreen.kt).

- [ ] Misurare espansione/scroll con stagioni da 24/100/300 episodi e date con 2/20/100 eventi.
- [ ] Esporre header ed episodi come elementi distinti dello stesso `LazyListScope` in Details.
- [ ] Esporre header di data ed eventi come elementi distinti della lista Home.
- [ ] Usare key qualificate e contentType coerenti; conservare raggruppamento visuale, speciali e ordine.
- [ ] Mantenere un solo scroller, senza liste annidate con altezza arbitraria.
- [ ] Valutare l'animazione dell'espansione nel nuovo layout; preservare stabilità del contenuto,
      controllo di stagione, semantics, target di tocco e font scaling.
- [ ] Verificare toggle, espansione/collasso, scroll e ritorno; confrontare elementi composti,
      richieste immagini, allocazioni e frame con cache immagini vuota e popolata.

**Completato quando:** il lavoro cresce con viewport/prefetch anziché con tutto il gruppo;
nessuna regressione di accessibilità, navigazione o progressi.

## 5. Collection: spostare filtro e ordinamento dal Main

Riferimento: [ProfileViewModel](../app/src/main/java/com/cydoniancitizen/bingee/feature/profile/ProfileViewModel.kt).

- [ ] Misurare digitazione e cambio filtri/sort su 100/1.000/10.000 titoli.
- [ ] Spostare filtro, ordinamento e calcoli delle preview nel dispatcher già disponibile,
      usando uno snapshot coerente di dati e parametri.
- [ ] Aggiornare immediatamente il testo digitato e pubblicare solo il risultato della richiesta ancora valida.
- [ ] Gestire cancellazione cooperativa dei cicli lunghi e invalidazioni Room durante il calcolo.
- [ ] Verificare input rapido, sort/filter alternati, aggiornamenti concorrenti e risultato finale corretto.
- [ ] Confrontare tempo sul Main e latenza input→risultato; evitare Paging/FTS o cache aggiuntive
      se lo spostamento minimo è sufficiente.

**Completato quando:** il calcolo costoso non blocca il Main e un risultato vecchio non sovrascrive
la selezione o i dati correnti.

## 6. Refresh episodi: eliminare le scansioni quadratiche delle mappe

Riferimento: [SeriesDao](../app/src/main/java/com/cydoniancitizen/bingee/data/library/local/SeriesDao.kt).

- [ ] Sostituire le due scansioni `entries.removeIf` per candidato con rimozioni dirette delle vecchie chiavi.
- [ ] Leggere le chiavi prima di sostituire `state.episode` e rimuoverle solo se puntano allo stesso stato.
- [ ] Conservare controlli di conflitto/appartenenza, ID locali, progressi e transazione unica.
- [ ] Verificare refresh invariato, nuovo ID provider sullo stesso numero, duplicati e conflitti ref/numero.
- [ ] Confrontare conteggi/CPU e durata della transazione su 24/100/300/1.000 episodi.

**Completato quando:** le scansioni delle mappe non crescono più quadraticamente nel refresh descritto,
senza DELETE/REINSERT di entità o perdita di progressi.

## 7. Statistiche: aggregazione mensile e cancellazione cooperativa

Riferimenti: [WatchedStatistics](../app/src/main/java/com/cydoniancitizen/bingee/domain/model/WatchedStatistics.kt),
[ProfileViewModel](../app/src/main/java/com/cydoniancitizen/bingee/feature/profile/ProfileViewModel.kt).

- [ ] Dopo aver ridotto le riemissioni ai punti 2–3, misurare aggregazione con 1.000/25.000/100.000 attività.
- [ ] Sostituire i dodici filtri dell'intero storico con un passaggio e accumulatori mensili.
- [ ] Conservare timezone, anno selezionato, anni disponibili, date future e indicatori di runtime mancante.
- [ ] Rendere cooperativa la cancellazione dei grandi calcoli: `mapLatest` da solo non interrompe
      i cicli sincroni privi di sospensioni o controlli del Job.
- [ ] Evitare ricalcoli di rating/generi al cambio anno solo se il percorso risulta significativo nelle misure.
- [ ] Verificare equivalenza statistica, storico fuori libreria, bordi anno/timezone e cancellazione durante il lavoro.

**Completato quando:** risultati equivalenti, meno passaggi/allocazioni e cancellazione verificata.
La riduzione da dodici passaggi a uno non implica un'accelerazione di dodici volte dell'intera funzione.

## 8. Backup: misurare il picco heap prima di cambiare la pipeline

Riferimenti: [BackupJsonCodec](../app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupJsonCodec.kt),
[BackupDataStore](../app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupDataStore.kt),
[BackupFileGateway](../app/src/main/java/com/cydoniancitizen/bingee/data/importexport/BackupFileGateway.kt).

- [ ] Misurare heap, allocazioni, GC, durata e transazioni con JSON sintetici da 1/10/40–50 MiB
      e una cache ampia non appartenente alla libreria.
- [ ] Registrare quali rappresentazioni complete sono contemporaneamente vive e se il picco è problematico.
- [ ] Se confermato, scegliere il più piccolo intervento: lettura a record con limiti/UTF-8 rigoroso,
      export su file temporaneo prima della pubblicazione oppure conversioni fuori dalla transazione
      dopo aver acquisito uno snapshot coerente. Non imporre una riscrittura completa.
- [ ] Se la misura non giustifica una modifica, registrare la decisione e l'evidenza; distinguere
      una scelta verificata dalla mancanza di hardware o misure.
- [ ] Per ogni modifica verificare backup v1/v2/v3, input malformato, UTF-8 errato, limiti,
      cancellazione, errore di scrittura e rollback.

**Completato quando:** verifica e decisione sono documentate; l'eventuale intervento riduce il problema
misurato preservando export completo, validazione preventiva e restore interamente transazionale.
Non ridurre limiti, troncare dati o introdurre commit parziali come scorciatoia.

## Opportunità condizionate, non requisiti automatici

- [ ] Verificare il numero di coroutine in attesa nel refresh manuale di una libreria molto grande;
      introdurre lotti solo se necessario, conservando tutti i titoli, concorrenza remota e risultati parziali.
- [ ] Valutare un Baseline Profile dell'app solo dopo una baseline di startup/percorsi utente;
      non aggiungere moduli benchmark o infrastruttura CI come effetto collaterale del cleanup.
- [ ] Lasciare invariati R8 già abilitato, Strong Skipping predefinito, loader Coil condiviso e WorkManager
      con lotti/retry limitati, salvo un problema concreto distinto.

## Verifica e chiusura v1.2.5

- [ ] Per ogni punto registrare revisione, scenario, verifica funzionale ed evidenza prima/dopo;
      distinguere implementato, verificato senza modifica e rinviato con motivo.
- [ ] Eseguire regressioni proporzionate: widget, Profile/Statistics e shortcut, query Room,
      identità/progressi, statistiche, import/export e worker secondo i file cambiati.
- [ ] Eseguire formattazione/lint, test JVM, test Room/strumentali pertinenti e build debug previsti dal repository.
- [ ] Verificare in release ottimizzata i percorsi modificati, inclusi callback Glance e detail intent.
- [ ] Misurare i benefici su hardware con fixture e condizioni comparabili; registrare ripetizioni,
      variabilità e frame P50/P95/P99. Non sommare risparmi di interventi sovrapposti.
- [ ] Eseguire il passaggio Ponytail e gli audit globali richiesti dalla roadmap dopo ogni modifica,
      poi la sintesi globale finale.
- [ ] Completare anche cleanup e sincronizzazione documentale già previsti per v1.2.5;
      aggiornare policy/ADR solo quando cambia una decisione durevole.
- [ ] Conservare Room 9 e backup 3 se non cambia lo schema/formato. Se serve una modifica,
      applicare incremento versione, migrazione non distruttiva/schema e test di compatibilità richiesti.
- [ ] Controllare link, diff, file generati e assenza di segreti; report/trace restano esclusi da Git.
- [ ] Passare il release gate e lasciare il progetto pronto per v1.3.0; commit/push solo su richiesta.
