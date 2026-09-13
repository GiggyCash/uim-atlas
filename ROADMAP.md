# UIM Atlas Roadmap

## Delivery philosophy

Build the brain first, prove it with a narrow vertical slice, then expand game coverage mostly through data.

Do not implement every UIM method before the engine can make one good recommendation from real account state.

## Phase 0 - Repository and guardrails

Goal: establish a safe foundation before feature code.

Deliverables:

- product specification
- architecture specification
- data schema
- coding rules
- Codex `AGENTS.md`
- Java-size reporting plan
- basic RuneLite Plugin Hub project scaffold

Exit criteria:

- repository structure is clear
- Codex can read permanent instructions from the repo
- no large game-data implementation exists yet

## Phase 1 - Thin working plugin

Goal: produce the smallest useful RuneLite plugin shell.

Scope:

- plugin entry point
- config
- sidebar panel
- UIM account-mode detection
- normalized account state for:
  - skills/XP
  - quests
  - inventory
  - equipment
  - current location
- minimal event-driven refresh
- Java-size report task

UI should show only:

- UIM Atlas
- detected account mode
- account-state loaded/ready state

Do not add methods, milestones, death mechanics, WOM, graphs, or broad data yet.

Exit criteria:

- project builds
- tests run
- plugin loads in RuneLite dev environment
- state updates correctly
- Java-size report exists

## Phase 2 - Recommendation engine vertical slice

Goal: prove that generic data can produce a specific next method.

Scope:

- versioned method schema
- milestone schema
- requirement evaluator
- candidate generation
- basic scoring
- explanation layer
- first recommendation card

Use a deliberately small knowledge set, enough to exercise different behavior.

Suggested early methods:

- Mahogany Homes
- Tempoross
- Wintertodt
- one Herblore route
- one Agility route
- one combat/Slayer route
- one Farming/Hunter opportunity

Suggested first major milestone:

- Recipe for Disaster

Reason: it creates a useful mix of quests, skills, items, training choices, and early/mid-game UIM decisions.

Exit criteria:

- engine chooses a method rather than a vague level target
- recommendation can show start point, missing prep, stop condition, and why
- satisfied requirements remain hidden
- adding another method mostly means adding data

## Phase 3 - Storage and provenance

Goal: make recommendations inventory/storage-aware.

Scope:

- storage snapshot model
- provenance/freshness model
- capability service
- optional Dude, Where's My Stuff? adapter
- current-inventory fit scoring
- free-slot pressure
- basic storage actions

Confidence states:

- VERIFIED NOW
- LAST OBSERVED
- USER CONFIRMED
- UNKNOWN
- CONFLICTED / NEEDS RECHECK

Exit criteria:

- recommendations differ when the same stats have different inventory/storage state
- unknown storage is never treated as verified
- DWM absence does not break the plugin

## Phase 4 - POH and STASH strategy

Goal: make permanent UIM storage progression a major planning factor.

Scope:

- POH storage capability definitions
- STASH capability definitions
- storage-unlock value
- permanent slot-relief calculation
- clue/gear transition benefits
- Construction method scoring
- post-unlock follow-up actions

Example behavior:

`Mahogany Homes` can beat a faster XP method because the target Construction level unlocks storage that permanently frees several useful inventory slots.

Exit criteria:

- storage unlocks receive meaningful account-specific weight
- plugin does not blindly recommend Construction when the unlock is irrelevant
- recommendation can explain exactly why a storage unlock matters

## Phase 5 - Transition planner

Goal: understand whether switching methods is actually worth it.

Scope:

- setup delta
- travel/setup friction
- retrieve/store changes
- gear changes
- unnoting/gathering/reclaiming
- reacquisition classes
- expected cleanup afterward
- switch threshold/hysteresis

Exit criteria:

- `keep doing what you're doing` can win
- small XP/hr gains do not cause expensive setup changes
- transition explanation is understandable

## Phase 6 - Item lifecycle intelligence

Goal: help players decide what to keep, store, use, reclaim, alch, or dispose of safely.

Scope:

- item lifecycle data
- future quest/clue/diary/milestone relevance
- POH/STASH set requirements
- reacquisition difficulty
- substitutes
- protected-item preference
- preparation actions

Start with high-value UIM categories rather than every item in the game:

