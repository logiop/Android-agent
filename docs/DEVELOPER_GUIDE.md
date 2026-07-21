# Guida sviluppatore — Android Agent

Guida operativa per **verificare e usare l'app in fase di sviluppo**, pensata
per un workflow **phone-first** (sviluppo dal telefono, senza PC). Ogni push
produce un APK installabile; questa guida spiega come ottenerlo, configurarlo,
usarlo e diagnosticare i problemi.

- Target reale: **Samsung Galaxy S26 Ultra** (Android 14+).
- Min SDK 24 · Compile/Target SDK 34 · Kotlin 2.0 · JDK 17 · Gradle 8.7.
- LLM on-device: **MediaPipe LLM Inference** `tasks-genai:0.10.24` (Gemma-3).

---

## 1. Come è fatta l'app (mappa per orientarsi)

Package sotto `com.logiop.androidagent`:

| Package | Ruolo |
| --- | --- |
| *(root)* | `MainActivity` (home/permessi), `WhitelistActivity`, `LogActivity`, `SkillsActivity`, `SkillReviewActivity` |
| `overlay` | `OverlayService`: bolla flottante, stati, dialog di conferma, orchestrazione |
| `voice` | `VoiceRecognizer`: SpeechRecognizer italiano on-device |
| `hands` | `AgentAccessibilityService` (legge/agisce), `ScreenReader`, `UiElement`, `AppLauncher`, `CommandInterpreter` |
| `brain` | `Brain` (MediaPipe), `PromptBuilder`, `ActionParser`, `AgentAction`, `ModelRepository` |
| `agent` | `AgentLoop` (il loop), `Whitelist`, `SafetyPolicy`, `StateVerifier` |
| `memory` | `MemoryDb`/`SkillStore` (SQLite), `SkillCompiler`, `TrajectoryCodec`, modelli |
| `security` | `AuditKey` (Keystore), `AuditLog` (log cifrato AES-256/GCM) |

Flusso di un comando: **voce → CommandInterpreter** → se "apri…/cerca…"
esecuzione deterministica; altrimenti **AgentLoop** (cattura schermo → `Brain`
pianifica azione JSON → esegue → ri-legge, max 15 step, stop dopo 3 senza
progresso). Le azioni di controllo passano dal gate **whitelist** + **conferma
irreversibili**. Una run riuscita e "learnable" può diventare una **skill**.

---

## 2. Workflow phone-first: dal push all'APK

Non serve un PC: **GitHub Actions** compila l'APK ad ogni push.

1. Ogni push su qualsiasi branch avvia il workflow `android`
   (`.github/workflows/android.yml`): esegue gli **unit test** + `assembleDebug`.
2. A build riuscita l'APK viene pubblicato nella **release `debug-latest`**
   (tag mobile, link stabile). Non è uno zip: è l'`app-debug.apk` diretto.
3. Dal telefono: repo su GitHub → **Releases** → `Debug build (latest)` →
   scarica `app-debug.apk` → installa (abilita "origini sconosciute").

Verifica CI: tab **Actions** del repo → il run deve essere verde. Se rosso,
apri il job `build`: gli errori di compilazione o gli unit test falliti sono lì.

> Il primo run dopo un cambio di dipendenze è più lento (scarica SDK + artefatti).

---

## 3. Build locale (solo se hai un PC — opzionale)

```bash
# crea local.properties con il path dell'SDK Android
echo "sdk.dir=/percorso/Android/sdk" > local.properties

./gradlew testDebugUnitTest   # unit test JVM
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installa su device/emulatore collegato
```

Nota: le funzioni chiave (overlay, biometria, accessibilità, microfono, LLM)
**non si testano su emulatore** in modo significativo — serve un device reale.

---

## 4. Procurarsi il modello LLM (obbligatorio per il "brain")

Il modello **non è nell'APK** (troppo grande). Serve un **bundle MediaPipe
`.task`, Gemma-3 1B, quantizzato int4**.

- ❌ NON vanno bene: `.gguf` (è per llama.cpp), `.safetensors`, `.tflite` "nudo".
- Il file `.task` di Gemma-3 richiede runtime ≥ 0.10.22 → noi siamo su **0.10.24**. OK.

