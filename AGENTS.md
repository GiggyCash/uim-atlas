# AGENTS.md

This file is the mandatory working contract for Codex and any coding agent operating in this repository.

## Mission

Build UIM Atlas as the best quality-of-life planning plugin for Ultimate Ironman accounts while keeping the implementation small, safe, reviewable, and RuneLite Plugin Hub compliant.

The core product question is:

> Given this exact UIM, this exact inventory, these known storages, this goal, and this play style, what should the player do next, and what is the safest, least painful way to get there?

## Read before coding

Before making architectural or product changes, read:

1. `PRODUCT.md`
2. `ARCHITECTURE.md`
3. `DATA_SCHEMA.md`
4. `CODING_RULES.md`
5. `ROADMAP.md`

If code and these documents conflict, do not silently choose one. Prefer the documented product and architecture unless a deliberate repo update changes the contract.

## Hard architecture rules

- Java contains behavior, orchestration, adapters, state normalization, scoring, safety logic, and UI.
- Large RuneScape knowledge sets belong in data/resources, not Java.
- Do not create one Java class per method, quest, item, STASH, milestone, or training route.
- Do not create giant item/quest/method switch statements.
- Do not grow large enums that are effectively game databases.
- Prefer generic models such as method definitions, requirements, transitions, capabilities, item rules, milestones, and explanations.
- Prefer event-driven updates. Avoid expensive per-tick rescans when RuneLite events can provide the change.
- Integrate with mature plugins through stable interfaces when practical instead of copying or vendoring their implementations.
- Integrations must be optional and fail closed or degrade conservatively when unavailable.

## Java size budget

The earlier Gielinor Compass project became difficult to maintain after Java source size grew dramatically. UIM Atlas must prevent that from the beginning.

Target budgets:

- Preferred core Java source: under approximately 100k source tokens
- Warning threshold: approximately 130k
- Mandatory architecture review: approximately 150k

Do not justify repetitive Java because a feature is convenient to implement that way. Move repeatable game knowledge into validated data.

Every material coding milestone should report:

- Java file count
- Java line count
- approximate Java source token count if tooling exists
- ten largest Java files
- resource/data size

## Recommendation rules

Never output a vague recommendation when a useful method-level recommendation is possible.

Bad:

- Train Construction to 42
- Train Fishing
- Get 70 Herblore

Good:

- Mahogany Homes, with the appropriate start point, actual missing prep, stopping condition, and reason
- Tempoross, when it fits the player's current setup and goal
- a specific Herblore method chosen from the player's resources, inventory pressure, unlocks, and target

The engine should reason in layers:

`milestone -> requirement -> valid methods -> UIM constraints -> transition cost -> current-state fit -> recommendation`

The user should normally see the method, not the entire reasoning tree.

## UI rules

- Default UI is quiet, compact, and decisive.
- One primary recommendation should dominate the panel.
- Hide requirements that are already satisfied.
- Show preparation only when the player actually needs to do something.
- No ordinary `Start` button. Detect activity from game state where practical.
- Use progressive disclosure for `Why this?`, alternatives, storage detail, and advanced reasoning.
- Specialist plugins may handle execution details. UIM Atlas should choose, prepare, and hand off.
- Visual direction: dark RuneLite-native shell, restrained warm-gold accent, muted status colors, OSRS icons, minimal animation.

## POH and STASH priority

POH and STASH progression are strategic account upgrades, not minor conveniences.

Storage unlock scoring should consider:

- permanent inventory slots relieved
- current carried items that become storable
- future methods made easier
- future transition cost reduced
- clue and gear setup simplification
- transport or capability unlocks
- construction/resource/setup cost

A lower-XP method may correctly win when it produces a high-value permanent storage capability.

## State trust rules

Important state must carry provenance and freshness.

Supported conceptual confidence states include:

- VERIFIED NOW
- LAST OBSERVED
- USER CONFIRMED, where necessary
- UNKNOWN
- CONFLICTED / NEEDS RECHECK

Never convert unknown state into a guess. Never claim an item is safely stored when the plugin cannot verify or reasonably rely on the observed state.

## UIM safety rules

Deathpiling, deathbanking, looting-bag destruction, dangerous deaths, unsafe storage transitions, and item disposal are safety-sensitive.

For these systems:

- be conservative
- keep logic deterministic and reviewable
- add scenario tests
- surface clear warnings and assumptions
- require explicit acknowledgement for dangerous recommendations where product design calls for it
- never automate clicks, movement, dropping, death, retrieval, storage, purchases, sales, or combat
- never guarantee item safety

Unknown or stale safety-critical state should block or strongly penalize a recommendation rather than be guessed.

## Engagement rules

UIM Atlas should support engagement by making real progress clearer, not by manufacturing compulsion.

Allowed:

- milestone celebrations
- level or unlock celebrations
- progress bars
- personal bests
- UIM journey/history
- optional WOM competition influence

Avoid:

- fake plugin currency
- random reward chests
- login rewards
- punitive streaks
- FOMO
- nagging notifications
- constant animation

Prefer surfacing new recommendations at natural task boundaries rather than interrupting focused play.

## Integration rules

Where stable support exists, prefer adapters for:

- Dude, Where's My Stuff? storage snapshots
- Inventory Setups loadout handoff
- RuneLite native timer/farming tracking where reusable
- specialist activity plugins
- Quest Helper handoff
- optional Wise Old Man progress/competition context

Do not assume an integration API exists. Verify current upstream behavior before coding against it.

## Working style for Codex

- Work in small vertical slices.
- Do not attempt to implement the entire roadmap in one task.
- Build and test after each material slice.
- Fix compilation errors before claiming completion.
- Summarize files changed and architecture impact at the end of each task.
- When adding a large knowledge set, propose or extend the data schema first.
- When a feature would add substantial repetitive Java, stop and redesign it as data or an adapter.
- Preserve Plugin Hub compatibility.