- common permanent/rare UIM gear
- clue/STASH equipment
- quest items with meaningful reacquisition burden
- common storage-heavy supplies

Exit criteria:

- disposal-style advice is conservative
- GE value is not used as a proxy for replacement difficulty
- protected items are respected

## Phase 7 - UIM safety subsystem

Goal: safely support death-related UIM planning.

Scope:

- active deathbank model
- activity safety compatibility
- deathpile state/timers where reliably observable
- looting-bag-sensitive transitions
- stale-state reconciliation
- user safety preference
- explicit warning/acknowledgement UX

Exit criteria:

- unsafe activity is blocked/strongly penalized with active risky storage
- safety-critical unknown state never becomes a guess
- scenario tests cover representative loss cases
- no automation of dangerous actions

## Phase 8 - Method library expansion

Goal: broaden UIM training coverage without growing Java significantly.

Scope:

- skill-by-skill UIM Wiki audit
- relaxed/AFK methods
- balanced methods
- efficient methods
- high-intensity methods
- tick-manipulation metadata where relevant
- secondary XP
- activity synergies
- inventory footprint
- method outputs
- source/review metadata

Exit criteria:

- most new coverage is resource data
- Java-size growth remains small
- method records pass automated validation

## Phase 9 - Quest and activity preflight

Goal: choose and prepare activities without duplicating specialist helpers.

Scope:

- quest start points
- UIM-specific free-slot/gear constraints
- Entrana restrictions
- dangerous fights
- temporary-item pressure
- specialist-plugin handoff hints

Boundary:

- UIM Atlas chooses/prepares
- Quest Helper or activity-specific plugins execute

Exit criteria:

- quest recommendation is useful before execution begins
- no large copied quest-step database exists

## Phase 10 - Opportunity queue

Goal: incorporate recurring UIM opportunities without nagging.

Scope:

- birdhouses
- herbs
- trees
- farming contracts
- selected clue opportunities
- natural task-boundary logic

Exit criteria:

- ready timers enter a queue
- active play is not interrupted unnecessarily
- opportunities can become the next recommendation at a sensible boundary

## Phase 11 - Focus mode and session intent

Goal: improve long-session usability.

Scope:

- activity detection
- current-focus card
- XP remaining/progress
- next review point
- temporary session preferences such as AFK/30 minutes/Focused

Exit criteria:

- recommendation churn is low while a valid activity is in progress
- temporary session preference influences scoring without rewriting long-term settings

## Phase 12 - Progress and UIM Journey

Goal: make real account progress emotionally visible without fake rewards.

Scope:

- session/day/week/month/all-time tracked progress where practical
- milestone history
- major unlock markers
- real achievement celebrations
- optional personal bests

Exit criteria:

- celebrations are optional/restrained
- history reflects real OSRS progress
- no plugin currency/streak economy exists

## Phase 13 - Optional Wise Old Man integration

Goal: add social/progress context without making the core engine dependent on external services.

Scope:

- WOM opt-in
- progress/history context
- SOTW/BOTW influence
- competition weighting

Exit criteria:

- plugin remains fully usable without WOM
- external data use is clear to the player
- competition focus never blindly overrides UIM safety/setup logic

## Phase 14 - Coverage hardening and Plugin Hub readiness

Goal: prepare for broad public use.

Scope:

- representative early/mid/late UIM scenario suites
- performance profiling
- event/update audit
- Java-size audit
- game-data freshness audit
- safety review
- Plugin Hub compliance review
- settings/UX polish
- documentation

Exit criteria:

- stable build
- acceptable performance
- conservative safety behavior
- manageable Java source size
- clear data-maintenance process
- Plugin Hub submission ready

## Immediate Codex starting task

The first Codex coding task should be intentionally small:

1. Read all root Markdown specification files.
2. Scaffold a minimal RuneLite plugin that compiles.
3. Detect UIM account type.
4. Create a normalized account-state model for skills/XP, quests, inventory, equipment, and current location.
5. Add an empty data-driven recommendation framework with no broad game-data implementation.
6. Add a minimal sidebar showing UIM Atlas, detected account type, and state readiness.
7. Add tests where practical.
8. Add Java source-size reporting.
9. Run build/tests and fix failures.
10. Report files changed and source-size metrics.

Explicitly do not implement the later roadmap phases in the first task.
