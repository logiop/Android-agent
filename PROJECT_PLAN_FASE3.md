# Fase 3 — Apprendimento e Memoria Procedurale

## Obiettivo
Dare all'agente la capacità di imparare a usare app che non conosce e di
ricordare come si fanno le cose. Quando l'agente affronta un task nuovo su
un'app (es. editare un video in CapCut), lo esegue passo-passo ragionando col
modello; l'utente approva o corregge i passaggi; la sequenza che ha funzionato
viene salvata come **skill riutilizzabile** in una memoria locale sul telefono.

La volta successiva, per lo stesso tipo di task, l'agente riusa la skill già
appresa senza doverla reimparare — più veloce, più affidabile, e senza (o con
molte meno) chiamate al modello. Questo trasforma l'agente da esecutore
stateless ad assistente che migliora con l'uso.

## Principio architetturale di fondo
Pattern validato da progetti reali (SkillDroid, Voyager, AutoDroid,
Mobile-Agent-E, LearnAct): ciclo a tre stadi **Impara → Compila → Riusa**.

1. **Impara**: loop LLM step-by-step (riusa il loop agente esistente) con
   verifica di stato ad ogni passo.
2. **Compila**: al successo, la trajectory viene trasformata in una skill
   parametrizzata e salvata.
3. **Riusa**: al task successivo simile, la skill viene recuperata ed eseguita
   in "replay" senza il modello, con verifica e fallback.

## Ordine di precedenza dell'esecuzione
Per ogni comando, l'agente prova in quest'ordine:

1. **Scorciatoia deterministica** (già in Fase 1): `open_app`, `search`, ecc.
2. **AppFunctions** (Jetpack, quando disponibile su Android recenti): se l'app
   target espone la funzione richiesta via linguaggio naturale, usala — è più
   affidabile di qualsiasi sequenza di tap. Da valutare in 3.3.
3. **Skill appresa** (nuovo in Fase 3): se esiste una skill che combacia col
   task e con l'app, esegui in replay.
4. **Apprendimento da zero** (nuovo in Fase 3): esegui col modello step-by-step,
   e se ha successo compila una nuova skill.

## Struttura della memoria
Tre tipi di memoria (tassonomia standard degli agenti):

- **Procedurale** = la skill library (come fare le cose). È il cuore della Fase 3.
- **Episodica** = log delle trajectory eseguite (cosa è successo, quando).
- **Semantica** = preferenze e fatti stabili sull'utente (es. app di
  messaggistica preferita, formato data).

## Formato di una skill
Ogni skill contiene:

- **intent pattern** con slot placeholder — es. "invia messaggio a {contatto}
  dicendo {testo}" — così una sola skill generalizza a molti casi.
- **lista di step**, dove ogni step ha:
  - tipo di azione (tap/type/scroll/open_app);
  - **locator resiliente multi-feature**: non un solo selettore ma più
    caratteristiche del nodo (resourceId, testo, contentDescription, classe,
    genitore, fratello) con pesi, così se una cambia le altre reggono il match;
  - **descrittore dello stato atteso** dopo lo step (per verificare che sia
    andato a buon fine);
  - binding dei parametri/slot.

## Storage e recupero
- Skill persistite in **SQLite**.
- Recupero per **similarità semantica**: si genera un embedding dell'intent
  pattern e si cerca la skill più vicina al comando dell'utente, con in più un
  match testuale/regex e il filtro sull'app target.
