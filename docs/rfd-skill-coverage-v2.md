# RFD Skill Coverage Pack v2

Reviewed 2026-09-14. This milestone adds twenty-one production methods across the eight previously uncovered Recipe for Disaster skill families. The production catalog now contains forty-one methods in fourteen skill files. No goal, planner layer, selected-skill orchestration, live wiring or UI is added.

## Result and limits

| Coverage measure | Before | After |
| --- | ---: | ---: |
| Encoded RFD skill requirements with a production method family | 5/13 | 13/13 |
| Encoded RFD skill requirements without a production method family | 8 | 0 |
| Production methods | 20 | 41 |
| Production method catalogs | 6 | 14 |
| Covered skill families with at least one setup provable by current skill/inventory/equipment facts | 4 | 13 |

All thirteen encoded skill requirements now have catalog coverage: Agility 48, Cooking 70, Crafting 40, Firemaking 50, Fishing 53, Fletching 10, Herblore 25, Magic 59, Mining 50, Ranged 40, Smithing 40, Thieving 53 and Woodcutting 36. `GoalContext` discovers every family from `activity` alone. A completed target removes `GOAL_PROGRESS` from its whole family.

This does **not** mean Recipe for Disaster is fully supported. It proves skill-training breadth. Quest execution preparation, direct-prerequisite quest routing, Quest-point acquisition and the safety-relevant no-Prayer combat capability remain incomplete.

## Exact v2 methods and selection reasons

| Skill | Production method | Why it was selected |
| --- | --- | --- |
| Cooking | `method.cooking.hosidius_mess_meat_pies` | A level-20, self-contained path whose supplies come from the activity; a small four-slot cycle avoids claiming the guide's maximum-throughput layout. |
| Cooking | `method.cooking.jugs_of_wine_carried` | A real carried-input transformation with exact grapes, jugs of water, fermentation delay and failure below 68 kept explicit. No ingredient acquisition is inferred. |
| Cooking | `method.cooking.hosidius_mess_pineapple_pizzas` | A level-65 self-contained higher-effort contrast; Humidify and a nearly empty inventory remain efficiency assumptions rather than gates. |
| Thieving | `method.thieving.east_ardougne_cake_stall` | A broadly available level-5 safespot with no food or equipment prerequisite; it stops at inventory pressure. |
| Thieving | `method.thieving.hosidius_fruit_stalls` | A level-25 two-stall location without nearby guard dogs, contrasting active low-setup training with random output pressure. |
| Thieving | `method.thieving.stealing_artefacts` | A level-49 mobile method that supports side activities; the drawer tool is a data-defined lockpick-or-hair-clip alternative. Energy and teleport support are not assumed. |
| Magic | `method.magic.arceuus_library_books` | A rune-free level-1 route that can be proved from ordinary live facts and contrasts with resource-consuming spell training. |
| Magic | `method.magic.mta_telekinetic_theatre` | A safe activity with a real level-33 distinction. The record covers one Telekinetic Grab using one law and one air rune, not a complete maze or reward grind. |
| Magic | `method.magic.fire_strike_sand_crabs` | A conservative one-cast combat action with the exact base rune cost. It carries CAUTION and makes no health, safety or long-term rune claim. |
| Smithing | `method.smithing.bronze_bar_edgeville` | A level-1 two-ore-to-one-bar transformation at a public furnace, useful when exact loose ores are already carried. |
| Smithing | `method.smithing.bronze_knives_varrock` | A level-7 carried-bar action that returns the hammer and produces one stack of five bronze knives. It contrasts compact output with iron platebodies. |
| Smithing | `method.smithing.iron_platebody_varrock` | The UIM guide's level-33 pre-40 anvil product, modeled only when five loose iron bars and a hammer are already carried. Mining, Superheat and disposal are excluded. |
| Firemaking | `method.firemaking.carried_regular_logs` | A level-1 one-log action with a returned tinderbox and an exact released inventory position. |
| Firemaking | `method.firemaking.carried_oak_logs` | A broadly available path from level 15 toward the RFD target, still bounded to currently carried logs rather than an assumed Woodcutting loop. |
| Ranged | `method.ranged.bronze_darts_sand_crabs` | A level-1 bounded combat session using an exact currently carried/equipped unpoisoned dart. It claims neither ammunition longevity nor safety. |
| Ranged | `method.ranged.dorgeshuun_crossbow_ammonite_crabs` | A level-28 weapon/ammunition contrast with explicit Fossil Island access, exact crossbow and exact bone bolts. The access capability currently remains unsupported. |
| Fletching | `method.fletching.arrow_shafts_regular_logs` | A level-1 one-log transformation that returns the knife and turns one unstackable log into one shaft stack. |
| Fletching | `method.fletching.headless_arrows` | A portable level-1 stack-to-stack action using exactly fifteen shafts and fifteen feathers; shop supply is excluded. |
| Woodcutting | `method.woodcutting.regular_trees_lumbridge` | A broad level-1 start proving a carried usable axe without assuming tool purchase or log disposal. |
| Woodcutting | `method.woodcutting.oak_trees_draynor` | The UIM guide's strong relaxed non-tick option from level 15, bounded by current capacity. |
| Woodcutting | `method.woodcutting.teak_castle_wars` | A level-35 higher-tier finish at a broadly reachable single tree, avoiding quest, Farming and tick-manipulation gates. |

