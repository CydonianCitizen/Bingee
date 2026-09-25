# Ricerca feature da app simili

Ricerca svolta il 2026-09-12 su TV Time, Trakt, Serializd, Simkl, Showly, Moviebase, Hobi, Rewatch,
Sofa Time, Sequel e Letterboxd. Ogni lacuna elencata è stata verificata nel codice di Bingee `1.2.0`,
non dedotta dalla documentazione.

Contesto rilevante: **TV Time ha chiuso il 15 luglio 2026**. Chi migra ha in mano un export che oggi
Bingee legge solo in parte.

## Priorità alta: logica di tracciamento

### 1. Import dei CSV di TV Time

- **Chi lo fa:** Hobi, Trakt, Rewatch, Moviebase, TVmaze e Bingers (l'app del fondatore di TV Time).
- **Formato dell'export GDPR:** uno ZIP di CSV. `tracking-prod-records-v2.csv` contiene serie ed
  episodi, `tracking-prod-records.csv` i film, i voti stanno in file `ratings-*.csv` separati.
- **Stato in Bingee:** l'importer accetta solo il profilo JSON `TVTIME-SAMPLE-001` ed esclude
  esplicitamente i CSV (`docs/imports/tv-time-source-format-v1.md`).
- **Limite:** le colonne dei CSV non sono documentate pubblicamente. Serve un export reale come
  prova, come richiede l'ADR 0018.

### 2. Backup automatico periodico

- **Chi lo fa:** Rewatch, con backup giornalieri e sync tramite il cloud personale, senza server.
- **Stato in Bingee:** l'export esiste solo manuale.
- **Come:** un Worker di WorkManager che scrive il backup v2 in una cartella scelta via SAF
  (`takePersistableUriPermission`). Se la cartella sta su Google Drive si ottiene un backup cloud
  senza backend, dentro il perimetro local-first.

### 3. "Segna come visti anche i precedenti" — implementato

- **Chi lo fa:** TV Time lo chiedeva quando si spuntava un episodio avanzato; sul forum Trakt è una
  richiesta ricorrente.
- **In Bingee:** spuntando un episodio con episodi precedenti non visti, Dettagli chiede se segnare
  anche quelli. Le stagioni precedenti mai aperte vengono prima scaricate da TMDB; se una fallisce
  non si scrive nulla. Sono inclusi solo gli episodi regolari già usciti; gli speciali restano
  esclusi e gli episodi già visti mantengono la loro data.

### 4. Data di visione degli episodi

- **Chi lo fa:** Trakt fa scegliere la data (anche "alla data d'uscita"); Rewatch registra una data
  per ogni visione.
- **Stato in Bingee:** gli episodi salvano solo `watchedAt = clock.instant()`; solo i film hanno
  `watchedDate`.
- **Effetto probabile (inferenza, non verificata):** segnare un'intera serie in un colpo solo
  concentra tutte le visioni in un solo mese dell'istogramma mensile.

### 5. Stato "In pausa"

- **Chi lo fa:** Hobi e Rewatch. Una serie in pausa esce dal calendario senza perdere lo storico.
- **Stato in Bingee:** esiste solo "Abbandonata".
- **Come:** un flag gemello di `isAbandoned`, escluso da Continue Watching, calendario e notifiche.

### 6. Rewatch

- **Chi lo fa:** Rewatch, Trakt (VIP) e Letterboxd registrano ogni visione.
- **Stato in Bingee:** `EpisodeWatchProgressEntity` ha come chiave `localEpisodeId`, quindi una sola
  visione per episodio.
- **Costo:** migrazione Room più backup v3. È lo sforzo più alto; da fare dopo il punto 4, che ne è
  la base.

## Priorità media: UI e organizzazione

| Feature | Chi ce l'ha | Nota per Bingee |
|---|---|---|
| Protezione spoiler: sfoca l'immagine e nasconde il titolo degli episodi non visti | Moviebase, Showly, Simkl | **Implementato** in Impostazioni → Aspetto, attivo nei Dettagli. Calendario, Centro notifiche e notifiche di sistema mostrano ancora i titoli degli episodi in uscita. |
| Note personali per titolo | Trakt, Rewatch, Letterboxd | Una colonna in Room più il campo nel backup. |
| Liste personalizzate, anche ordinate (Top 10) | Showly, Moviebase, Sofa Time, Sequel, Trakt | Sforzo medio-alto. I tag stile Letterboxd si coprono con le liste. |
| Cronologia / diario | Letterboxd, Rewatch, Trakt | Già in roadmap ("La tua storia"). I dati (`watchedAt`) esistono. |
| Riepilogo annuale | Trakt, Letterboxd | Già in roadmap. Può riusare le statistiche esistenti. |
| Cast, trailer e voto TMDB nei dettagli | Sequel, Moviebase | `append_to_response=credits,videos`; il trailer si apre con un intent verso YouTube. |
| Scorciatoie dal launcher | Showly | **Implementato**: Cerca, In visione, Da vedere (`res/xml/shortcuts.xml`, `AppShortcut`). |
| Widget per watchlist o countdown | Sofa Time, Sequel, Hobi | Il widget 4×4 è già tra i rinvii in roadmap. |
| Scale di voto alternative (5 stelle, pollice) | Rewatch | Priorità bassa. |

## Fuori scope secondo AGENTS.md

Dove guardarlo / JustWatch, raccomandazioni o titoli simili, funzioni social e reazioni agli episodi
(il punto forte di TV Time), sync con Trakt e scrobbling, import da Letterboxd. Material You sarebbe
in conflitto con la palette del brand, che `PRODUCT.md` dichiara autoritativa.

## Fonti

- [9to5Mac – Bingers e chiusura di TV Time](https://9to5mac.com/2026/08/04/bingers-a-new-tv-tracking-app-from-the-founder-of-tv-time-is-now-available-to-download/)
- [Achriom – TV Time alternatives 2026](https://www.achriom.com/blog/best-tv-tracking-apps/)
- [Hobi – best TV trackers 2026](https://hobiapp.com/blog/best-tv-show-tracker-apps)
- [Hobi – stati e import TV Time](https://hobiapp.com/tv-time)
- [Hobi – struttura dell'export TV Time](https://hobiapp.com/blog/how-to-export-tv-time-data)
- [TV Track – export TV Time](https://tvtrack.io/export-tv-time-data)
- [Rewatch (App Store)](https://apps.apple.com/es/app/rewatch/id6761073539)
- [Moviebase](https://play.google.com/store/apps/details?id=com.moviebase&hl=en_US)
- [Showly su GitHub](https://github.com/wildcatstudios/showly)
- [Sofa Time](https://play.google.com/store/apps/details?id=com.theclashsoft.sofatime)
- [Sequel (App Store)](https://apps.apple.com/gh/app/sequel-media-tracker/id1630746993)
- [Letterboxd – funzioni](https://letterboxd.com/welcome/)
- [Trakt – Year in Review](https://trakt.medium.com/year-in-review-5c6ac98f0d3c)
- [Trakt Forums – mark all previous episodes](https://forums.trakt.tv/t/mark-all-previous-episodes-as-watched/115121)
- [Simkl – Spoilers Protection](https://docs.simkl.org/how-to-use-simkl/getting-started-with-simkl/account-creation/basic-account-setup/profile-settings/spoilers-protection)
