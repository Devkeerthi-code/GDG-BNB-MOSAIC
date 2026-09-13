# Prompt: Add AI-Agent Negotiation & Deal Reporting to Industrial Material Exchange (IME)

You are working inside the existing **Industrial Material Exchange (IME)** repository
(`https://github.com/hruthikeshb/industrial-material-exchange`), a Java 21 / Spring Boot 3.5.4 /
PostgreSQL / Spring Security+JWT / Thymeleaf B2B marketplace where organisations post `Supply` and
`Demand` items, get matched via `MatchingAlgorithmService`, and browse results on `/discover`.

Read the existing code before writing anything new. In particular, understand:

- `MatchingAlgorithmService` + `MatchResponse` — how matches and scores are produced.
- `DiscoveryController` + `templates/discover.html` — where the (currently fake) **"Connect"**
  button lives (`onclick="alert(...)"` — this is the button we are wiring up for real).
- `Organisation`, `Supply`, `Demand`, `Category`, `BaseEntity` (soft-delete pattern via
  `@SQLDelete`/`@SQLRestriction`, audited `createdAt`/`updatedAt`).
- `SecurityConfig`, `JwtAuthenticationFilter`, `CustomUserDetails`, `CustomUserDetailsService` —
  stateless JWT auth; `SecurityContextHolder.getContext().getAuthentication().getName()` returns the
  logged-in organisation's `contactNumber`, which is how existing controllers (`SupplyController`,
  `DashboardController`) resolve "current org".
