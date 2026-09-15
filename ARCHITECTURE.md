# UIM Atlas Architecture

## Architectural goal

Build a small generic Java engine that can grow to broad UIM game coverage without growing Java source in proportion to game knowledge.

The architecture should make it cheap to add a new method, milestone, storage rule, quest dependency, or item lifecycle rule without adding a bespoke Java class.

## High-level shape

```text
RuneLite events / APIs
        |
        v
+-----------------------+
| Account State Layer   |
+-----------------------+
        |
        v
+-----------------------+      +------------------------+
| Capability / Storage  |<---->| Optional Integrations  |
+-----------------------+      +------------------------+
        |
        v
+-----------------------+
| Data Repositories     |
+-----------------------+
        |
        v
+-----------------------+
| Candidate Generation  |
+-----------------------+
        |
        v
+-----------------------+
| Safety / Validation   |
+-----------------------+
        |
        v
+-----------------------+
| Transition Planning   |
+-----------------------+
        |
        v
+-----------------------+
| Recommendation Score  |
+-----------------------+
        |
        v
+-----------------------+
| Explanation / UI      |
+-----------------------+
```

## Core modules

### Implemented recommendation-domain foundation

`data/MethodDefinitionLoader` validates versioned JSON catalogs into immutable `recommendation/MethodDefinition` values through separate synthetic-v1 and production-v3/v4/v5 entry points. Production requires source/review metadata and a caller-supplied canonical item-ID boundary; optional setup stays metadata and cannot affect eligibility. Schema v4 adds explicit bounded resource-flow slot semantics, while v5 adds reusable `ANY_OF` preparation groups and input-only resource sinks. Older production records retain their exact contracts. `Requirement` evaluates a numeric fact with its existing `state/Observation` provenance and explicit freshness policy. `MethodEvaluator` separates hard blockers, missing preparation, unknown requirements and alternative-group diagnostics. `MethodScorer` accepts explicit normalized inputs and returns a weighted breakdown only for available/preparable methods. Required unknown state cannot be offset by a high score.

Three fixture methods reside exclusively in test resources. Forty-one reviewed methods across fourteen catalogs, plus one reviewed Recipe for Disaster goal graph, ship in main resources. `ProductionMethodCatalog` and `ProductionGoalCatalog` read bundled indexes, delegate strict per-file parsing and reject cross-catalog ID collisions. The live plugin loads both generic catalogs once through `PlanningService`; neither catalog contains a skill registry or hard-coded selected method list. The v2 coverage pack closes all thirteen encoded RFD skill-family gaps without skill-specific orchestration; see [coverage and observation limits](docs/rfd-skill-coverage-v2.md). Game knowledge, exact item/quest IDs, recipes, tool alternatives and goal prerequisites remain in resources; Java only provides generic observation, planning and presentation behavior. Artifact tests enforce production/test separation. See the implemented v1/v3/v4/v5 method and v1 goal sections of `DATA_SCHEMA.md` for schema and scoring contracts.

`state/AccountStateFacts` projects one normalized snapshot into the generic `recommendation/FactLookup` interface. The dependency direction is `RuneLite Client -> RuneLiteAccountObserver -> AccountState -> AccountStateFacts -> FactLookup -> goal/evaluation/resource flow/scoring/readiness`. The evaluators know neither account layout nor client APIs. Skill, quest, Quest-point, capability, slot and supported container facts preserve their observations; arbitrary carried item quantities and directly observed exact-item position counts are resolved on request without an item registry or mutable cache.

The adapter accepts an explicit observation cutoff (normally the evaluation time) and never refreshes source timestamps. Direct facts retain source, confidence and time. Carried quantities require both inventory and equipment, retain both source labels, take the older timestamp and use the weaker confidence. Future-dated inputs are unknown at the cutoff so combining timestamps cannot conceal them. Evaluation must occur at the cutoff or later and continues to enforce each requirement's age/confidence policy. Unknown state stays explicit. See [account fact IDs and semantics](DATA_SCHEMA.md#account-state-fact-contract) for the stable namespaces and limits. `PlanningService` creates this bridge for each live recalculation.

