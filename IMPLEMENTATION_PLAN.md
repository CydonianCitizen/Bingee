# Piano di implementazione milestone-by-milestone

## Obiettivo del piano

Questo documento guida Codex nello sviluppo incrementale dell'app Android local-first per tracking di film, serie TV e, successivamente, anime.

Ogni milestone deve produrre un incremento eseguibile, testabile e dimostrabile. Non iniziare una milestone finché la precedente non soddisfa i propri exit criteria, salvo attività esplicitamente indipendenti.

## Regole di esecuzione

Per ogni milestone Codex deve:

1. leggere `AGENTS.md` e questo documento;
2. controllare lo stato corrente del repository;
3. proporre o confermare il sottoinsieme di task da implementare;
4. procedere in cambiamenti piccoli e coesi;
5. aggiungere test insieme al codice;
6. eseguire i controlli indicati;
7. aggiornare documentazione e ADR pertinenti;
8. produrre un report finale con file modificati, test, debito residuo e passaggio successivo.

---

# Milestone 0 — Bootstrap del repository

## Scopo

Creare una base Android riproducibile, pulita e pronta per contributi open source.

## Deliverable

- Progetto Android Kotlin/Compose avviabile.
- Package/application ID provvisorio documentato.
- Version catalog Gradle.
- Configurazione Hilt.
- Navigation Compose minima.
- Room, Retrofit/OkHttp e WorkManager aggiunti senza ancora costruire feature complete.
- Struttura package coerente con `AGENTS.md`.
- Configurazione lint e formattazione scelta dal progetto.
- CI GitHub Actions per build debug e test unitari.
- `.gitignore` corretto.
- `README.md` iniziale.
- `LICENSE` scelta esplicitamente.
- `CONTRIBUTING.md` iniziale.
- `SECURITY.md` essenziale.
- directory `docs/adr/` con template ADR.

## Decisioni da fissare

- nome provvisorio del progetto;
- minSdk, targetSdk e compileSdk;
- versione Kotlin/AGP/Compose;
- licenza open source;
- policy di gestione versioni.

## Test e verifiche

- build debug pulita;
- test unitari di esempio rimossi o sostituiti da test reali minimi;
- CI verde;
- nessun segreto o file locale tracciato.

## Exit criteria

- Un contributor può clonare il repository, eseguire la build e avviare la schermata shell seguendo il README.
- La bottom navigation placeholder mostra Home, Ricerca e Profilo/Impostazioni.
- Il repository contiene solo dipendenze effettivamente previste a breve.

---

# Milestone 1 — Fondazioni architetturali e design system minimo

## Scopo

Definire i confini tra UI, dominio e dati prima delle feature remote.

## Deliverable

- Modelli di dominio di base:
  - `MediaSource`;
  - `MediaType`;
  - `ExternalMediaRef`;
  - `MediaSearchResult`;
  - `MediaDetails`;
  - `LibraryEntry`;
  - `Season`;
  - `Episode`;
  - `ReleaseEvent`.
- Contratti repository iniziali, senza accoppiamento ai DTO.
- Result/error model condiviso e mappatura errori UI.
- Theme chiaro/scuro.
- componenti base per loading, empty state, error state e offline banner.
- navigazione tipizzata o argomenti centralizzati.
- convenzioni per `UiState` e ViewModel.
- fake repository per preview e test.

## Vincoli

- Nessun DTO TMDB nel dominio.
- Nessuna dipendenza di Room o Retrofit nei composable.
- Non creare ancora una gerarchia generica eccessiva di use case.

## Test e verifiche

- test sui value object e mapping errori;
- test di navigazione o route serialization se applicabile;
- preview o screenshot manuali dei principali stati UI.

## Exit criteria

- È possibile sviluppare una feature con fake data attraversando UI → ViewModel → repository senza provider reale.
- Le convenzioni sono documentate nel README tecnico o in ADR.

---

# Milestone 2 — Impostazioni e configurazione TMDB

## Scopo

Permettere all'utente di configurare in modo sicuro la propria API key TMDB.

## Deliverable

- Schermata onboarding/configurazione iniziale.
- Schermata Impostazioni accessibile dal profilo.
- Inserimento, modifica e rimozione API key.
- Validazione formale locale della key.
- Verifica remota della key tramite endpoint TMDB appropriato.
- Persistenza locale separata dalle preferenze ordinarie.
- Stato app quando la key manca o non è valida.
- Possibilità di continuare a consultare la libreria offline anche senza key valida.
- Sezione attribuzioni TMDB e informazioni privacy.

## Sicurezza

- La key non appare nei log.
- La key non entra in backup/export.
- Nessuna key di sviluppo viene committata.