These records cover useful contrasts: activity-supplied versus carried resources, active versus relaxed play, broadly reachable versus access-dependent activities, compact processing versus unstackable output, and rune/ammunition-free versus resource-limited training. They are not a 1–99 guide.

## Hard, preparation and optional classification

Hard requirements contain only real participation gates encoded by the record: skill levels and the Fossil Island/Bone Voyage access capability for ammonite crabs. Existing first-pack access gates for Canifis, Cam Torum and a currently accessible crashed-star layer remain hard.

Preparation contains the exact setup for the bounded action: carried tools, raw materials, bars, ores, ammunition, runes and ordinary free positions. A hammer, knife, tinderbox, lockpick, hair clip, axe or pickaxe is preparation rather than a permanent access gate. Known absence produces known missing preparation. UNKNOWN carriage remains UNKNOWN.

Recommended support is not promoted into a gate. Humidify, graceful, stamina potions, run restoration, teleport books, staffs, food, prayer, combat armour and maximum-throughput inventory layouts earn no default benefit. Existing optional rune-pickaxe efficiency profiles remain optional; the rune pickaxe can also satisfy the generic usable-pickaxe group when its use level is met.

## Generic usable-tool solution

Production schema v5 adds one catalog-local `ANY_OF` preparation composition primitive:

- a group has at least two named alternatives;
- each alternative is a conjunction of ordinary validated requirements;
- methods reference groups by stable ID;
- every declared group must be referenced;
- one verified alternative satisfies the group;
- all known-invalid alternatives make it missing preparation;
- no verified alternative plus any still-possible UNKNOWN alternative makes it UNKNOWN;
- group, alternative and leaf evaluation uses stable ID ordering.

Mining and Woodcutting define exact ordinary pickaxe/axe alternatives in data. Each alternative combines one pinned carried item ID with its data-defined Mining or Woodcutting use level. Bronze, iron, steel, black, mithril, adamant and rune variants are included through the relevant RFD ranges. Production Java does not know what a pickaxe or axe is. Stealing artefacts reuses the same primitive for lockpick or hair clip.

Strict validation rejects malformed/unused/duplicate/unresolved groups, fewer than two alternatives, duplicate alternative IDs or facts, safety predicates inside preparation groups, bad activity identifiers and item IDs outside the pinned RuneLite boundary.

## Resource-flow and combat semantics

Eight v2 methods use `ResourceFlow`:

- one grape plus one jug of water becomes one unfermented wine;
- copper plus tin becomes one bronze bar;
- one bronze bar becomes one shared stack of five bronze knives;
- five loose iron bars become one iron platebody;
- one regular log becomes one shared stack of fifteen arrow shafts;
- two existing stacks become one shared stack of fifteen headless arrows;
- one regular or oak log is consumed by a Firemaking resource sink.

Every ordinary flow item declares `ONE_SLOT_PER_UNIT` or `ONE_SHARED_STACK`, and direct occupied-position observations must agree with quantity. Returned hammers, knives and tinderboxes are setup items rather than consumed inputs. Schema v5 permits a nonempty input flow with no retained inventory output, which lets Firemaking release the consumed log position without inventing ashes in inventory. Output-only flows remain invalid. UNKNOWN output capacity remains unfavorable.

Magic and Ranged do not use an inventory-output flow. Their `consumes` predicates prove only the next cast, shot or bounded start. Fire Strike requires one mind, two air and three fire runes. The Telekinetic Theatre record requires one law and one air rune for one cast. Bronze darts and bone bolts require an exact current ammunition unit. No staff substitution, ammunition recovery, future supply, food, Prayer, safe monster behavior or death safety is inferred. Combat records carry `CAUTION`.

## Coverage, observable readiness and gaps

“Observable READY path” means the current observer can, for some account state, supply every fact needed by at least one method. It does not say that an arbitrary current account owns the setup or is at the start location.

| RFD skill target | Methods | At least one observable READY path | Main remaining observation/acquisition limits |
| --- | ---: | --- | --- |
| Agility 48 | 3 | Yes — Draynor/Varrock | Canifis access alias and health/failure support are not observed; run-energy setup is optional. |
| Cooking 70 | 3 | Yes — Hosidius Mess or carried wine inputs | Travel and full activity state are not observed; Atlas cannot acquire grapes, jugs or raw food. |
| Crafting 40 | 3 | Yes — carried glass inputs | Atlas cannot acquire/un-note sand, ash or glass, or dispose of output. |
| Firemaking 50 | 2 | Yes — carried tinderbox/log | A clear fire line and future logs are not observed or acquired. |
| Fishing 53 | 2 | Yes — carried fly-fishing setup | Catch output is random; sustained capacity and Tempoross execution are not modeled. |
| Fletching 10 | 2 | Yes — carried log/knife or shaft/feather stacks | No log, feather or shaft acquisition provider exists. |
| Herblore 25 | 6 | Yes — clean carried guam | Mixology order/hopper access remains unsupported; herb/secondary acquisition is absent. |
| Magic 59 | 3 | Yes — Arceuus Library | Current library task/maze state is not observed; runes cannot be acquired; combat sustainability is unknown. |
| Mining 50 | 3 | Yes — iron plus a carried usable ordinary pickaxe | Cam Torum access and crashed-star eligibility have no live provider; no ore-disposal route exists. |
| Ranged 40 | 2 | Yes — carried bronze darts | Bone Voyage capability alias, health and sustained ammunition are not observed; no ammo acquisition exists. |
| Smithing 40 | 3 | Yes — carried ores/bars and hammer | Mining, Superheat, shops, unnoting, alchemy and disposal are outside scope. |
| Thieving 53 | 3 | Yes — cake or fruit stall | Current health, run-energy sustainability, assignment state and teleport support are not established. |
| Woodcutting 36 | 3 | Yes — carried usable ordinary axe | Tree availability/location and later log handling are not observed; no axe acquisition provider exists. |

Remaining non-skill RFD gaps:

- no acquisition route for the 175 Quest-point target;
- no trustworthy provider for `capability.combat.rfd_no_prayer`;
- no automatic strategic actions for prerequisite quests that are not encoded RFD milestones;
- no quest-item, inventory-pressure, fight, boost or safety preflight for RFD chapters;
- manual Quest Helper handoff only, because the reviewed integration exposes no supported external start contract;
- no live goal selection, recommendation panel or sidebar wiring.

## Strategic scenarios

Production-backed tests prove the existing layers work across the broader catalog:

1. **Cooking deficit:** level-20 Mess meat pies receive `GOAL_PROGRESS` and become best actionable while unrelated families receive zero.
2. **Thieving deficit:** with Cooking satisfied, level-25 Hosidius fruit stalls become best actionable without Thieving-specific orchestration.
3. **Combat resources:** Fire Strike and bronze-dart training remain goal-relevant but are non-actionable with known missing resources and UNKNOWN with unknown inventory. Rune-free Arceuus Library can still win.
4. **Smithing/Fletching flow:** carried hammer/bar and knife/log setups make bounded processing READY; higher-tier methods with missing resources stay non-actionable even when their editorial efficiency is higher.
5. **Usable tool:** a valid carried tool/level alternative satisfies preparation; a carried rune axe below its use level does not; unknown carriage stays unresolved; reversed alternative order preserves results.
6. **All thirteen covered:** every encoded RFD skill fact maps to at least one production `activity` family.
7. **Target completion:** satisfying Cooking removes reward from every Cooking method and naturally shifts the decision to the still-missing Thieving family.
8. **Quest versus training:** a ready Pirate Pete handoff and ready Cooking training are both retained. Existing explicit shared score inputs select the quest deterministically, and reversing method order preserves candidate order and winner.

Scorer weights and `StrategicDecision` are unchanged. No RFD-specific method selection or per-skill `GoalContext` Java exists.

## Files changed

- Product and schema documentation: `README.md`, `ARCHITECTURE.md`, `DATA_SCHEMA.md` and this report.
- Generic production Java: `MethodDefinition`, `MethodDefinitionLoader`, `MethodEvaluator`, `SetupScoringInputs`, `PreparationFeasibility` and `ResourceFlow`.
- Production resources: the eight new per-skill catalogs, `catalogs.txt`, and the migrated data-defined tool requirements in `mining-v1.json`.
- Tests: `RfdSkillCoverageV2Test` plus focused updates to `ProductionHerbloreCatalogTest`, `ProductionSkillCoverageCatalogTest`, `RfdSkillCoverageTest` and `SkillCoverageReadinessTest`.

No `GoalContext`, goal definition, account observer, scorer, strategic decision, UI or plugin wiring file changed.

## Factual uncertainties

No unresolved item identity, encoded skill level, recipe quantity or spell cost was treated as verified. The remaining uncertainties concern live circumstances that the cited pages cannot establish for an exact account: current activity state, location availability, health and combat safety, random-output capacity, future input supply and sustained XP throughput. The methods therefore use bounded actions and editorial XP ranges, and keep these circumstances in assumptions, stop conditions, observation gaps or acquisition gaps. Their source metadata is reviewed at 2026-09-14 and must be re-reviewed if upstream game behavior changes.

## Sources