`AccountState` includes one small generic `Observation<Map<String, Boolean>>` for stable capability fact IDs. The first provider is `RuneLiteAccountObserver`, which reads the public server-value API `Client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION)` on logged-in game ticks. The generated named constant is present in both the pinned RuneLite 1.12.38 API and current upstream source. RuneLite documents the server-value read behavior, but it does not document an ownership enum or guarantee that zero means verified non-ownership. UIM Atlas therefore treats a positive house-location value as `capability.poh.owned = true`; zero, negative values and read failures remain UNKNOWN. This is a conservative inference from a server-supplied positive house location, not a location lookup or a claim about house contents. See the [pinned named varbit](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java) and [pinned Client API](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/Client.java).

Capability observations use the existing source, confidence and timestamp model. They are overwritten on every logged-in refresh rather than persisted as `LAST_OBSERVED`. Logout, loading, hopping, connection loss, profile changes, disable/re-enable and account-hash changes clear the whole snapshot before another account can contribute state. The transient account hash remains only an in-memory lifecycle boundary; no name or login identifier enters the snapshot. Capabilities deliberately do not participate in the current shell's `isLoaded` flag because this provider cannot verify non-ownership; recommendation requirements enforce their own freshness. No evaluator, scorer, Construction-specific rule or UI component reads RuneLite directly.

`AccountState` also has one generic map of `ContainerState` values. Ownership, typed contents and free capacity are separate `Observation` values because one may be known while another is not. The first provider recognizes a currently carried plank sack from the exact inventory item and reads RuneLite 1.12.38's named server varbits `PLANK_SACK_PLAIN`, `PLANK_SACK_OAK`, `PLANK_SACK_TEAK`, `PLANK_SACK_MAHOGANY`, `PLANK_SACK_CAMPHOR`, `PLANK_SACK_IRONWOOD` and `PLANK_SACK_ROSEWOOD`. It publishes typed contents and derived free capacity only when all seven reads are valid, each count is in [0,28], and their sum is at most the sourced shared capacity of 28. Absence from inventory proves only that the sack is not currently carried, so `container.plank_sack.owned` remains UNKNOWN rather than false. Failed or invalid reads preserve verified current ownership when present but leave contents and capacity UNKNOWN. Inventory and relevant varbit events coalesce these reads at the next game tick; every existing session/account reset clears them. Sources: [RuneLite 1.12.38 VarbitID](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java), [RuneLite Client server-varbit API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Client.html#getServerVarbitValue(int)), and the [official Construction Contracts poll](https://oldschool.runescape.com/polls/2020/1606).

`recommendation/MethodRanker` now orchestrates `FactLookup -> MethodEvaluator -> MethodScorer -> RankingResult` over caller-supplied candidates (a method definition plus explicit normalized scoring inputs). It has no mutable ranking state and uses one supplied evaluation instant. Immutable results retain each candidate's inputs, full evaluation diagnostics and optional score breakdown. Only `AVAILABLE` and `NEEDS_PREP` enter the ranked list; the optional best and ordered alternatives come exclusively from that list. `BLOCKED` and `UNKNOWN` remain unscored in a separate diagnostic list, preserving their original status and all unresolved requirements.

Eligible ordering is descending total score, then ascending method ID using Java `String` natural order. There is no status bonus, approximate tie threshold or minimum winning score. Excluded diagnostics are also ordered by ID, and duplicate candidate IDs are errors. This makes both lists independent of input order without a switching policy. The ranker reuses the existing scorer's eligibility and weights. Callers must provide a coherent immutable fact snapshot and scoring inputs derived for that snapshot. Live Planner v1 supplies that snapshot composition; focus/hysteresis remains future work.