## Test e verifiche

- test storage key con fake crypto/storage;
- test ViewModel per key mancante, valida, invalida e errore rete;
- MockWebServer per validazione remota;
- test manuale di riavvio app e persistenza.

## Exit criteria

- L'app guida correttamente un nuovo utente alla configurazione.
- Una key invalida non provoca crash né loop.
- La cancellazione della key disabilita solo le funzioni remote.

---

# Milestone 3 — Ricerca TMDB verticale end-to-end

## Scopo

Consegnare il primo percorso utente completo: cercare film e serie TV.

## Deliverable

- Client Retrofit TMDB.
- DTO separati per movie e TV search.
- Mapper verso `MediaSearchResult`.
- Repository TMDB.
- Schermata Ricerca con:
  - query;
  - debounce;
  - cancellazione richiesta precedente;
  - caricamento;
  - stato vuoto;
  - errore;
  - retry;
  - paginazione o caricamento progressivo, se previsto dall'endpoint.
- Risultati distinti chiaramente per film/serie.
- Gestione immagini e placeholder.
- Nessuna persistenza lunga delle query.

## Test e verifiche

- mapper movie/TV;
- error mapping HTTP, rete, rate limit e payload incompleto;
- ViewModel ricerca con debounce/cancellazione;
- repository con MockWebServer;
- UI test essenziale per query → risultati → errore/retry.

## Exit criteria

- Con key valida l'utente cerca e vede risultati reali.
- Con rete assente riceve uno stato comprensibile.
- Query rapide non mostrano risultati obsoleti.

---

# Milestone 4 — Database Room e libreria locale

## Scopo

Rendere persistente la selezione dell'utente e introdurre la vera base local-first.

## Deliverable

- Schema Room v1 esportato.
- Entità iniziali:
  - `media_entries`;
  - `external_refs`;
  - `library_entries`;
  - eventuali dati cache minimi del dettaglio.
- DAO con query osservabili.
- Repository libreria.
- Aggiunta/rimozione titolo dalla libreria.
- Profilo/Libreria con filtri minimi:
  - tutti;
  - film;
  - serie.
- Visualizzazione locale disponibile offline.
- Vincoli univoci su fonte + ID esterno + tipo entità dove necessario.

## Comportamenti da definire

- rimuovere un titolo cancella o conserva lo storico? Per l'MVP scegliere una policy esplicita e documentata;
- distinguere “in libreria” da “visto”.

## Test e verifiche

- DAO in-memory;
- transazioni add/remove;
- vincoli univoci;
- emissioni Flow;
- repository con fake clock per timestamp.

## Exit criteria

- Un titolo aggiunto resta dopo riavvio.
- La libreria si apre e funziona senza rete.
- Non vengono create entry duplicate per lo stesso riferimento TMDB.

---

# Milestone 5 — Dettaglio titolo cache-first

## Scopo

Mostrare dettagli affidabili, utilizzabili anche quando il refresh remoto fallisce.

## Deliverable

- Endpoint TMDB dettaglio film e serie.
- Modelli/DTO e mapper dedicati.
- Persistenza dei metadati utili.
- `MediaDetailsRepository` o contratto equivalente con policy di refresh esplicita.
- Schermata dettaglio:
  - poster/backdrop;
  - titolo e titolo originale;
  - anno/data;
  - generi;
  - sinossi;
  - stato produzione/uscita;
  - durata per film o informazioni stagioni per serie;
  - pulsante libreria;
  - rating personale, se già pianificato qui.
- Stato “dati locali non aggiornati” in caso di errore refresh.
- refresh manuale.

## Test e verifiche

- cache vuota → rete → persistenza;
- cache fresca → nessuna chiamata inutile;
- cache scaduta → dati locali mostrati + refresh;
- errore rete con cache disponibile;
- mapper per campi null o incompleti.

## Exit criteria

- Il dettaglio si apre dalla Ricerca e dalla Libreria.
- Il provider non è visibile come dettaglio infrastrutturale alla UI, salvo attribuzione/debug.
- Un errore remoto non elimina dati già salvati.

---

# Milestone 6 — Stagioni, episodi e progressi

## Scopo

Implementare il cuore del tracking delle serie TV.

## Deliverable

- Tabelle Room:
  - `seasons`;
  - `episodes`;
  - `watch_progress`.
- Endpoint stagioni/episodi TMDB necessari.
- Sincronizzazione cache di stagioni ed episodi.
- UI dettaglio serie con elenco stagioni ed episodi.
- Azioni:
  - segna episodio visto/non visto;
  - segna stagione vista/non vista;
  - mostra avanzamento numerico;
  - aggiorna il progresso dell'opera.
