# UIM Atlas

**Your UIM, mapped.**

UIM Atlas is a RuneLite Plugin Hub project for Ultimate Ironman accounts. Its job is not to play the game for the player or replace specialist RuneLite plugins. Its job is to understand the player's observable account state and recommend the most useful **next method or activity** with UIM-specific preparation, storage, transition cost, and safety in mind.

## Product philosophy

> Observe deeply. Think deeply. Guide simply.

The main panel should stay quiet and decisive. It should normally show one strong next recommendation, the minimum preparation that actually needs attention, the stopping condition, and a short explanation of why this is the best fit for the account right now.

UIM Atlas should prefer real RuneScape progress over plugin-generated engagement systems. It may celebrate genuine milestones, but it should not create fake currencies, streak punishment, random rewards, FOMO, or casino-like mechanics.

## Core principles

- Recommend **methods**, not vague goals. Never stop at `Train Construction to 42` when the useful answer is `Mahogany Homes` with the relevant start point, preparation, stopping condition, and reason.
- Treat current inventory as part of strategy, not merely a requirement check.
- Give POH and STASH progression heavy strategic value when it permanently reduces inventory pressure or future transition cost.
- Requirements already satisfied stay hidden from the normal UI.
- Unknown or stale storage state must never be presented as verified fact.
- Dangerous UIM mechanics such as deathbanking and deathpiling are handled conservatively and never automated.
- Integrate with mature specialist plugins where practical instead of rebuilding their functionality.
- Keep Java small and generic. RuneScape knowledge belongs primarily in resource/data files.

## Working milestone set

The first-run main-goal choices are intentionally small:

- Automatic
- Recipe for Disaster
- Fire Cape
- Song of the Elves
- Quest Cape
- 2000 Total
- Max

`Automatic` is the default and should choose a sensible major milestone from account state.

## Intended integrations

Where stable local interfaces exist, prefer thin adapters over duplicated implementations. High-value candidates include:

- Dude, Where's My Stuff? for observed storage snapshots and provenance
- Inventory Setups for existing player loadouts and setup handoff
- RuneLite Time Tracking for existing timer state where reusable
- specialist activity plugins for execution guidance
- Quest Helper as a quest-execution handoff rather than duplicated quest steps
- Wise Old Man as an optional external integration for progress and SOTW/BOTW influence

Integrations must degrade safely when unavailable.

## Repository documents

Read these before making architectural changes:

- [`AGENTS.md`](AGENTS.md) - mandatory Codex working rules
- [`PRODUCT.md`](PRODUCT.md) - product behavior and UX contract
- [`ARCHITECTURE.md`](ARCHITECTURE.md) - system boundaries and technical design
- [`DATA_SCHEMA.md`](DATA_SCHEMA.md) - data-driven game-knowledge model
- [`CODING_RULES.md`](CODING_RULES.md) - size, quality, and safety constraints
- [`ROADMAP.md`](ROADMAP.md) - staged delivery plan

## Status

Pre-alpha. The repository is being designed before implementation so the project can grow in game coverage without repeating the code-size problems of earlier large RuneLite projects.