- Valutare **Google AI Edge RAG SDK** (Embedder locale + VectorStore su SQLite)
  con un modello di embedding piccolo (Gecko ~110M o EmbeddingGemma ~308M, che
  supporta bene l'italiano). Taggare la versione del modello di embedding nel DB.

## Ciclo di apprendimento con l'utente nel loop
1. Comando nuovo → l'agente esegue col modello, uno step alla volta.
2. Dopo ogni step, verifica lo stato reale dello schermo (non fidarsi del "ho
   finito" dichiarato dal modello: va controllato l'albero UI).
3. Le azioni irreversibili passano già dalla conferma umana (Fase 1): quella
   conferma diventa anche il segnale "questo step è corretto".
4. Al completamento, l'agente propone all'utente la skill dedotta (sequenza +
   parametri individuati) per approvazione/correzione. Dato che il modello 1B è
   debole nella parametrizzazione, questa conferma umana è un componente
   necessario, non un extra.
5. Skill approvata → salvata in stato "in prova" (**quarantena**).

## Replay robusto
- Match della skill → esecuzione step-by-step in replay.
- Ad ogni step, verifica dello stato con **tolleranza graduata**: deviazione
  nulla/lieve → procedi; deviazione media → gestisci i dialog imprevisti (es.
  popup permessi) e riprova; deviazione grave → interrompi e torna alla modalità
  apprendimento per quel pezzo.
- **Controllo di contesto prima del replay** (guard condition): se la schermata
  di partenza non è quella attesa, non eseguire alla cieca.
- **Fallback al modello limitato** (pochi step, con tetto) quando un elemento
  non si trova, senza rifare tutto da capo.

## Apprendimento dai fallimenti
- Registrare ogni fallimento (quale step, che tipo di deviazione, stato UI).
- Se una skill supera una soglia di fallimenti (~50%), va segnalata per
  ricompilazione (le UI cambiano: la skill va riaggiornata).
- Le sequenze di recupero diventano nuove skill senza sovrascrivere l'originale.

## Sicurezza (requisiti aggiuntivi obbligatori della Fase 3)
La memoria persistente e il replay autonomo introducono rischi nuovi:

- **Skill appresa male** → nessuna skill viene salvata senza verifica di
  successo E approvazione umana; le skill nuove restano "in prova" finché non
  accumulano N successi verificati in modalità supervisionata.
- **Avvelenamento della memoria** (testo malevolo letto dallo schermo che
  inquina una skill): non compilare skill da trajectory che includono contenuto
  UI non fidato senza revisione; validare ogni scrittura in memoria; mantenere
  un audit log locale delle skill (chi/quando le ha create).
- **Riesecuzione autonoma di azioni irreversibili**: le azioni irreversibili
  mantengono la conferma umana anche in replay, anche per skill già fidate. Il
  replay salta il ragionamento del modello, quindi non deve saltare i freni di
  sicurezza.
- **Automation bias**: attenzione a non abituare l'utente ad approvare tutto
  meccanicamente; le conferme devono restare informative.

## Vincoli tecnici da tenere presenti
Il modello Gemma 3 1B ha context window ridotto on-device (~2048 token) ed è
sotto la soglia per pianificare task multi-step complessi in autonomia in modo
affidabile. Conseguenze pratiche:

- il 1B va usato per esecuzione step-by-step su alberi UI già compattati e per
  l'estrazione degli slot, non come pianificatore autonomo di task lunghi;
- per la fase di apprendimento iniziale di task difficili, valutare un modello
  locale più capace (es. Gemma 3n E2B/E4B via LiteRT-LM), tenendo il 1B per il
  replay veloce.
- **MediaPipe LLM Inference è in modalità maintenance**: pianificare la
  migrazione a LiteRT-LM come evoluzione del modulo brain.
- L'apprendimento affidabile funziona bene su task ripetitivi e strutturati;
  "fare ogni cosa" resta un'ambizione da calibrare — anche i modelli enormi
  plateau sui task multi-step aperti. Le skill eccellono dove il compito si
  ripete uguale.

## Sotto-fasi consigliate
- **3.0 — Fondamenta memoria**: schema SQLite delle skill + integrazione
  embedding/retrieval on-device + i tre tipi di memoria.
- **3.1 — Ciclo impara-verifica-salva (MVP)**: loop step-by-step con verifica di
  stato + compilazione skill con conferma umana degli slot.
- **3.2 — Replay robusto**: match + replay con verifica graduata, gestione
  dialog, fallback al modello, apprendimento dai fallimenti.
- **3.3 — Robustezza UI e scalabilità**: guard condition pre-replay, valutazione
  AppFunctions come binario prioritario, instradamento a modello più capace per
  l'apprendimento difficile.
- **3.4 — Sicurezza (in parallelo, non negoziabile)**: quarantena skill,
  conferma persistente su azioni irreversibili in replay, difesa avvelenamento
  memoria, audit log.

## Fuori scope della Fase 3
- pVM / isolamento in macchina virtuale protetta dei componenti AI: interessante
  per la sicurezza a lungo termine ma troppo pesante per ora.
- Interfaccia completamente app-less / agent-centric: visione futura, non
  obiettivo di questa fase.

## Riferimenti principali (da cui derivano i pattern)
SkillDroid, Voyager, AutoDroid/AutoDroid-V2, Mobile-Agent-E, AppAgent/AppAgentX,
LearnAct/LearnGUI, SUGILITE (programming-by-demonstration), Reflexion e
Generative Agents (memoria/riflessione), Magentic-UI (action guard
human-in-the-loop), Google AI Edge RAG SDK + EmbeddingGemma (retrieval
on-device).

---

## Stato di implementazione (aggiornato)
- **3.0 / 3.1 — fatte** (con conferma umana): schema SQLite (`skill`,
  `skill_step`, `pending_skill`, `episode`), `StateVerifier`, `SkillCompiler`
  (estrazione slot **deterministica** dai valori dimostrati invece che dal 1B —
  più affidabile e testabile), review con conferma umana, skill in quarantena.
- **Deviazione consapevole**: l'**embedding/retrieval on-device** (previsto in
  3.0) è **rimandato a 3.2**, dove serve davvero (per il match in replay). In
  3.0/3.1 non c'è ancora riuso, quindi il retrieval non era necessario.
- **3.2 / 3.3 / 3.4 (parti restanti) — da fare**: replay robusto, AppFunctions,
  scalabilità modello, e il completamento dei requisiti di sicurezza in replay.