- Gestione stagione zero/speciali.
- Timestamp `watchedAt` coerente.
- Film: azione visto/non visto separata dagli episodi.

## Regole di dominio da testare

- stagione completa solo se tutti gli episodi rilevanti sono visti;
- toggle di una stagione non deve coinvolgere stagioni diverse;
- refresh dei metadati non deve perdere progressi locali;
- nuovi episodi aggiunti possono rendere una stagione precedentemente completa non completa: documentare la policy UI;
- progressi persistenti anche se il titolo viene aggiornato.

## Test e verifiche

- test regole progressione;
- transazioni bulk toggle;
- test refresh preservando watch progress;
- test speciali/stagione zero;
- UI test del flusso dettaglio → toggle episodio.

## Exit criteria

- L'utente può tracciare una serie completa offline dopo che i dati sono stati caricati.
- Nessun refresh remoto sovrascrive lo stato personale.
- Operazioni bulk sono transazionali.

---

# Milestone 7 — Rating e miglioramento libreria

## Scopo

Completare il modello personale minimo senza introdurre funzioni social.

## Deliverable

- Rating a livello di opera con scala documentata.
- Modifica e rimozione rating.
- Ordinamento libreria per:
  - aggiunta recente;
  - titolo;
  - progresso;
  - rating, se utile.
- Filtri per stato:
  - da iniziare;
  - in corso;
  - completato;
  - visto per i film.
- Ricerca locale nella libreria.
- Statistiche minime solo se derivabili localmente senza ampliare eccessivamente lo scope.

## Test e verifiche

- validazione range rating;
- query Room per filtri/ordinamenti;
- stato derivato serie/film;
- persistenza rating.

## Exit criteria

- L'utente può organizzare una libreria non banale senza rete.
- Rating e filtri non dipendono dal provider remoto.

---

# Milestone 8 — Calendario locale delle prossime uscite

## Scopo

Costruire la Home come calendario dei titoli seguiti, senza chiamate remote bloccanti all'apertura.

## Deliverable

- Tabella `release_events`.
- Query Home per eventi futuri e recenti.
- Generazione eventi da metadati TMDB.
- Supporto almeno a:
  - uscita film;
  - debutto stagione;
  - messa in onda episodio, quando disponibile.
- Home raggruppata per data.
- Stato ultimo aggiornamento.
- refresh manuale.
- deduplicazione idempotente degli eventi.
- gestione timezone/date-only documentata.

## Vincoli

- La Home legge solo Room.
- Nessuna chiamata diretta automatica della schermata per costruire il calendario.
- Non ridurre il calendario a un solo `nextAirDate` per titolo.

## Test e verifiche

- generazione eventi;
- deduplicazione;
- ordinamento per data;
- eventi aggiornati/cancellati dal provider;
- date al confine di timezone;
- titolo rimosso dalla libreria.

## Exit criteria

- La Home si apre istantaneamente con dati locali.
- Più eventi dello stesso titolo possono convivere.
- Refresh ripetuti non generano duplicati.

---

# Milestone 9 — WorkManager e notifiche locali

## Scopo

Aggiornare periodicamente libreria e calendario e notificare eventi rilevanti.

## Deliverable

- Worker periodico di refresh.
- Scheduling unico e idempotente.
- Constraints di rete/batteria ragionevoli.
- backoff per errori temporanei.
- elaborazione in lotti.
- gestione key assente/invalida senza retry infinito.
- canale notifiche.
- richiesta permesso notifiche sulle versioni Android pertinenti.
- preferenze:
  - notifiche abilitate;
  - anticipo selezionabile tra opzioni finite;
  - categorie notificabili, se utile.
- registro locale minimo per evitare notifiche duplicate.

## Test e verifiche

- Worker con fake repository/clock;
- idempotenza;
- retry vs failure definitivo;
- evento già notificato;
- permesso negato;
- cambio preferenze.

## Exit criteria

- Un refresh manualmente forzato aggiorna eventi senza duplicati.
- Il worker non danneggia progressi o dati offline.
- La notifica non viene duplicata per lo stesso evento e configurazione.

---

# Milestone 10 — Export e restore JSON versionato

## Scopo

Garantire realmente la proprietà e portabilità dei dati.

## Deliverable

- Formato JSON documentato con `schemaVersion`.
- Export di:
  - libreria;
  - riferimenti esterni;
  - progressi film/episodi;
  - rating;
  - preferenze esportabili selezionate.
