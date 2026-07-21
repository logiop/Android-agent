# Android Agent — Piano progetto (Fase 1)

## Obiettivo
Overlay AI sempre visibile sullo schermo Android, attivabile a comando vocale,
capace di controllare le app del telefono (navigare, aprire email, usare i
social) tramite AccessibilityService. Fase 1 = costo zero per l'operatore,
modello LLM solo locale, nessuna chiamata a API a pagamento.

## Vincoli di progetto
- Costo zero: nessuna API cloud in questa fase.
- Sviluppo interamente da telefono, senza PC. Ogni step di lavoro termina con
  una build APK scaricabile da installare e testare sul device.
- Target reale: Samsung Galaxy S26 Ultra.
- Sicurezza non negoziabile (vedi sezione dedicata) perché l'app userà i
  permessi più potenti di Android (Accessibility + overlay).

## Architettura moduli
1. **overlay** — bolla flottante trascinabile (SYSTEM_ALERT_WINDOW), sempre
   visibile, attivabile/disattivabile.
2. **voice** — SpeechRecognizer on-device, lingua italiana.
3. **brain** — inferenza LLM locale via MediaPipe LLM Inference (Google), il
   motore on-device scelto per la Fase 1. MediaPipe non usa il formato GGUF,
   quindi il Qwen GGUF del piano originale non è compatibile con questo motore:
   il modello di default è **Gemma 3 (1B/2B) Instruct in formato `.task`,
   quantizzato int4**, buon compromesso latenza/RAM sul Galaxy S26 Ultra. Il
   modello si carica solo quando l'overlay è attivo e si libera dalla memoria
   quando inattivo. Il file `.task` non è incluso nell'APK: si importa sul
   device (Storage Access Framework) e resta in storage privato dell'app.
4. **hands** — AccessibilityService: estrae l'albero UI corrente in XML
   compatto (solo elementi interattivi, testo troncato, max ~30 nodi ) ed
   esegue le azioni (tap, type, scroll, open_app).

## Loop agente
comando vocale + UI tree → LLM risponde in JSON rigido
`{action, target, text}` → l'azione viene eseguita → nuovo UI tree → si
ripete, massimo 15 step. Se dopo 3 step non c'è progresso, l'agente si ferma
e lo comunica a voce invece di insistere.

**Scorciatoie deterministiche** (non passano dal modello): "apri [app]" usa
direttamente PackageManager; "cerca [X] su Google" costruisce l'intent con
URL diretto. Il modello serve solo a classificare il comando e a riempire i
parametri quando serve reale comprensione del linguaggio.

## Sicurezza (requisiti obbligatori, non derogabili)
- Nessuna credenziale o config sensibile in chiaro: uso di Android Keystore.
- Sblocco biometrico richiesto per attivare l'overlay.
- Whitelist esplicita delle app in cui l'agente può operare; tutto il resto
  bloccato di default.
- Conferma manuale obbligatoria (dialog nativo) prima di qualsiasi azione
  irreversibile: invio messaggi/email, pubblicazione su social,
  eliminazione dati, pagamenti.
- Il testo letto dallo schermo va marcato nel prompt come dato non fidato;
  il system prompt istruisce esplicitamente il modello a ignorare eventuali
  istruzioni contenute in quel testo (difesa da prompt injection).
- Log locale cifrato di ogni azione eseguita dall'agente.

## Fuori scope per la Fase 1
- Qualsiasi chiamata API cloud (DeepSeek o altri) — è Fase 2, non va
  implementata né predisposta ora oltre a lasciare il modulo brain
  facilmente estendibile in futuro.
- Nessun layer di routing ibrido locale/cloud.

## Criteri di completamento Fase 1
- Overlay attivabile/disattivabile con sblocco biometrico funzionante.
- Comando vocale di riferimento eseguito correttamente:
  "apri Chrome e cerca meteo Genova".
- Modello locale carica e risponde con latenza accettabile sul Galaxy S26
  Ultra (misurare token/secondo).
- Whitelist e conferma manuale verificate: un tentativo di azione
  irreversibile su un'app non whitelisted o senza conferma viene bloccato
  correttamente.

## Note per l'esecuzione
- Lavoro interamente da telefono: ogni fase di sviluppo deve concludersi
  con build + link APK scaricabile per l'installazione e il test manuale.
- Procedere per step in ordine logico; chiedere conferma solo quando un
  passaggio è realmente ambiguo o bloccante, altrimenti proseguire in
  autonomia secondo questo piano.