### Da dove scaricarlo (percorso affidabile)
Dal **browser del telefono**, da Hugging Face **`litert-community/Gemma3-1B-IT`**:
Files → un file `.task` **int4** → accetta la licenza Gemma (login HF) → scarica
in **Download**. Così il selettore file dell'app lo vede.

> **Edge Gallery**: l'app Google AI Edge Gallery scarica gli stessi modelli, ma
> nella **propria memoria privata** (`Android/data/...`), che su Android 13+ non
> è accessibile dal nostro selettore. Usala semmai come *controprova* che il
> modello gira sul device; per l'import nella nostra app scarica il `.task` dal
> browser come sopra.

### Importare e persistenza
In app: **Importa modello LLM** → seleziona il `.task`. Viene **copiato** in
`filesDir/models/model.task` (storage interno privato), quindi **persiste dopo
il riavvio** — non dipende dall'URI di download. Stato atteso: *"Modello LLM:
pronto"*.

---

## 5. Setup iniziale sul device (una volta)

Nella `MainActivity`, in ordine:

1. **Concedi permesso overlay** → abilita "Mostra sopra le altre app".
2. **Concedi microfono** (`RECORD_AUDIO`).
3. **Abilita accessibilità** → attiva il servizio "Android Agent".
4. **Importa modello LLM** (vedi §4).
5. **Gestisci whitelist app** → attiva le app in cui l'agente può toccare/
   scrivere/scorrere (di default **tutto è bloccato**).

Ogni riga di stato in home diventa "verde/pronto" quando il rispettivo permesso
è a posto.

---

## 6. Usare l'app

### Attivazione
**Attiva agente** → sblocco biometrico → compare la **bolla** flottante
(trascinabile). Colori della bolla:
- viola = idle · verde = in ascolto · blu = sto pensando (LLM) · rosso = errore/permesso mancante.

Disattivazione: azione **Disattiva** nella notifica, o pulsante in app.

### Comandi deterministici (non usano l'LLM)
Tocca la bolla e parla:
- **"apri &lt;app&gt;"** / "avvia &lt;app&gt;" → apre l'app per nome.
- **"cerca &lt;query&gt; su google"** / "google &lt;query&gt;" → ricerca web.

### Comandi liberi (loop LLM)
Qualsiasi altro comando entra nel loop: la bolla diventa blu, il modello
pianifica un'azione `{action,target,text}`, l'agente la esegue e ri-legge lo
schermo. Le azioni `open_app`/`search` sono sempre permesse; `tap`/`type`/
`scroll` solo in app **whitelisted**.

### Sicurezza in azione
- Azione **irreversibile** (invia/pubblica/elimina/paga…): compare il **dialog
  di conferma** prima di eseguirla.
- Azione di controllo in app **non** in whitelist → **bloccata** (toast).
- **Mostra log azioni** → log **decifrato** di comandi/azioni/blocchi/conferme.

### Apprendimento skill (Fase 3.1)
Se una run libera ha successo *e lo stato è cambiato davvero*, parte **"Salva
come skill?"**:
- Il **pattern** è pre-compilato con `{graffe}` sui parametri (estratti dai testi
  che hai digitato). Correggilo se serve (aggiungi/rinomina `{slot}`).
- I **passi registrati** sono in sola lettura (`⚠` = step irreversibile).
- **Salva skill** → la skill nasce in **quarantena**; la trovi in **"Skill
  apprese"** (con elimina).

> Il **riuso/replay** delle skill è la Fase 3.2, **non ancora attivo**: per ora
> le skill si apprendono e si salvano soltanto.

---

## 7. Checklist di verifica per fase (con esiti attesi)

### Fase 1 — agente base
| Passo | Esito atteso |
| --- | --- |
| Installa APK, apri app | Home con righe di stato e pulsanti |
| Concedi overlay/mic/accessibilità | Le 3 righe di stato diventano "attivo/concesso" |
| Importa modello `.task` | "Modello LLM: pronto" |
| Attiva agente + biometria | Compare la bolla; è trascinabile |
| "apri Chrome" | Chrome si apre (toast "Apro chrome…") |
| "cerca meteo Genova su Google" | Parte la ricerca Google |
| Nega il microfono e tocca la bolla | Bolla **rossa** + messaggio, ti manda all'app (mai silenzio) |
| Comando libero, bolla blu | Il modello risponde e l'azione viene eseguita/riportata |
| Azione irreversibile | Dialog di conferma prima di eseguire |
| Azione in app non-whitelist | Bloccata con toast |
| Mostra log azioni | Righe decifrate con timestamp |
| Riavvia il telefono, riapri | "Modello LLM: pronto" (persistenza) |