- Esclusione di API key e dati tecnici sensibili.
- Condivisione/salvataggio tramite Storage Access Framework.
- Import con:
  - parsing;
  - validazione;
  - anteprima riepilogativa;
  - gestione duplicati;
  - transazione atomica;
  - report errori.
- Strategia merge vs replace esplicita. Per l'MVP preferire una sola modalità sicura e ben documentata.
- fixture di backup incluse nei test.

## Test e verifiche

- round trip export → clear DB → restore;
- JSON malformato;
- versione non supportata;
- riferimenti mancanti;
- duplicati;
- import interrotto senza modifiche parziali;
- verifica automatica che la key non sia esportata.

## Exit criteria

- Un utente può reinstallare l'app e ripristinare la propria cronologia.
- Il formato è stabile, documentato e versionato.
- Un file invalido non corrompe il database.

---

# Milestone 11 — Hardening, accessibilità e beta Android

## Scopo

Portare il prodotto TMDB-first a una beta pubblica affidabile.

## Deliverable

- audit accessibilità:
  - screen reader;
  - font scaling;
  - contrasto;
  - target touch;
  - semantics;
  - reduced motion dove pertinente.
- gestione completa loading/empty/error/offline.
- performance profiling dei flussi principali.
- paging/liste ottimizzate se necessario.
- migrazione Room di prova v1 → v2 per validare il processo, anche se minima e utile.
- test su process death e restore dello stato essenziale.
- test su configurazione/rotazione.
- privacy policy e schermata About.
- attribution e licenze dipendenze.
- icona, branding originale e screenshot.
- pipeline release firmata documentata senza segreti nel repository.
- checklist F-Droid/APK/Play Store, senza obbligo di pubblicare in tutti i canali.

## Test e verifiche

- suite completa unit test;
- assemble debug e release;
- lint;
- test migrazioni;
- test strumentali dei flussi critici;
- smoke test su almeno due versioni Android o emulatori configurati;
- verifica installazione pulita e aggiornamento.

## Exit criteria

- Nessun blocker noto di perdita dati o crash nei flussi principali.
- README permette installazione, configurazione e contributo.
- La beta è distribuibile senza inserire segreti nel binario o nel repository.

---

# Milestone 12 — Import da TV Time / formato esterno

## Scopo

Ridurre il costo di adozione per utenti con cronologia preesistente.

## Prerequisito

Ottenere e documentare esempi reali, anonimizzati e legalmente utilizzabili del formato di export da supportare. Non implementare parser basati su supposizioni.

## Deliverable

- Modello intermedio provider-agnostic `ImportedWatchRecord`.
- Parser isolato dal database.
- Normalizzazione titoli, date, stagioni ed episodi.
- Matching con TMDB:
  - automatico ad alta confidenza;
  - revisione manuale per ambigui;
  - possibilità di saltare elementi.
- anteprima import;
- report finali:
  - importati;
  - duplicati;
  - non riconosciuti;
  - errori.
- transazione finale solo dopo conferma dell'utente.

## Test e verifiche

- fixture reali anonimizzate;
- titoli omonimi;
- film vs serie;
- episodi mancanti;
- date assenti;
- import ripetuto;
- annullamento prima del commit.

## Exit criteria

- Nessun record ambiguo viene collegato silenziosamente con bassa confidenza.
- L'import può essere rieseguito senza moltiplicare i progressi.

## Stato Milestone 12B — 2026-08-04

È presente un'implementazione sperimentale limitata al profilo JSON ZIP role-based documentato da `TVTIME-SAMPLE-001`: SAF, ZIP bounded e sicuro, parsing strutturale, matching TMDB conservativo, review/skip, anteprima e transazione Room additiva/idempotente. CSV, varianti TV Time non verificate, rating, preferiti, liste custom, rewatch timeline, autenticazione/rete TV Time e sostituzione dati restano fuori scope. La milestone resta parziale finché non sono completate le verifiche runtime/manuali e il full connected suite non supera il timeout esistente.

---

# Milestone 13 — Integrazione Jikan e anime

## Scopo

Aggiungere anime senza compromettere identità, progressi e UX già stabili.

## Prerequisiti

- MVP TMDB stabile.
- Modello `ExternalMediaRef` già usato ovunque.
- ADR su UX ricerca multi-provider.
- ADR sulla rappresentazione di anime divisi in entry/cour/sequel.

## Deliverable

- client Jikan separato;
- rate limiting rispettoso e caching appropriato;
- DTO e mapper Jikan;
- ricerca anime in tab o sezione separata nella prima iterazione;
- dettaglio anime;
- libreria e progressi compatibili con fonte Jikan;
- eventi di uscita quando affidabili;
- attribuzioni e documentazione provider;
- gestione errori/rate limit dedicata.