- The package layout: `controller/`, `dto/`, `entity/` (+`entity/enums/`), `repository/`,
  `security/`, `service/`. **Follow this exact layout and existing code style** (constructor
  `@Autowired`, Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`, soft
  deletes on new entities that need them, DTOs for request/response shaping).

Do not restructure or rename existing files. Additive changes only, unless a small edit to
`discover.html` / `dashboard.html` is required to wire up the new flow.

---

## 1. Feature Goal

Today, clicking **Connect** on a match card in `/discover` just shows a browser `alert()` with the
counterpart's name and phone number. Replace that with a real workflow:

1. User finds a match on `/discover` (a `Supply` ↔ `Demand` pair, already scored by
   `MatchingAlgorithmService`).
2. User clicks **Connect** on the org they want to deal with.
3. This creates a **Negotiation** between the two organisations for that specific supply/demand
   pair.
4. Two **AI agents** — one representing the supplying org, one representing the demanding org —
   negotiate automatically over price, quantity, and terms, each trying to get the best outcome for
   *its own* organisation, using the real listing data (price/unit, quantity, distance, description)
   as grounding context.
5. The negotiation ends in either a **Deal Reached** (agreed price, quantity, terms) or **No Deal**
   (with a reason).
6. The agents jointly produce a **Deal Report** — written for both organisations, but **only the
   report belonging to the organisation currently authenticated in this browser session is ever
   rendered/returned**. The counterpart org sees their own copy of the report only when *they* are
   logged in.
7. The user reads the report and decides whether to proceed with the deal offline (finalizing an
   actual transaction is out of scope for this task — see "Explicitly out of scope" below).

Design this so it is **extensible**: a near-future requirement will add a third kind of
participant — a **quality/compliance verification organisation** that supplies product-quality
documents into the negotiation. Do not hardcode "exactly two agents" in a way that blocks adding a
third agent role later (see §7).

---

## 2. Data Model (new entities, `entity/` package)

Add these under `com.hruthikesh.ime.entity`, following the existing `Supply`/`Demand` conventions
(Lombok annotations, `extends BaseEntity` where soft-delete/audit makes sense, `@ManyToOne(fetch =
FetchType.LAZY)` for relations).

### `entity/enums/NegotiationStatus.java`
```java
public enum NegotiationStatus {
    PENDING,        // created, agent run not started yet
    IN_PROGRESS,    // agent turns are being generated
    DEAL_REACHED,
    NO_DEAL,
    FAILED          // technical failure (LLM error, timeout, etc.)
}
```

### `entity/enums/AgentRole.java`
```java
public enum AgentRole {
    SUPPLIER_AGENT,
    DEMANDER_AGENT
    // future: QUALITY_VERIFIER_AGENT (see §7) — add here, do not repurpose existing values
}
```

### `entity/Negotiation.java`
Extends `BaseEntity`. Fields:
- `id` (Long, identity)
- `supply` (`@ManyToOne` → `Supply`)
- `demand` (`@ManyToOne` → `Demand`)
- `supplierOrg` (`@ManyToOne` → `Organisation`) — convenience denormalization, equal to
  `supply.getOrganisation()`
- `demanderOrg` (`@ManyToOne` → `Organisation`) — equal to `demand.getOrganisation()`
- `initiatedByOrg` (`@ManyToOne` → `Organisation`) — whoever clicked Connect
- `status` (`NegotiationStatus`, default `PENDING`)
- `matchScoreAtStart` (Double, nullable) — carry over `MatchResponse.totalScore` for context/audit
- `maxRounds` (Integer, default e.g. 6) — hard cap so agents can't loop forever
- `currentRound` (Integer, default 0)
- `agreedPricePerUnit` (BigDecimal, nullable)
- `agreedQuantity` (BigDecimal, nullable)
- `agreedUnit` (Unit, nullable)
- `agreedTerms` (TEXT, nullable) — free-text summary of any non-price terms agreed
- `failureReason` (TEXT, nullable)
- `completedAt` (LocalDateTime, nullable)

Apply the same `@SQLDelete`/`@SQLRestriction` soft-delete pattern used on `Supply`/`Demand` if you
want negotiations to be cancel-able without hard deletion (recommended).

### `entity/NegotiationTurn.java`
One row per agent utterance/round. Fields:
- `id`
- `negotiation` (`@ManyToOne` → `Negotiation`)
- `roundNumber` (Integer)
- `agentRole` (`AgentRole`)
- `speakingForOrg` (`@ManyToOne` → `Organisation`)
- `message` (TEXT) — natural-language content shown in the transcript UI
- `proposedPricePerUnit` (BigDecimal, nullable)
- `proposedQuantity` (BigDecimal, nullable)
- `decision` (enum `CONTINUE | ACCEPT | REJECT`, stored as string) — the structured action this
  turn represents
- `createdAt` (LocalDateTime)

### `entity/NegotiationReport.java`
**One row per organisation** (two rows total per completed negotiation) so that report content can
legitimately be written from that org's point of view and access control is a trivial row filter —
do not try to build one shared report object with conditional rendering.
- `id`
- `negotiation` (`@ManyToOne` → `Negotiation`)
- `forOrg` (`@ManyToOne` → `Organisation`) — which org this copy is for
- `counterpartOrg` (`@ManyToOne` → `Organisation`) — denormalized for easy display
- `outcome` (`NegotiationStatus`, only `DEAL_REACHED`/`NO_DEAL` expected here)
- `summary` (TEXT) — plain-language narrative of how the negotiation went
- `recommendation` (TEXT) — what the agent recommends this org do next
- `dealTerms` (TEXT, nullable) — final agreed terms, if any, phrased for this org
- `generatedAt` (LocalDateTime)

Add corresponding repositories in `repository/`:
`NegotiationRepository`, `NegotiationTurnRepository`, `NegotiationReportRepository`
— Spring Data JPA interfaces, same style as `SupplyRepository`/`DemandRepository`. You will need at
least:
- `NegotiationRepository.findBySupplierOrg_IdOrDemanderOrg_Id(Long, Long)` (or a `@Query`) for "my
  negotiations" listing.
- `NegotiationTurnRepository.findByNegotiation_IdOrderByRoundNumberAsc(Long)`.
- `NegotiationReportRepository.findByNegotiation_IdAndForOrg_Id(Long, Long)`.

---

## 3. AI Agent Service (`service/` package)

### 3.1 `service/NegotiationAgentClient.java`
A thin client wrapping calls to the Claude API (Anthropic Messages API). Use `java.net.http.HttpClient`
(built into JDK 21 — no new dependency required) to `POST https://api.anthropic.com/v1/messages`.

- Add config in `application.properties`:
  ```properties
  anthropic.api.key=${ANTHROPIC_API_KEY}
  anthropic.model=claude-sonnet-4-6
  ```
  Bind via a small `@ConfigurationProperties` class or `@Value` injection — follow whatever
  lightweight pattern `JwtUtil` uses for its own config values (`jwt.secret`, `jwt.expirationMs`) so
  it's consistent with the rest of the codebase.
- Method: `AgentTurnResult generateTurn(NegotiationContext context)` where `NegotiationContext` is a
  small record capturing: item name/description/category, own org's role & price/qty target,
  counterpart's last message + proposed price/qty, round number, max rounds, distance, and full
  turn history (so the agent has memory — the Messages API is stateless per call, so you must resend
  prior turns as the conversation `messages` array).
- **Force structured output.** Instruct the model (system prompt) to return *only* JSON matching:
  ```json
  {
    "message": "string, the negotiation message in natural language",
    "proposedPricePerUnit": 123.45,
    "proposedQuantity": 100.0,
    "decision": "CONTINUE | ACCEPT | REJECT"
  }
  ```
  Parse defensively (strip markdown fences, catch `JsonProcessingException`, retry once, then mark
  the negotiation `FAILED` with `failureReason` set if parsing still fails).
- System prompts must differ per role:
  - **Supplier agent**: represents `supplierOrg`, wants price ≥ their listed `pricePerUnit`
    (ideally higher), wants to sell up to their listed `quantity`, should not agree to give away
    more than they have.
  - **Demander agent**: represents `demanderOrg`, wants price ≤ their listed `pricePerUnit` budget,
    wants to receive up to their listed `quantity`, should not agree to pay more than budget allows
    by a large margin.
  - Both prompts should instruct the agent to be reasonable, converge within `maxRounds`, and choose
    `ACCEPT` once the two proposals are within a small tolerance (e.g. price within 5% and quantity
    within the smaller listing's amount), or `REJECT` if it becomes clear no agreement is possible
    (e.g. hard price floor/ceiling mismatch, or `maxRounds` reached with a wide gap).

### 3.2 `service/NegotiationService.java`
Orchestrates the full lifecycle. Public methods roughly:

- `Negotiation startNegotiation(Long supplyId, Long demandId, Organisation initiator)`
  - Validates: supply/demand exist and are `ACTIVE`; `initiator` must be the owning org of *either*
    the supply or the demand (you can't start a negotiation on someone else's listing on your
    behalf) — mirror the ownership check pattern used in
    `SupplyController.deleteSupply`/`SupplyService`.
  - Validates: no other `IN_PROGRESS`/`PENDING` negotiation already exists for this exact
    supply+demand pair (prevent duplicate spam-clicking Connect).
  - Creates the `Negotiation` row (`status = PENDING`), persists, returns it.

- `void runNegotiationAsync(Long negotiationId)` — annotate `@Async` (enable `@EnableAsync` on the
  main application class or a config class; Java 21 virtual threads make this cheap — you may
  configure the async executor to use `Executors.newVirtualThreadPerTaskExecutor()`). This method:
  1. Sets status `IN_PROGRESS`.
  2. Loops rounds 1..`maxRounds`, alternating supplier/demander turns, calling
     `NegotiationAgentClient.generateTurn(...)` each time, persisting each as a `NegotiationTurn`.
  3. Stops early on `ACCEPT` (record `agreedPricePerUnit`/`agreedQuantity`/`agreedTerms`, status
     `DEAL_REACHED`) or `REJECT` (status `NO_DEAL`, set `failureReason`).
  4. If `maxRounds` exhausted without convergence, status `NO_DEAL`, `failureReason = "No agreement
     within round limit"`.
  5. Sets `completedAt`.
  6. Calls `generateReports(negotiation)`.

- `void generateReports(Negotiation negotiation)` — one more LLM call (or two, one per org, with a
  perspective-specific system prompt) that takes the full turn transcript + final outcome and
  produces the `summary`/`recommendation`/`dealTerms` text for **each** org, persisting two
  `NegotiationReport` rows (`forOrg = supplierOrg` and `forOrg = demanderOrg`).

- Query helpers for the controller: `getNegotiation(id, requestingOrgId)` (throws/403s if
  `requestingOrgId` is not `supplierOrg` or `demanderOrg` on that negotiation — **every negotiation
  and report lookup must be scoped to the authenticated org**, same trust boundary as
  `SupplyService.deleteSupply` scoping deletes to the owning org), `getTurns(negotiationId)`,
  `getReportForOrg(negotiationId, orgId)`, `listMyNegotiations(orgId)`.

---

## 4. DTOs (`dto/` package)

- `NegotiationStartRequest` — `{ Long supplyId; Long demandId; }` (or reuse the existing
  `matchId`/route pattern from `DiscoveryController` if that's cleaner — the important part is the
  Connect button on `discover.html` must POST supply+demand identifiers, not just org name/contact
  like it does today).
- `NegotiationTurnDto` — flattened view of `NegotiationTurn` for JSON polling (id, roundNumber,
  agentRole, speakingForOrgName, message, proposedPricePerUnit, proposedQuantity, decision,
  createdAt).
- `NegotiationStatusResponse` — `{ status; currentRound; maxRounds; List<NegotiationTurnDto> turns;
  boolean reportAvailable; }` for AJAX polling.
- `NegotiationReportDto` — flattened view of `NegotiationReport` for the report page.

---

## 5. Controller & Routes (`controller/NegotiationController.java`)

Follow `DiscoveryController`/`SupplyController` conventions (constructor injection, resolve current
org the same way: `SecurityContextHolder.getContext().getAuthentication().getName()` →
`organisationRepository.findByContactNumber(...)`).

- `POST /negotiations/start` — body/params `supplyId`, `demandId`. Calls
  `negotiationService.startNegotiation(...)`, then `negotiationService.runNegotiationAsync(...)`,
  then redirects (or returns JSON `{negotiationId}` if called via `fetch`) to
  `/negotiations/{id}`.
- `GET /negotiations/{id}` — Thymeleaf page (`negotiation.html`, new template) showing: item
  summary, both orgs, a transcript panel (rendered turns so far), a status badge, and — once
  completed — a link/section for **"View my report"**. Enforce the org-membership check here
  (return 403/redirect to `/dashboard` with an error flash if the logged-in org is not a party to
  this negotiation).
- `GET /negotiations/{id}/status` — `@ResponseBody` JSON endpoint returning
  `NegotiationStatusResponse`, polled by the frontend (e.g. every 1.5–2s via `setInterval`/`fetch`)
  while `status` is `PENDING`/`IN_PROGRESS`, so the transcript fills in turn-by-turn like a chat.
  Stop polling client-side once status is terminal.
- `GET /negotiations/{id}/report` — Thymeleaf page (`negotiation-report.html`, new template).
  Resolves the current org, fetches **only** `negotiationService.getReportForOrg(id,
  currentOrg.getId())`, and renders it. **Never** pass both orgs' reports into the model, and never
  expose an org-selecting parameter here — the report shown must always be implicitly determined by
  who's logged in, not by a request parameter, so one org can never view the other's report by
  editing a URL.
- `GET /negotiations` — "My Negotiations" list page, useful from the dashboard.

### `discover.html` changes
Replace the current Connect button:
```html
<button class="btn button-secondary button-sm" ... onclick="event.stopPropagation(); alert(...)">Connect</button>
```
with a real call, e.g.:
```html
<button class="btn button-secondary button-sm"
        th:data-supply-id="${sourceType == 'Supply' ? sourceItem.id : match.id}"
        th:data-demand-id="${sourceType == 'Demand' ? sourceItem.id : match.id}"
        onclick="event.stopPropagation(); startNegotiation(this)">Connect</button>
```
with a small JS helper (`app.js` or inline) doing
`fetch('/negotiations/start', {method:'POST', ...}).then(r => window.location = '/negotiations/' + id)`.
Match the existing CSS variable / class conventions already used in `discover.css`/`ime.css` — do
not introduce a new design system.

### `dashboard.html` change
Add a "My Negotiations" link/section (small addition) pointing at `GET /negotiations`, consistent
with the existing supplies/demands cards on that page.

### `SecurityConfig`
No change needed to the permit-list — `/negotiations/**` should require authentication like
everything else (it will fall under `.anyRequest().authenticated()`).

---

## 6. Report Visibility Rule (must-follow)

- A negotiation always produces **two** `NegotiationReport` rows (one `forOrg = supplierOrg`, one
  `forOrg = demanderOrg`), generated in the same backend pass so both are consistent with the same
  transcript/outcome.
- The `/negotiations/{id}/report` endpoint **only ever queries and renders the row matching the
  currently authenticated organisation**. The other org's report is not fetched, not serialized into
  the page/model, and not reachable via any parameter on this endpoint.
- This is intentionally the same trust model already used for supplies/demands ownership checks
  elsewhere in the codebase — reuse that pattern, don't invent a new one.

---

## 7. Forward-Compatibility: Future Quality-Document Organisations

A near-future iteration will introduce a third participant type: an organisation that supplies
product-quality/compliance documents into a negotiation. Design now (without building it yet) so
that addition doesn't require reworking the core model:

- Keep `AgentRole` as an enum you can extend (`QUALITY_VERIFIER_AGENT`) rather than a boolean
  `isSupplier` flag anywhere.
- Keep `NegotiationTurn.speakingForOrg` generic (`Organisation`, not `supplierOrg`/`demanderOrg`
  specific columns) so a third org's turns fit the same table without a schema change.
- Keep `NegotiationAgentClient.generateTurn(NegotiationContext)` role-agnostic — the role-specific
  behavior should live entirely in the system prompt selected by `AgentRole`, not in branching
  Java code that assumes exactly two participants.
- Don't hardcode "exactly 2 reports" anywhere outside `generateReports` — that method can later
  loop over "all parties to this negotiation" instead of hardcoding
  supplier+demander.

You do not need to build document upload/verification in this task — just avoid decisions that
would block it later.

---

## 8. Explicitly Out of Scope (for this task)

- Actually executing/settling a real-world transaction, payments, or contracts.
- Real-time chat between humans (this is agent-to-agent only, with the humans reading transcripts
  and reports after the fact).
- The future quality-document organisation itself — only keep the model extensible per §7.
- Notifications/email to the counterpart org when a negotiation starts (nice-to-have, not required).

---

## 9. Suggested Build Order

1. Entities + enums + repositories (§2). Let `spring.jpa.hibernate.ddl-auto=update` create the
   tables — consistent with how this project already manages schema.
2. `NegotiationAgentClient` with a **hardcoded/mocked** `generateTurn` first (return canned
   JSON) so the rest of the pipeline can be built and tested without burning API calls or needing
   the key configured yet.
3. `NegotiationService.startNegotiation` + `runNegotiationAsync` using the mock client; verify the
   full round loop, persistence, and status transitions work end-to-end.
4. `NegotiationController` + `negotiation.html` + polling JS; wire up the real Connect button on
   `discover.html`.
5. `generateReports` + `negotiation-report.html`, enforcing the per-org visibility rule (§6).
6. Swap the mock `NegotiationAgentClient` for the real Anthropic Messages API call; add
   `anthropic.api.key`/`anthropic.model` to `application.properties` (document them in README the
   same way `jwt.secret` is documented).
7. Add tests under `src/test` mirroring existing test conventions: unit tests for
   `NegotiationService` round logic (mock `NegotiationAgentClient`), and a controller test asserting
   an org cannot fetch another org's report or a negotiation it's not party to.
8. Update `README.md`'s **User Workflow** and **Project Architecture** sections to describe the new
   Connect → Negotiate → Report flow, and add the new `anthropic.api.key` env var to the
   Configuration section.