### Fase 3.0/3.1 — memoria e apprendimento
| Passo | Esito atteso |
| --- | --- |
| Run libera riuscita con stato cambiato | Parte "Salva come skill?" |
| Pattern proposto | Comando con `{slot}` sui testi digitati |
| Salva skill | Compare in "Skill apprese", stato *quarantine* |
| Elimina una skill | Sparisce dalla lista |
| Riavvia e riapri "Skill apprese" | Le skill salvate sono ancora lì (SQLite persistente) |
| Run che non cambia lo schermo | **Nessuna** proposta di skill (StateVerifier) |

---

## 8. Troubleshooting

| Sintomo | Causa probabile | Rimedio |
| --- | --- | --- |
| "Modello LLM assente" / errore del modello | `.task` non importato o incompatibile | Importa un **Gemma-3 1B int4 `.task`** (non gguf/safetensors); vedi §4 |
| Il modello non carica / crash all'inferenza | Formato/versione modello non compatibile col runtime | Usa un `.task` Gemma-3 (runtime 0.10.24); prova la variante int4 con context più piccolo |
| Bolla rossa quando parlo | Microfono negato o riconoscimento non disponibile | Concedi `RECORD_AUDIO`; verifica pacchetto lingua italiano |
| "Accessibilità disattivata" | Servizio non abilitato | Abilita "Android Agent" in Impostazioni → Accessibilità |
| Azione di controllo bloccata | App non in whitelist | Aggiungi l'app in "Gestisci whitelist app" |
| Nessuna proposta di skill dopo una run | Stato non cambiato o 0 step di controllo | Atteso: `StateVerifier` evita skill "vuote" |
| "apri Chrome e cerca X" apre solo Chrome | Comando composto: lo shortcut deterministico prende solo la prima app | Dai due comandi separati, oppure lascia che sia il loop LLM a gestirlo |
| Prima inferenza lentissima | Caricamento del modello in memoria | Normale; le run successive sono più rapide |
| CI rossa dopo un push | Errore di compilazione o unit test fallito | Apri Actions → job `build` → leggi l'errore |

---

## 9. Diagnostica avanzata (se hai un PC con adb)

I dettagli tecnici (albero UI compatto, output grezzo dell'LLM, azioni) sono nel
logcat con tag **`AndroidAgent`**:

```bash
adb logcat -s AndroidAgent
```

Senza PC, la fonte di verità è la schermata **"Mostra log azioni"** (log cifrato
delle azioni eseguite: comando, azioni, blocchi, conferme, fine).

---

## 10. Stato e limiti noti (onestà tecnica)

- **Replay skill (3.2) non attivo**: le skill non si riusano ancora → nessun
  risparmio di velocità/LLM in questa fase.
- **Il 1B è sotto la soglia di pianificazione autonoma affidabile**: su task
  multi-step aperti può sbagliare; per questo la conferma umana (skill) e le
  scorciatoie deterministiche sono parte dell'architettura, non un extra.
- **MediaPipe LLM Inference è in maintenance-only**: in futuro migrazione a
  LiteRT-LM. Il parametro di sampling (temperatura/topK) è ai default del
  modello (rimosso per compatibilità col runtime nuovo).
- **Compatibilità modello↔runtime**: un `.task` più nuovo del runtime non carica.
  Con 0.10.24 usa Gemma-3 1B int4.

---

## 11. Convenzioni di sviluppo

- **Branch di lavoro**: `claude/kotlin-scaffold-l6csoe`; PR verso `main`.
- **CI obbligatoria verde** prima del merge (unit test + build APK).
- **Unit test JVM** per la logica pura: `CommandInterpreter`, `ActionParser`,
  `SafetyPolicy`, `StateVerifier`, `SkillCompiler`. Aggiungine per ogni nuova
  logica testabile senza device.
- **Ogni step di sviluppo si chiude con un APK** scaricabile da `debug-latest`.
