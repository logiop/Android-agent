# Valutazione — AppFunctions (Jetpack) per Android Agent

Contesto: il PROJECT_PLAN della Fase 3 indica **AppFunctions** come possibile
"binario prioritario" nell'ordine di precedenza dell'agente (Fase 3.3), perché
invocare una funzione esposta da un'app è più affidabile di qualsiasi sequenza
di tap ricostruita dall'accessibilità. Questa è una valutazione di fattibilità e
una raccomandazione — **non** un'implementazione.

> ⚠️ AppFunctions è un'area **molto recente e in rapida evoluzione**; API,
> requisiti di versione e disponibilità possono essere cambiati rispetto a
> quanto qui riportato. Trattare i dettagli come indicativi e ri-verificarli
> sulla documentazione ufficiale prima di implementare.

## Cos'è
AppFunctions è l'iniziativa Jetpack/Android per far sì che un'app **dichiari**
funzioni invocabili (via annotazioni tipo `@AppFunction` + uno schema tipizzato)
che un agente/assistente può **chiamare direttamente**, passando parametri
strutturati, invece di navigare la UI. Concettualmente è il cugino on-device
dell'App Actions / degli App Shortcuts, ma pensato per gli agenti LLM.

## Perché sarebbe prioritario
- **Affidabilità**: niente locator fragili né tap che si rompono quando la UI
  cambia — si chiama una funzione con parametri tipizzati.
- **Sicurezza**: superficie più controllabile di un AccessibilityService che
  "vede tutto"; l'app dichiara cosa espone.
- **Velocità**: nessun ciclo osserva-agisci-rileggi.

## I limiti che lo rendono NON adottabile ora
1. **Richiede che l'app target implementi AppFunctions.** È opt-in dello
   sviluppatore dell'app: l'agente può chiamare solo funzioni che l'app ha
   scelto di esporre. L'adozione reale, ad oggi, è minima.
2. **Requisiti di piattaforma stringenti**: versioni Android recenti + supporto
   di sistema (AICore/Play Services lato on-device). Il parco di device che lo
   supporta è ristretto; sul target (Galaxy S26 Ultra) va verificato caso per
   caso.
3. **Maturità API**: è in fase iniziale (alpha); firme e comportamenti possono
   cambiare. Legarci ora significa manutenzione continua.
4. **Copertura parziale**: anche dove disponibile, coprirà poche funzioni di
   poche app — non sostituisce l'agente accessibility, lo **integra**.

## Come si inserirebbe nell'architettura (quando maturo)
Nell'ordine di precedenza dei comandi (`OverlayService.handleCommand` +
`AgentLoop`), AppFunctions andrebbe **tra la scorciatoia deterministica e il
replay di skill**:

1. Scorciatoia deterministica (`open_app`/`search`) — invariato.
2. **AppFunctions** (nuovo): se l'app target espone la funzione richiesta →
   invocala. Da mediare con un capability-probe a runtime.
3. Replay di skill appresa.
4. Apprendimento da zero col modello.

Un `AppFunctionRouter` isolato (dietro un'interfaccia) manterrebbe il resto
dell'agente indipendente dalla disponibilità dell'API.

## Rischi specifici per un agente che apprende (Fase 3)
- Chiamare una funzione che compie un'azione **irreversibile** deve comunque
  passare dal gate di conferma umana (come per tap/replay): l'affidabilità
  tecnica non elimina il bisogno del freno.
- La `targetApp` e i parametri passati vanno validati (una funzione esposta è
  comunque input verso un'app di terze parti).

## Raccomandazione
**Non implementare ora. Preparare, non costruire.**

1. **Astrazione già pronta**: quando aggiungeremo il routing, definire
   `ActionRoute { DETERMINISTIC, APP_FUNCTION, SKILL_REPLAY, LLM_LEARN }` così il
   binario AppFunctions si innesta senza rifattorizzare.
2. **Capability probe**: prima di tentarlo, verificare a runtime la
   disponibilità dell'API e del supporto di sistema; assente → saltare
   silenziosamente al binario successivo.
3. **Sicurezza invariata**: le azioni irreversibili via AppFunctions mantengono
   la conferma umana; validare `targetApp` e parametri.
4. **Trigger di rivalutazione**: riconsiderare l'implementazione quando (a)
   l'API esce dall'alpha, (b) almeno alcune app d'uso comune sul device target
   la espongono, (c) il capability probe è affidabile.

## Nel frattempo
Il valore maggiore resta sui binari già presenti: scorciatoie deterministiche +
skill apprese (replay) + apprendimento col modello. AppFunctions è un
potenziamento futuro ad alto valore ma a bassa disponibilità: lo teniamo nel
mirino, non nel percorso critico.
