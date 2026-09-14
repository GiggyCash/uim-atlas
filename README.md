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

Pre-alpha. The first working plugin observes account state and shows account mode, state readiness, total level, and occupied inventory slots in a minimal sidebar. The first authenticated live UIM smoke test passed through RuneLite and the Jagex Launcher, validating UIM detection, account-state loading, total level, inventory occupancy and sidebar behavior.

A domain-only recommendation foundation now loads three synthetic test methods, evaluates hard requirements and missing preparation with explicit freshness, and scores eligible methods using visible placeholder weights. See [the implemented schema](DATA_SCHEMA.md#implemented-foundation-synthetic-method-schema-v1). Fixtures exist only in test resources and cannot load as production advice. Live recommendations, recommendation UI and integrations remain unimplemented.

An immutable `AccountStateFacts` adapter now exposes observed real skill levels/XP, inventory occupancy/free slots, and exact item quantities in inventory, equipment and combined carried scopes through a generic `FactLookup`. It preserves observation provenance and freshness, including unknown state and conservative combined quantities. See [the fact-ID contract](DATA_SCHEMA.md#account-state-fact-contract). A pure-domain synthetic test connects account state to method evaluation; the adapter is not yet wired into the live plugin.

The domain-only `MethodRanker` now evaluates multiple candidates, reuses `MethodScorer`, and selects an optional best plus ordered eligible alternatives. `AVAILABLE` and `NEEDS_PREP` compete by descending score, then ascending method ID; `BLOCKED` and `UNKNOWN` retain unscored diagnostics and cannot win. Results preserve missing preparation, original scoring inputs and contribution breakdowns. Synthetic tests cover deterministic ordering, UIM scoring tradeoffs and an inventory change reversing the winner through the full account-state/fact/evaluation/scoring chain. Live recommendation wiring remains unimplemented.

`SetupScoringInputs` now derives current setup fit, setup cost, transition cost and inventory disruption from method metadata and the observed quantities/free capacity exposed by `FactLookup`. Missing quantities, preparation burden and slot pressure use a small [documented formula](DATA_SCHEMA.md#account-specific-setup-scoring). Unusable observations produce an explicit unresolved result with provenance, never favorable default scores. The other five factors remain explicitly supplied. The full-chain synthetic test now uses this derivation: current setup beats a slightly faster alternative, and an inventory change reverses the winner. This component is domain-only and is not wired into the live plugin.

The first [production Construction catalog](src/main/resources/uimatlas/methods/construction-v1.json) now bundles three sourced methods: novice oak Mahogany Homes, adept teak contracts, and limestone attack stones with an existing flamtaer bag. [Production schema v3](DATA_SCHEMA.md#production-construction-catalog-v1-schema-v3) adds immutable source/review metadata, optional setup, working-capacity diagnostics, conditional efficiency profiles, explicit slot semantics, and a canonical item-ID validation boundary. Current Wiki pages and upstream item IDs were reviewed on 2026-09-14. XP ranges are conditional on a verified matching profile, setup quantities are starting checks, and storage value is neutral. Unsupported house ownership keeps all three methods UNKNOWN under the current account adapter; further limestone access/preparation checks are also unsupported. Tests verify these boundaries and the built JAR's production/test separation. Nothing selects or displays these methods live.

Capacity is now separated into genuine hard/preparation constraints, diagnostic working batches, and optional verified efficiency profiles. `MethodEfficiency` derives an explicit optional input for the existing scorer and retains all profile checks. Unknown helper state cannot improve efficiency; a verified full matching plank sack can establish better batching without claiming a published XP rate. Limestone uses ordinary free positions plus positions occupied by loose bricks, retaining the sourced 20-position preparation constraint. Container observations remain unsupported. See [the capacity contract](DATA_SCHEMA.md#capacity-and-efficiency-contract) for conditional XP, deterministic priority selection, and future actionability rules. Synthetic v1 and all live/UI behavior remain unchanged.

## Local development

Use JDK 11–21 (verified with JDK 21). The checked-in Gradle 8.10 wrapper downloads the build tooling; no system Gradle installation is required. RuneLite is pinned to release `1.12.38` in `build.gradle` for reproducible API behavior. Review and update that pin as RuneLite releases change.

```sh
./gradlew build
./gradlew test
./gradlew javaSourceSize
./gradlew run
```

On Windows, use `gradlew.bat`. In an IDE, import the Gradle project and run `com.uimatlas.UimAtlasDevLauncher` from the test source set with `--developer-mode --debug`. The development launcher uses RuneLite's normal `ExternalPluginManager.loadBuiltin` workflow. A desktop display and network access to RuneLite/game services are required to launch the client. Enable **UIM Atlas** in RuneLite's plugin list if needed, then open its compass sidebar button. **Show sidebar** is the only plugin setting.

The plugin JAR is `build/libs/uim-atlas-0.1.0.jar`; RuneLite dependencies and the development launcher are not bundled into it. Test reports are under `build/reports/tests/test/`. Every build runs the source-size report, also saved as `build/reports/java-source-size.txt`. It reports all Java and core Java separately, estimates tokens as characters divided by four, warns at ~100k/~130k core tokens, and fails at ~150k pending architecture review. Resource/data bytes are reported separately for main and test sources. Tests depend on JAR creation and inspect the actual artifact.

## State contract and current limitations

- `state/RuneLiteAccountObserver` is the only component that reads the client. `AccountStateService` publishes immutable, session-only snapshots. Domain values do not retain RuneLite objects. Skills use RuneLite enum names as identifiers; quests use RuneLite quest IDs; item containers preserve slot indices, exact item IDs, and quantities.
- Observations carry source, observation time, and confidence. Missing containers are **unknown**, while an observed empty container has zero occupied slots. Total level sums real skill levels, excluding RuneLite's aggregate skill; boosted levels and XP remain available separately.
- Reads start at a logged-in game tick with an available account hash. Stat/container events mark only their corresponding inputs dirty and coalesce at the next tick. The small account-mode and current-location reads run once per game tick; unchanged skills, containers, and quests are not rescanned.
- Quest status uses RuneLite's read-only `Quest.getState` API after login and after `QUESTLIST_INIT`, outside script callbacks. RuneLite does not expose a universal quest-stage-change event. Quest snapshots are therefore **LAST OBSERVED**, not continuously verified; unavailable quest reads stay **UNKNOWN** until a subsequent refresh. This snapshot must not silently become a verified prerequisite for future recommendations. No quest rules or prerequisite tables are copied into Java.
- **Loaded** means every implemented section is available, including quest statuses; it does not upgrade quest freshness. **Partial** means some state is unavailable. **Not logged in** also covers loading, reconnecting, and world hopping, when all prior observations are conservatively cleared. Logout, profile changes, disable/re-enable, and account-hash changes also reset state. Nothing is persisted or sent to external services by UIM Atlas.
- Location includes world number/type flags, scene coordinates, plane, region ID, world-view ID, and instance status. Coordinates inside instances or nested world views are not overworld route destinations. No safe-area inference, pathfinding, or risk classification is performed.

The observer follows the public [RuneLite API](https://github.com/runelite/runelite/tree/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api); development loading follows the official [example plugin](https://github.com/runelite/example-plugin). `Quest.getState` executes a read-only status script; UIM Atlas performs no gameplay actions.

## Manual client check

1. Launch with `./gradlew run`, enable UIM Atlas, and open its sidebar. Before login, values should be unknown and state should be **Not logged in**.
2. Log into a UIM. After state arrives, verify **Ultimate Ironman**, real total level, and inventory occupancy against the client. A stack counts as one slot. Empty equipment is valid; unavailable equipment remains unknown.
3. Change an inventory/equipment slot and gain XP or change a boosted level. Check that the next snapshot reflects the event. Reopen the quest list to request a fresh quest observation.
4. Hop worlds, log out, switch accounts/profiles, and disable/re-enable the plugin. Check that old account values disappear and are re-observed. Other account modes should be labelled accurately, never as UIM.
5. Toggle **Show sidebar** and verify that the navigation button disappears/reappears without duplicates.

Automated tests cover normalization, unknown state, freshness, event coalescing, account resets, Guice injection, event subscription, and sidebar lifecycle with a mocked client. Pure domain tests additionally cover method availability/preparation, safety-relevant unknowns, scoring tradeoffs and malformed resource validation. The first authenticated smoke test passed; repeat the manual steps above when changing live observation or sidebar behavior. JDK 21 may produce upstream RuneLite reflection/LWJGL diagnostics during debug startup; these do not originate in UIM Atlas.