## Vincoli

- Non fondere automaticamente risultati TMDB/Jikan.
- Non presumere equivalenza tra episodi TMDB e MAL/Jikan.
- Non degradare il percorso TMDB quando Jikan non è disponibile.

## Test e verifiche

- mapper Jikan;
- rate limit/backoff;
- ID collisione numerica tra provider;
- libreria mista;
- ricerca con provider indipendenti;
- offline con cache anime.

## Exit criteria

- Titoli TMDB e Jikan con lo stesso ID numerico non collidono.
- Un errore Jikan non blocca ricerca o libreria TMDB.
- La UI rende evidente la provenienza quando necessario, senza esporre dettagli tecnici inutili.

---

# Milestone 14 — Ricerca unificata e deduplicazione assistita (opzionale)

## Scopo

Valutare una UX mista solo dopo aver raccolto dati reali sull'uso dei due provider.

## Deliverable possibili

- ranking normalizzato per provider;
- regole conservative di potenziale equivalenza;
- suggerimento “possibile duplicato” invece di merge automatico;
- collegamento manuale di riferimenti esterni a una stessa entità locale;
- possibilità di separare un collegamento errato;
- audit trail delle fusioni.

## Vincoli

- Nessun merge irreversibile.
- Nessun matching basato solo sul titolo.
- Richiedere almeno anno/tipo e altri segnali.
- Progressi non devono essere persi durante link/unlink.

## Exit criteria

- La deduplicazione è coperta da test con casi ambigui.
- L'utente mantiene controllo sulle associazioni.

---

# Milestone 15 — Preparazione futura multipiattaforma (solo se giustificata)

## Scopo

Preparare iOS o codice condiviso soltanto dopo la validazione del prodotto Android.

## Attività preliminari

- misurare quali componenti sono realmente condivisibili;
- stabilizzare formato export e dominio;
- valutare Kotlin Multiplatform senza migrazione obbligatoria;
- evitare di spostare UI Android o Room prematuramente.

## Possibili deliverable

- specifica formale del backup come contratto multipiattaforma;
- libreria Kotlin pura per parsing/validazione export;
- dominio privo di dipendenze Android dove utile;
- ADR build-vs-rewrite per client iOS.

## Exit criteria

- La decisione multipiattaforma è supportata da costi/benefici reali.
- Android non viene rallentato o destabilizzato per un client non ancora pianificato.

---

# Ordine consigliato delle release

## Alpha interna

Milestone 0–6:

- setup;
- key TMDB;
- ricerca;
- libreria;
- dettagli;
- progressi.

## Alpha pubblica

Milestone 7–10:

- rating e filtri;
- calendario;
- worker/notifiche;
- backup/restore.

## Beta pubblica

Milestone 11:

- hardening;
- accessibilità;
- packaging;
- documentazione open source.

## Post-beta

Milestone 12–15:

- import esterno;
- anime/Jikan;
- eventuale ricerca unificata;
- preparazione multipiattaforma.

---

# Checklist standard per ogni milestone

## Prima di implementare

- [ ] `git status` controllato.
- [ ] `AGENTS.md` letto.
- [ ] criteri di accettazione identificati.
- [ ] file e flussi esistenti ispezionati.
- [ ] nessun lavoro utente verrà sovrascritto.

## Durante l'implementazione

- [ ] incremento verticale piccolo.
- [ ] dominio separato da DTO/Room/UI.
- [ ] errori e stato vuoto gestiti.
- [ ] test aggiunti insieme al codice.
- [ ] nessun segreto o log sensibile.

## Prima di concludere

- [ ] formatter/lint eseguiti.
- [ ] test mirati eseguiti.
- [ ] suite prevista dalla milestone eseguita.
- [ ] build debug riuscita.
- [ ] diff revisionato.
- [ ] schema/ADR/documenti aggiornati.
- [ ] report finale accurato, senza dichiarazioni non verificate.

---

# Template di incarico per Codex

Usare questo schema quando si assegna una milestone o parte di essa:

```text
Implementa [MILESTONE/TASK] seguendo AGENTS.md e IMPLEMENTATION_PLAN.md.

Obiettivo:
[risultato utente osservabile]

Scope incluso:
- ...

Fuori scope:
- ...

Criteri di accettazione:
- ...

Verifiche obbligatorie:
- ...

Prima di modificare il codice, ispeziona il repository e segnala eventuali conflitti con l'architettura esistente. Implementa il più piccolo incremento verticale completo. Non committare né pubblicare senza richiesta esplicita. Nel report finale indica file modificati, test eseguiti, risultati, assunzioni e debito residuo.
```