`recommendation/SetupScoringInputs` derives four setup factors from existing method predicates/cost metadata and the observed carried setup exposed by `FactLookup`. It reuses `Requirement.evaluate` for observation trust, returns an immutable all-or-unresolved result with original observations, and composes with five explicitly supplied non-setup factors into the existing scorer input map. Required unknown setup cannot be exchanged for an optional uncertainty penalty. This remains a domain-only friction estimate, not a transition action planner. See [the formula and limitations](DATA_SCHEMA.md#account-specific-setup-scoring).

### Capacity and conditional efficiency

**Hard feasibility != working capacity != efficient capacity.**

The existing hard gates and preparation predicates define whether the scoped start is runnable. Genuine capacity constraints remain in those groups; neither working assumptions nor profiles can hide their shortfalls. `AVAILABLE` only proves encoded starting checks, not a complete contract, funded session or feasible replenishment plan.

`workingCapacity` describes a useful ordinary batch and returns independent diagnostics, not gates. Smaller batches may remain possible, and a verified helper profile may establish a different usable payload without satisfying the ordinary working target. Both Mahogany Homes records use a 14-position working assumption; their minimum starting-stock check does not require that batch. Limestone's sourced 20-position collection loop stays real preparation. Generic `inventory.item.<id>.usable_slots` counts ordinary free slots plus directly observed positions occupied by that exact item, preserving inventory provenance. It counts slot entries, never quantities: one large stack contributes one position. Production data must opt into this exact meaning with `capacitySemantics: FREE_PLUS_OBSERVED_EXACT_ITEM_SLOTS`. No item registry, stackability inference or disposal planning is added.

`MethodEfficiency` is one small domain derivation alongside `SetupScoringInputs`, reusing `Requirement.evaluate` and `MethodEvaluator`. It assesses data-defined profiles and emits an optional `METHOD_EFFICIENCY` input for the existing `MethodScorer`. It is not another scorer. Selection requires an `AVAILABLE` method and all profile assumptions verified now within their age limits; unsupported, stale, historical, future or invalid inputs retain UNKNOWN diagnostics. A lower verified profile can win while higher profiles remain unresolved. Failure to match efficiency assumptions never changes method status. Working assumptions and profile matches preserve predicates, source observations and results, including unresolved facts after known failures.

Profiles have unique explicit priorities; highest applicable priority wins. Overlapping profiles with different priorities are deliberate; equal priorities and duplicate IDs are rejected by the loader. Fact checks are ordered by ID, so JSON/map order cannot change selection. No selected profile means unresolved efficiency, never a neutral or favorable fallback. Raw production XP ranges move into optional profiles, and trusted XP is exposed only from a selected profile with a range. A helper-only payload profile intentionally has no XP estimate. Benchmark setup capabilities remain unsupported where current observations cannot establish the source assumptions.

Composition is explicit: pass four non-setup/non-efficiency factors into the efficiency result, then pass its optional five-factor map into the setup result, then the complete map to `MethodScorer` / `MethodRanker`. Existing explicit efficiency inputs cannot be overwritten. Callers retain both derivation results for explanations. Setup, transition and disruption factors, scorer weights, ranking rules and synthetic v1 behavior are unchanged. No observer or plugin/UI call site is added.

### Bounded resource flows

Production schema v4 attaches one optional generic `ResourceFlow` to a method. It describes one bounded consume-then-produce batch using the existing `consumes` and `produces` records. Schema v5 also permits an input-only resource sink, such as lighting one carried log, so the released position is calculated without inventing a retained output. Output-only flows remain invalid. Each entry must explicitly declare `ONE_SLOT_PER_UNIT`, `ONE_SHARED_STACK`, or `NO_INVENTORY_SLOT`. The last value is restricted to a supported external quantity scope such as a generic container; it does not claim that an external resource occupies or releases an ordinary inventory position.

The flow calculator requests exact quantity, exact occupied-position and free-slot observations from the same `FactLookup`. It never derives positions from quantity alone. `ONE_SLOT_PER_UNIT` is trusted only when observed quantity equals observed positions. `ONE_SHARED_STACK` is trusted only when zero quantity has zero positions or a positive quantity has exactly one observed position. Any contradiction, missing observation, stale value, future value or disallowed historical value makes the flow UNKNOWN. This is a small data-declared boundary rather than a Java stackability registry.

For a trusted batch, input positions become reusable before declared outputs are placed. A per-unit output needs one position per unit. A shared-stack output needs no new position only when a compatible exact output stack is directly observed; otherwise it needs one. The result retains all source observations, input positions released, output positions required, resulting free positions or the exact additional free-position deficit. It does not mutate inventory, acquire inputs, dispose of items, unnote supplies, combine doses or model multi-step routes.

`RecommendationDecision` retains the resource-flow analysis beside evaluation, setup, efficiency, score and preparation. Missing flow inputs remain evaluator preparation deficits. UNKNOWN flow facts become unresolved preparation. A known output-position shortfall becomes an exact capacity deficit, but remains unresolved without a safe slot-creation provider. Profile selection and raw score cannot hide it. Schema-v3 methods have no flow analysis and preserve their existing Construction behavior.

Schema v5 requirement groups are reusable catalog-local preparation alternatives. Each group contains at least two named alternatives; each alternative remains a conjunction of ordinary validated requirements. A group is satisfied when one complete alternative is verified, missing when every alternative is known invalid, and unknown when no alternative is verified but at least one remains unresolved. Groups, alternatives and their leaf checks are evaluated in stable ID order. Pickaxe and axe identities and their use levels therefore stay in resources; production Java contains no knowledge of either tool family.

`PreparationFeasibility` now classifies evaluator and working-capacity diagnostics as `READY`, `FEASIBLE_PREP`, `UNRESOLVED_PREP` or `BLOCKED`. It retains exact observed shortfalls and unresolved predicates. A known deficit is not a plan: it becomes feasible only when an explicit preparation provider declares support for that exact fact. The milestone supplies no live providers, so freeing slots, acquiring items and satisfying unknown capabilities remain unresolved by default.

`MethodActionability` applies the independent gate: `AVAILABLE + READY` is `ACTIONABLE`; `NEEDS_PREP + FEASIBLE_PREP` is `ACTIONABLE_WITH_PREP`; every unresolved, blocked or unknown combination is `NOT_ACTIONABLE`. `RecommendationDecision` composes `AccountStateFacts`, evaluation, setup derivation, efficiency, the unchanged scorer/ranker, preparation and actionability. It retains all candidate diagnostics, orders scored candidates by score then method ID, orders unscored candidates by ID, and chooses the first scored actionable candidate. A higher-scoring unresolved candidate stays visible but cannot displace a lower-scoring ready candidate. An empty best result means no actionable recommendation yet. `PlanningService` now consumes this result without duplicating its evaluation logic.

### Goal context

`data/GoalDefinitionLoader` is a dedicated strict parser for the separate goal resource family. It accepts caller-supplied canonical RuneLite quest IDs and the current maximum Quest-point boundary, validates source metadata and requirement ranges, resolves milestone dependencies, rejects cycles, and emits milestones in deterministic topological order with stable IDs breaking ties. It does not add parsing responsibility to `MethodDefinitionLoader`. Recipe for Disaster is data; no production Java class names the goal, its chapters or prerequisites.

`GoalEvaluator` applies the existing `Requirement` freshness rules to a `GoalDefinition` and `FactLookup`. Its immutable `GoalState` retains every satisfied, missing and unknown check with the original observation, milestone status, deterministic current frontier, and completed/total milestone counts. It does not fabricate a percentage. Verified final completion produces `COMPLETE`; otherwise any required unknown produces `UNKNOWN`, while fully observed incomplete state produces `IN_PROGRESS`.

`GoalContext` derives `GOAL_PROGRESS` as a binary, reviewable signal: `1.0` only when a method's normalized `activity` names a skill with a verified unmet goal target and the account currently satisfies that method's own gate for the same skill; otherwise `0.0`. Unknown or satisfied targets never award progress. Candidate generation passes only positive-relevance methods into the existing `RecommendationDecision`. Every unmet or unknown requirement without a currently supported matching method remains a structured coverage gap, so unrelated methods cannot substitute for missing quest, skill, capability or Quest-point coverage.

Storage value, risk and uncertainty remain explicit caller inputs. Goal context supplies only `GOAL_PROGRESS`; setup, efficiency, preparation and actionability remain independently derived by their existing components. Thus a relevant method with unresolved preparation cannot displace a ready relevant method. A complete goal or a goal with no covered unmet requirements returns no goal-driven actionable recommendation. Live Planner v1 supplies its documented conservative production inputs through `PlanningService`.

Quest states use the pinned RuneLite `Quest.getState(Client)` semantic API, including RFD chapter and miniquest enum entries. `AccountStateFacts` projects known states as `quest.<id>.complete` and `quest.<id>.started`; UNKNOWN entries are omitted rather than converted to zero. Both inherit the quest snapshot's `LAST_OBSERVED` confidence and timestamp. Quest points use `Client.getVarpValue(VarPlayerID.QP)`, matching RuneLite's built-in achievement-diary requirement, and are captured with the quest refresh as `LAST_OBSERVED`. Login, logout, hop, profile and account-hash reset behavior is inherited from the session-only snapshot, with no account identifier persisted.

### Strategic actions

`StrategicDecision` composes one already-evaluated `GoalContext.Result`, including its original `RecommendationDecision.Result`. `StrategicAction` exposes only ID, kind and readiness. A method wrapper retains the full method result and matched goal requirements. `QuestAction` retains the evaluated milestone, dependency states, prerequisite diagnostics and a manual handoff descriptor. There is no parallel quest evaluator, inheritance rewrite or RuneLite dependency. `PlanningService` is its single live call site.

Only unfinished quest-completion milestones in the selected goal graph enter quest consideration. The evaluator's `AVAILABLE` frontier supplies ready handoffs. Pending stages outside that frontier remain blocked/unknown diagnostics with their dependency states; an arbitrary quest prerequisite or other coverage gap is not turned into a new quest action. Complete milestones and complete goals produce no quest candidates. A capability-completion milestone cannot masquerade as a quest.

Quest `READY_TO_HANDOFF` means the milestone's strategic prerequisites and dependencies are verified under their existing freshness policies. It means strategically appropriate to begin, not fully equipped to finish. Unknown own completion, prerequisite quests, Quest points, skills or required combat capabilities prevent actionable handoff. Aggregate late-goal targets do not become new gates on independent earlier chapters. All original goal gaps remain available, including unknown no-Prayer combat and unsupported skill coverage. A quest-completion gap in the method-only context can now have a corresponding strategic quest candidate; the underlying diagnostic is preserved rather than silently rewritten.

Method actionability is delegated unchanged: `ACTIONABLE` maps to `READY`, `ACTIONABLE_WITH_PREP` to `READY_WITH_PREP`, and unproven preparation remains `UNRESOLVED`. Neither score nor goal relevance bypasses that gate. Quest readiness never approves inventory disposal, death storage, acquisition, item movement or combat tactics. Full quest preflight and a deterministic SafetyService remain separate future work.

Strategic ordering first uses generic goal-graph proximity. A ready milestone on `GoalState`'s active frontier has `FRONTIER_HANDOFF` priority. When no such action is selected, a method that matches a missing prerequisite on a blocked milestone whose dependencies are complete has `FRONTIER_UNBLOCKER` priority. Other positively relevant methods retain `GOAL_PROGRESS` priority. This uses evaluated scope IDs, dependency states and requirement facts already retained by the goal model; it contains no goal, milestone or skill names. Blocked, unknown and unresolved actions remain ineligible regardless of tier.

Within a priority tier, comparison uses only shared factors and the existing centralized `MethodScorer` weights/arithmetic. Method signed contributions are projected without changing the original nine-factor score. Quests require explicit normalized setup, transition, disruption, risk and uncertainty inputs for beginning the handoff; absent inputs are listed and leave the candidate unscored. No quest XP, efficiency or storage reward is invented. The formula and exact tie rule are documented in `DATA_SCHEMA.md`.

Results retain every candidate's structural priority, direct frontier requirements, score, missing factors and the complete original context. A higher-scoring unresolved method remains visible when a ready quest wins. Strategic ordering may differ from method-only ordering because it first respects goal-graph proximity and then compares a smaller shared factor set. Results are snapshot decisions at the original evaluation instant, not durable authorization: callers must recreate the entire context after account resets or relevant state changes. No state is persisted.

### Live Planner v1

`planning/PlanningService` owns the smallest live composition boundary: it loads all indexed production goals and methods, accepts an immutable `AccountState` plus a generic selected goal ID, builds `AccountStateFacts`, and delegates to `GoalContext` and `StrategicDecision`. It returns structured status, primary/alternative candidates, diagnostic reason, counts and the earliest relevant fact-expiry boundary. It contains no skill names, method IDs or Recipe-for-Disaster branch.

The live eligibility rule is stricter than general domain actionability. A primary needs positive goal progress, a complete score, and exact domain readiness `READY` or `READY_TO_HANDOFF`. `READY_WITH_PREP`, unknown, blocked, unscored and coverage-gap candidates remain explanations or alternatives. Goal completion and no selected goal produce no action. The UI labels a method `SETUP READY` because location/travel is not verified; domain readiness is unchanged. Quest external factors are zero only because the modeled action is displaying a manual handoff; the score does not model quest execution, item readiness or combat safety. Encoded unknown safety requirements still block the handoff.

`ui/PlannerViewModel` translates structured results into bounded display text. Method explanations use the matched target, verified setup requirements, structured start location and trusted XP profile; they do not expose raw catalog reasons, assumptions, start instructions or editorial stop-condition prose. `UimAtlasPanel` renders wrapping plain text at its actual parent width and places alternative title, status and reason on separate lines. Swing never evaluates methods or goals. Client reads, observation refresh and planning run on RuneLite's client thread. The immutable view model crosses to Swing's event-dispatch thread. Expiring relevant facts mark their generic observer families dirty before recalculation. Logout, loading, world hop, profile change, account-hash change and plugin shutdown clear the snapshot/result before another account can be displayed. No selected account identifier is stored or logged.

### 1. Account state

Purpose: normalize observable RuneLite/game state into a stable model used by the rest of the plugin.

Candidate responsibilities:

- account type
- skill levels and XP
- quest states
- inventory
- equipment
- current location/region
- relevant varbits/varps/config state
- current activity signals
- current Slayer context where observable
- active timers/opportunities where available

Suggested conceptual types:

- `AccountState`
- `SkillState`
- `QuestState`
- `InventoryState`
- `EquipmentState`
- `ActivityState`

Do not expose raw RuneLite APIs throughout the recommendation engine. Normalize at the edge.

### 2. Observed storage and provenance

Purpose: represent where items/currencies/capabilities are known to exist and how trustworthy that knowledge is.

Conceptual types:

- `StorageSnapshot`
- `StorageLocation`
- `ObservedItemStack`
- `StateConfidence`
- `ObservationTimestamp`
- `StateProvenance`

Important rule: preserve provenance. Do not merge every observed quantity into one anonymous `owned items` map if doing so destroys the ability to explain retrieval or storage actions.

Conceptual confidence states:

- `VERIFIED_NOW`
- `LAST_OBSERVED`
- `USER_CONFIRMED`
- `UNKNOWN`
- `CONFLICTED`

A storage source may be available through native observation, an integration, or both.

### 3. Capability model

Purpose: answer questions such as:

- Can the player use this method?
- Can this item be stored here?
- Does the player have this travel option?
- Is this POH furniture tier available?
- Is this STASH built?
- Is the required area/spellbook/minigame unlock available?

Conceptual type:

- `CapabilityService`

Capabilities should derive from account state and data definitions rather than many hand-coded booleans.

### 4. Game-data repositories

Purpose: load validated resource definitions.

Candidate repositories:

- `MethodRepository`
- `MilestoneRepository`
- `ItemRuleRepository`
- `StorageRuleRepository`
- `QuestRuleRepository`
- `OpportunityRepository`

These should be thin loading/query layers over resource files, not databases embedded in Java.

### 5. Candidate generation

Purpose: convert a selected milestone and current account state into a small set of valid next actions.

Pipeline:

```text
selected milestone
    -> unmet meaningful requirements
    -> prerequisite graph
    -> valid method/activity candidates
    -> hard-gate filtering
```

Examples of candidate types:

- training method
- quest
- POH/STASH/storage unlock
- resource-gathering activity
- recurring opportunity
- boss/activity milestone
- preparation action when no method is currently valid

The engine should not turn every tiny prerequisite into a visible recommendation if it can be bundled into a meaningful method-level action.

### 6. Safety service

Purpose: centralize risky UIM logic.

Candidate responsibilities:

- active deathbank/deathpile implications
- dangerous death compatibility
- looting-bag-sensitive actions
- disposal safety
- stale safety-critical state
- user safety preference
- method danger classification

Conceptual type:

- `UimSafetyService`

Safety logic should be deterministic, heavily tested, and isolated from generic scoring where practical.

### 7. Transition planner

Purpose: estimate the UIM cost of moving from current state into and out of a candidate method.

Potential components:

- travel/setup time
- retrieve/store operations
- free-slot changes
- gear changes
- looting-bag changes
- unnoting/gathering/reclaiming
- item disposal/reacquisition
- expected cleanup after the method

Conceptual types:

- `TransitionPlanner`
- `TransitionPlan`
- `PreparationAction`
- `ReacquisitionCost`

The transition planner should produce both a numeric/ordinal cost and explainable actions.

### 8. Storage planner

Purpose: reason about permanent storage improvements and current item placement.

Conceptual types:

- `StoragePlanner`
- `StorageOpportunity`
- `StorageAction`

POH/STASH scoring should consider long-term slot relief and downstream setup savings, not just current inventory.

### 9. Recommendation engine

Purpose: score valid candidates and choose the best next action.

Conceptual score:

```text
score =
    goalProgress
  + unlockValue
  + storageValue
  + currentSetupFit
  + synergy
  + playStyleFit
  + sessionFit
  + readyOpportunityValue
  - transitionCost
  - inventoryPressure
  - reacquisitionCost
  - riskPenalty
  - uncertaintyPenalty
```

Weights must be centralized/configurable rather than scattered through feature code.

Avoid pretending the score is mathematically exact. It is a decision model, not a truth oracle.

Conceptual types:

- `RecommendationEngine`
- `CandidateRecommendation`
- `RecommendationScore`
- `ScoreBreakdown`

### 10. Explanation layer

Purpose: turn the winning internal reasoning into a few useful human-facing reasons.

Do not dump raw scores by default.

Good explanation examples:

- Uses supplies already carried
- Unlocks POH storage for three items
- No storage changes required
- Frees five slots afterward
- Birdhouses are not ready
- Switching methods would require a large setup rebuild

Conceptual type:

- `RecommendationExplainer`

### 11. Focus / activity state

Purpose: prevent recommendation churn while the player is engaged in a valid activity.

Conceptual types:

- `FocusSession`
- `ActivityDetector`

The engine can defer rescoring until meaningful boundaries unless safety-critical state changes.

### 12. Opportunity engine

Purpose: track ready recurring content without constant interruption.

Candidate opportunities:

- birdhouses
- herbs
- trees
- farming contracts
- clues
- selected dailies/recurring tasks if they later prove useful

Conceptual type:

- `OpportunityEngine`

Ready opportunities should usually influence the next decision at a task boundary.

### 13. History and progress

Later module.

Potential responsibilities:

- session XP
- milestone history
- UIM Journey
- major unlock markers
- optional WOM context

Do not let progress/history code block the core recommendation engine.

## Integration architecture

Integrations should be isolated behind adapters.

Conceptual interface:

```text
IntegrationAdapter
  - availability
  - refresh/request
  - normalized snapshot/event output
  - reset on account/profile change
```

Candidate adapters:

- `DwmStorageAdapter`
- `InventorySetupsAdapter`
- `WiseOldManAdapter`
- additional local handoff adapters only after upstream support is verified

### Dude, Where's My Stuff?

Preferred use:

- consume tracked storage snapshots and provenance through its supported RuneLite PluginMessage contract
- do not vendor/copy its storage implementation
- accept absence or unsupported versions safely
- never invent storage data when no valid response exists

### Inventory Setups

Preferred use:

- detect/list relevant player setups when supported
- hand off to existing setup/filtering behavior rather than duplicate a full loadout manager

### Quest Helper

Reviewed 2026-09-14: the directly fetched [Plugin Hub marker](https://raw.githubusercontent.com/runelite/plugin-hub/master/plugins/quest-helper) and Quest Helper master both resolved to `a52646118f0e5ea63a6b3331cefa98087a7b4d6c` (build version 4.17.1). The source audit found no documented supported external start/open-helper contract, no incoming `PluginMessage` handler, and no separate published integration API in the repository. This is a finding about that reviewed revision, not a guarantee about future releases.

RuneLite does provide [PluginMessage](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/events/PluginMessage.html) for inter-plugin data, but a transport alone is not a receiving contract. Quest Helper's [DetailedQuestStep](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/steps/DetailedQuestStep.java) and [QuestRequirementsPanel](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/panel/QuestRequirementsPanel.java) send outgoing messages to Shortest Path and Not Enough Runes. They do not accept requests to start a quest.

[QuestManager.startUpQuest](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/managers/QuestManager.java) and [QuestMenuHandler](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/managers/QuestMenuHandler.java) contain public implementation methods tied to Quest Helper's internal objects and UI state. Java visibility does not establish a supported Plugin Hub dependency API. UIM Atlas neither imports nor invokes them.

The implemented descriptor contains the RuneLite quest ID already validated in goal data, the milestone display name, target `QUEST_HELPER`, and availability `MANUAL_ONLY`. It is not a Quest Helper enum mapping or a plugin-presence assertion. The player can manually select the quest as described in [Quest Helper's README](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/README.md). No opening, installation or execution occurs. Absence of Quest Helper does not affect planning. Re-review upstream before adding an optional automatic adapter.

Compatibility review: this milestone adds no third-party dependency, reflection, runtime downloads, network calls, configuration writes, synthetic game events or gameplay automation. Resources remain bundled and loaded as streams, consistent with [Plugin Hub guidance](https://github.com/runelite/plugin-hub#plugin-resources). Plugin Hub's own review remains required before publication.

### RuneLite native features

Before duplicating timers, farming state, XP tracking, pathing, or other core-client functionality, inspect whether RuneLite exposes reusable state/events or whether a small adapter can consume existing behavior.

## UI architecture

Keep UI separated from decision logic.

Possible top-level panel sections:

- recommendation/focus
- progress
- journey
- settings

The recommendation view should consume a UI-ready view model rather than call the engine repeatedly from Swing components.

Conceptual types:

- `UimAtlasPanel`
- `RecommendationViewModel`
- `FocusViewModel`
- `ProgressViewModel`

Avoid many custom Swing classes for tiny visual variations. Reuse generic components.

## Data loading and validation

Resource definitions should be validated at startup/build/test time.

Validation examples:

- unique IDs
- references resolve
- no impossible requirement cycles
- item IDs are valid where expected
- method stop conditions exist
- danger policy is specified for safety-sensitive methods
- source metadata exists for curated UIM knowledge
- schema version compatibility

Prefer failure with a clear developer error over silently accepting malformed game data.

## Package direction

Exact package names may change, but keep boundaries recognizable.

```text
com.uimatlas
  plugin/
  state/
  capability/
  data/
  recommendation/
  transition/
  storage/
  safety/
  opportunity/
  integration/
  progress/
  ui/
```

Do not create deeply nested package trees until complexity actually requires them.

## Performance rules

- Prefer RuneLite events to polling.
- Recompute only when inputs relevant to a recommendation change.
- Cache parsed immutable data definitions.
- Avoid scanning every storage definition every game tick.
- Avoid network calls on the client thread.
- Keep optional integrations asynchronous or event-based as appropriate.
- Reset account-specific cached state cleanly when identity/profile changes.

## Testing strategy

Tests should target behavior and safety rather than Java internals.

High-value test families:

- requirement evaluation
- milestone prerequisite resolution
- method validity
- transition scoring
- POH/STASH unlock scoring
- item reacquisition/disposal rules
- stale/unknown storage behavior
- deathbank + unsafe-activity blocking
- recommendation stability while focused
- integration absence/version mismatch
- scenario regressions for representative early/mid/late UIM accounts

Use fixture data for account scenarios rather than giant hand-constructed Java tests where practical.

## Build-time size checks

Add tooling early to report:

- Java source files
- Java lines
- approximate Java source tokens
- largest Java files
- resource data size

Warnings should appear before the source approaches the architecture-review threshold defined in `CODING_RULES.md`.

## Architecture litmus test

Before merging a large feature, ask:

1. Is this RuneScape knowledge or engine behavior?
2. Could a resource definition represent most of it?
3. Does another mature plugin already own the execution/tracking problem?
4. Does this preserve state confidence/provenance?
5. Does it materially improve the next-action decision?
6. How much Java does it add?

If the feature adds a lot of Java but little unique recommendation intelligence, redesign it.
