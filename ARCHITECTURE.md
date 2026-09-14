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

`data/MethodDefinitionLoader` validates a versioned synthetic JSON catalog into immutable `recommendation/MethodDefinition` values. `Requirement` evaluates a numeric fact with its existing `state/Observation` provenance and explicit freshness policy. `MethodEvaluator` separates hard blockers, missing preparation and unknown requirements. `MethodScorer` accepts explicit normalized inputs and returns a weighted breakdown only for available/preparable methods. Required unknown state cannot be offset by a high score.

This is a domain-only scaffold. It does not read the RuneLite client, alter account observation, run at startup, select a live recommendation or render UI. Three fixture methods reside exclusively in test resources. See the implemented v1 section of `DATA_SCHEMA.md` for the exact schema and scoring contract.

`state/AccountStateFacts` now projects one normalized snapshot into the generic `recommendation/FactLookup` interface. The dependency direction is `RuneLite Client -> RuneLiteAccountObserver -> AccountState -> AccountStateFacts -> FactLookup -> MethodEvaluator`. The evaluator also retains its existing map-based entry point; it knows neither account layout nor client APIs. Skill and slot facts are projected once; arbitrary item quantities are resolved on request from immutable containers, without an item registry or mutable cache.

The adapter accepts an explicit observation cutoff (normally the evaluation time) and never refreshes source timestamps. Direct facts retain source, confidence and time. Carried quantities require both inventory and equipment, retain both source labels, take the older timestamp and use the weaker confidence. Future-dated inputs are unknown at the cutoff so combining timestamps cannot conceal them. Evaluation must occur at the cutoff or later and continues to enforce each requirement's age/confidence policy. Unknown state stays explicit. See [account fact IDs and semantics](DATA_SCHEMA.md#account-state-fact-contract) for the stable namespaces and limits. This bridge is not wired into the plugin lifecycle yet.

`recommendation/MethodRanker` now orchestrates `FactLookup -> MethodEvaluator -> MethodScorer -> RankingResult` over caller-supplied candidates (a method definition plus explicit normalized scoring inputs). It has no mutable ranking state and uses one supplied evaluation instant. Immutable results retain each candidate's inputs, full evaluation diagnostics and optional score breakdown. Only `AVAILABLE` and `NEEDS_PREP` enter the ranked list; the optional best and ordered alternatives come exclusively from that list. `BLOCKED` and `UNKNOWN` remain unscored in a separate diagnostic list, preserving their original status and all unresolved requirements.

Eligible ordering is descending total score, then ascending method ID using Java `String` natural order. There is no status bonus, approximate tie threshold or minimum winning score. Excluded diagnostics are also ordered by ID, and duplicate candidate IDs are errors. This makes both lists independent of input order without a switching policy. The ranker reuses the existing scorer's eligibility and weights. Callers must provide a coherent immutable fact snapshot and scoring inputs derived for that snapshot. Preparation safety planning, candidate generation, focus/hysteresis, live wiring and player-facing explanations remain future work.

`recommendation/SetupScoringInputs` now derives four setup factors from existing method predicates/cost metadata and the observed carried setup exposed by `FactLookup`. It reuses `Requirement.evaluate` for observation trust, returns an immutable all-or-unresolved result with original observations, and composes with five explicitly supplied non-setup factors into the existing scorer input map. Required unknown setup cannot be exchanged for an optional uncertainty penalty. This is a domain-only friction estimate, not a transition action planner: no new container model, game registry, scorer weights, schema fields or client/UI dependencies are introduced. See [the formula and limitations](DATA_SCHEMA.md#account-specific-setup-scoring). The full-chain synthetic ranking test now uses this component instead of test-only setup scores and reverses the winning candidate when inventory changes.

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

Initial boundary:

- UIM Atlas chooses and prepares the quest
- Quest Helper handles quest execution

Do not depend on an unmerged or unofficial API. If a stable handoff API becomes available later, implement it as an optional adapter.

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
