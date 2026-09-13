# UIM Atlas Coding Rules

## Purpose

These rules exist to keep UIM Atlas small enough to maintain, safe enough for UIM decision support, and suitable for RuneLite Plugin Hub review.

## 1. Java is for behavior

Java should contain:

- RuneLite event handling
- state normalization
- capability evaluation
- recommendation/scoring logic
- transition planning
- safety rules
- integration adapters
- UI/view-model logic

Java should not become the primary store for:

- hundreds of training methods
- quest prerequisite tables
- item lifecycle facts
- POH/STASH eligibility tables
- milestone graphs
- large storage mappings
- static XP/method metadata

Put repeatable game knowledge in validated resource data.

## 2. Source-size budget

Target:

- preferred Java source size: under approximately 100k source tokens
- warning threshold: approximately 130k
- mandatory architecture review before approximately 150k

The threshold is not permission to fill the budget. Smaller is better when behavior remains clear.

Every major milestone should report:

- Java file count
- Java LOC
- approximate Java source tokens
- ten largest Java source files
- total resource/data size

Add a build/report task early so this is visible continuously.

## 3. Avoid repetition patterns that caused past bloat

Do not use:

- one class per method
- one class per quest
- one class per STASH
- one class per item rule
- giant method/quest/item enums
- enormous switch statements
- duplicated recommendation explanations
- duplicated integration state

Prefer:

- immutable generic definitions
- evaluators
- repositories
- rule composition
- resource records
- fixture-driven tests

## 4. Small vertical slices

Codex tasks should be bounded.

Good task:

- scaffold plugin + account state + minimal panel
- add one generic method schema and validator
- add DWM adapter with safe fallback
- add storage-unlock scoring with three fixture scenarios

Bad task:

- implement all UIM methods
- build the whole product
- add every storage type and all quests in one pass

Complete, build, test, and review each slice before expanding breadth.

## 5. RuneLite boundaries

Keep Plugin Hub compatibility in mind from the beginning.

Do not use forbidden or review-hostile techniques such as:

- reflection to bypass normal APIs
- JNI
- subprocess execution
- runtime-downloaded executable code
- hidden automation

Do not automate:

- clicks
- movement
- item dropping
- item retrieval
- death actions
- purchases/sales
- combat
- banking/storage interactions

UIM Atlas observes and advises.

## 6. Event-driven state

Prefer RuneLite/game events to repeated polling.

Avoid expensive work on every game tick unless it is demonstrably necessary.

Recommendation recalculation should be triggered by relevant state changes, such as:

- inventory/equipment changes
- XP/level changes
- quest state changes
- storage snapshot changes
- location/activity changes
- timer readiness
- selected-goal/preference changes
- safety-critical death/storage changes

Debounce/coalesce noisy changes where appropriate.

## 7. State confidence is mandatory

Safety-sensitive state must retain freshness/provenance.

Do not reduce all storage knowledge to `item exists = true`.

When an important fact is unknown, stale, or conflicting:

- preserve that state
- apply uncertainty penalties or block the recommendation
- surface `Check needed` when the player must refresh it

Never guess a safety-critical storage fact.

## 8. UIM safety code requires tests

Changes affecting the following require targeted regression tests:

- deathbanks/item retrieval services
- deathpiles
- looting bag safety
- Wilderness/PvP implications
- item disposal/alching/dropping advice
- storage-loss semantics
- stale safety-critical state

Prefer conservative false negatives to dangerous false positives.

## 9. Method recommendations must be specific

The engine should recommend a method/activity whenever possible.

Do not produce generic player-facing strings such as:

- Train Construction
- Get 70 Herblore
- Level Fishing

A method recommendation should be able to produce:

- method/activity name
- useful start point
- actual missing prep
- stop condition
- winning reasons
- relevant follow-up unlock/action

Do not duplicate the full execution loop when a specialist plugin already handles it.

## 10. Hide satisfied requirements

Requirements are primarily engine inputs.

Normal UI should not tell a player they meet a requirement they obviously already meet.

Only show:

- missing preparation
- missing prerequisite when it becomes the next meaningful action
- uncertainty/check-needed state
- a short explanation of why the method won

## 11. POH/STASH are first-class strategy

Storage unlock value must not be bolted on later as a tiny bonus.

Scoring should support substantial value for:

- permanent slot relief
- gear/set storage
- clue/STASH simplification
- future transition reduction
- transport/capability unlocks

But it must remain account-specific and compare against real cost.

## 12. Integrate instead of vendor

When another maintained RuneLite plugin exposes a stable local interface, prefer a thin adapter.

Do not copy large upstream codebases into UIM Atlas just to save integration work.

Before adding an integration:

1. verify the current upstream API/contract
2. isolate it behind an adapter
3. support absence/version mismatch
4. reset state cleanly on account/profile changes
5. document any external network data sent

## 13. Network integrations are optional

UIM Atlas should work without external services.

For optional services such as Wise Old Man:

- make the feature opt-in or clearly configurable
- disclose what account information is sent
- use reasonable caching/rate limits
- keep network work off the client thread
- fail without breaking core recommendations

## 14. UI should not own business logic

Swing panels should render view models and emit user intent.

Do not bury recommendation scoring, storage safety, or requirement evaluation inside UI classes.

Do not create many bespoke panel classes for small stylistic differences.

## 15. Performance before cleverness

Prefer clear, predictable code over hyper-generic frameworks.

Do not introduce a heavy abstraction library just to save a few lines.

The project should remain easy for a RuneLite reviewer and future contributor to understand.

## 16. Data changes need validation

Any new method/milestone/storage/item data should pass schema/reference validation.

Safety-sensitive records should include source/review metadata where appropriate.

Do not silently skip malformed records in production without a visible developer signal.

## 17. Recommendation stability

Do not constantly churn recommendations from tiny score changes.

Support:

- focus state
- switch thresholds/hysteresis
- natural task boundaries
- explicit user request for an alternative

A small theoretical improvement should not cause a costly UIM setup change.

## 18. Explainable scoring

Scoring may be weighted, but winning reasons must be understandable.

Do not make a recommendation that cannot explain itself in a few player-facing factors.

Examples:

- uses current supplies
- frees permanent slots
- unlocks POH storage
- no setup change
- avoids risky death storage
- progresses SOTE

## 19. Git hygiene

- Keep commits focused.
- Do not mix large data imports with unrelated engine refactors.
- Include tests with behavior changes.
- Avoid generated build output in Git.
- Keep resource generation reproducible.

## 20. Definition of done for a Codex task

Before claiming a coding task is complete:

1. build the project
2. run relevant tests
3. fix compilation/test failures
4. summarize files changed
5. report any architecture/schema changes
6. report Java-size impact for material changes
7. call out known limitations honestly