Each record carries its own reviewed source notes. Primary method selection came from current [UIM Cooking](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Cooking), [UIM Thieving](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Thieving), [UIM Magic](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Magic), [UIM Smithing](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Smithing), [Ironman Firemaking](https://oldschool.runescape.wiki/w/Ironman_Guide/Firemaking), [UIM Ranged](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Ranged), [UIM Fletching](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Fletching) and [UIM Woodcutting](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Woodcutting) guidance.

Activity and recipe claims were checked against [Hosidius Mess](https://oldschool.runescape.wiki/w/Hosidius_Mess), [jug of wine](https://oldschool.runescape.wiki/w/Jug_of_wine), [cake stall](https://oldschool.runescape.wiki/w/Cake_stall), [stealing artefacts](https://oldschool.runescape.wiki/w/Stealing_artefacts), [Arceuus Library](https://oldschool.runescape.wiki/w/Arceuus_Library), [Mage Training Arena](https://oldschool.runescape.wiki/w/Mage_Training_Arena), [Telekinetic Grab](https://oldschool.runescape.wiki/w/Telekinetic_Grab), [Fire Strike](https://oldschool.runescape.wiki/w/Fire_Strike), [Smithing tables](https://oldschool.runescape.wiki/w/Smithing/Experience_table), [Firemaking](https://oldschool.runescape.wiki/w/Firemaking), [Dorgeshuun crossbow](https://oldschool.runescape.wiki/w/Dorgeshuun_crossbow), [arrow shafts](https://oldschool.runescape.wiki/w/Arrow_shaft), [headless arrows](https://oldschool.runescape.wiki/w/Headless_arrow), [Woodcutting](https://oldschool.runescape.wiki/w/Woodcutting), [oak trees](https://oldschool.runescape.wiki/w/Oak_tree) and [teak trees](https://oldschool.runescape.wiki/w/Teak_tree).

Exact item identities were checked against pinned public [RuneLite 1.12.38 `gameval.ItemID`](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java) and are revalidated against that dependency during tests.

## Architecture impact and next milestone

The only new schema concept is generic preparation alternatives. The implementation is isolated to the immutable method model, strict loader, evaluator diagnostics, setup derivation and preparation classification. `GoalContext`, `GoalDefinitionLoader`, `RuneLiteAccountObserver`, `RecommendationDecision`, `StrategicDecision` and scorer weights do not change. `ResourceFlow` gains only input-only sink support. The milestone adds no class per skill and no game-item switch.

Recommended next milestone: **RFD observation and quest-preflight gap closure v1**. Add a small, reviewed slice that replaces capability aliases with trustworthy observable quest/access facts where the current RuneLite quest state already supports them, then model one RFD chapter's item/inventory preflight and manual Quest Helper handoff. Do not add acquisition routing, death storage, UI or another planner layer.

## Verification and measured growth

`./gradlew clean build` completed successfully with 187 tests across 24 suites, zero failures, zero errors and zero skipped tests. This includes existing Construction, Herblore, first coverage-pack, RFD goal and strategic suites plus the v2 production scenarios. `./gradlew javaSourceSize`, `git diff --check`, the repository privacy scan, JSON/source metadata audit and built-JAR inspection also passed. The JAR contains the fourteen indexed production method catalogs and contains no synthetic or test resources.

| Measure | Before | After | Delta |
| --- | ---: | ---: | ---: |
| Core Java files | 40 | 40 | 0 |
| Core Java lines | 4,094 | 4,272 | +178 |
| Core Java characters | 164,347 | 174,125 | +9,778 |
| Approximate core Java tokens | 41,087 | 43,532 | +2,445 |
| All Java files | 65 | 66 | +1 test file |
| All Java lines | 9,058 | 9,590 | +532 |
| Production resource files | 8 | 16 | +8 |
| Production resource bytes | 159,797 | 245,728 | +85,931 |
| Test resource bytes | 10,341 | 10,341 | 0 |

The resource increase is about 8.8 times the core-Java character increase. Core growth remains just under the milestone's preferred +2,500 approximate-token ceiling.

Ten largest Java files after the change, measured by characters:

1. `AccountStateFactsTest.java` — 23,386 characters, 378 lines
2. `MethodDefinitionLoader.java` — 21,748 characters, 381 lines
3. `ProductionConstructionCatalogTest.java` — 21,391 characters, 406 lines
4. `StrategicDecisionProductionTest.java` — 21,280 characters, 364 lines
5. `ProductionCapacityTest.java` — 20,662 characters, 358 lines
6. `RfdSkillCoverageV2Test.java` — 20,040 characters, 339 lines
7. `RfdSkillCoverageTest.java` — 15,708 characters, 248 lines
8. `HerbloreReadinessTest.java` — 13,939 characters, 257 lines
9. `SetupScoringInputsTest.java` — 13,755 characters, 262 lines
10. `ConstructionReadinessTest.java` — 13,405 characters, 263 lines

The principal growth concern is `MethodDefinitionLoader`, now the largest production Java file. Schema v5 was kept within the existing loader to avoid a parallel loading framework, but further schema features should first extract generic validation/parsing components or remain entirely data-only. No scorer, goal orchestration, observer or strategic-selection growth was needed.
