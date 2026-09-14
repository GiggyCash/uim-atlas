# RFD Skill Coverage Pack v1

Reviewed 2026-09-14. Eleven methods added; twenty production methods total. No new goal, method schema, scoring framework, live recommendation wiring or UI. No commit or push is part of this milestone.

## Coverage and selection

The [current RFD requirement list](https://oldschool.runescape.wiki/w/Recipe_for_Disaster) agrees with the thirteen aggregate skill targets in the existing goal resource. The pack leaves that resource unchanged.

| Coverage measure | Before | After |
| --- | ---: | ---: |
| Distinct RFD skills with a production training record below the target | 1 | 5 |
| RFD skills without any production training record | 12 | 8 |
| Production training methods, including non-RFD Construction | 9 | 20 |
| RFD skills with at least one setup expressible through current live observations | 1 | 4 |

Before: Herblore 25 was covered. Construction has production methods but is not an RFD skill target. After: Agility 48, Crafting 40, Fishing 53 and Mining 50 join Herblore.

**Remaining uncovered requirements:** Cooking 70, Thieving 53, Magic 59, Smithing 40, Firemaking 50, Ranged 40, Fletching 10, Woodcutting 36. Boostable targets retain the goal's existing conservative unboosted interpretation.

Catalog coverage is not complete level-range coverage or live readiness. Fishing below 20 and Mining below 10 have no matching records; iron begins at 15. Unsupported tool/access facts can prevent otherwise relevant records becoming actionable. Every Mining record requires a currently unsupported usable-pickaxe capability. Therefore the immediately observable skill-family coverage is four, including Herblore, while the domain catalog coverage is five. Nothing is selected live yet.

The four families were chosen for complementary UIM decisions:

- **Mining:** active iron, idle stars and lower-intensity calcified rocks contrast attention, dynamic availability and resource pressure. They need no dangerous storage strategy. [UIM Mining guide](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Mining).
- **Fishing:** a small carried lure setup competes with a larger minigame loadout. The guide prefers fly fishing to Barbarian Fishing before 58, beyond the RFD target, so Barbarian Fishing was not included. [UIM Fishing guide](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Fishing).
- **Agility:** rooftop laps remain useful with a full inventory and contrast unrestricted access with Canifis access and fall risk. [UIM Agility guide](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Agility).
- **Crafting:** glassmaking and two glassblowing tiers provide real carried-material transformations before 40. The guide's shopping, unnoting and death-storage loops are outside these bounded records. Early quest rewards can still be preferable, but this pack adds no new quest graph. [UIM Crafting guide](https://oldschool.runescape.wiki/w/Ultimate_Ironman_Guide/Crafting).

Cooking was considered but not forced into deterministic fish-output flows: burning and wine fermentation would need carefully scoped treatment. Combat methods and death-storage-dependent routes were excluded. Al Kharid rooftop was omitted because its page discourages it without energy support; it was not added merely to raise the count. [Al Kharid course](https://oldschool.runescape.wiki/w/Al_Kharid_Rooftop_Course).

## Exact methods and readiness contracts

All levels are real, unboosted levels. Required tools/materials are preparation, not hard blockers. Missing preparation cannot be resolved merely by knowing its deficit. No preparation provider is installed.

| Production ID suffix | Hard requirements | Preparation and inventory | Favored state; limiting assumption |
| --- | --- | --- | --- |
| `mining.iron_mount_karuulm` | Mining 15 | Verified usable carried/equipped pickaxe; 1 free slot | Compact active start at surface iron triangle; tool proof currently unsupported |
| `mining.calcified_cam_torum` | Mining 41; verified mine access reached during Perilous Moons | Usable pickaxe; 1 genuinely free slot | Lower attention with bone-shard resource value; access/tool proofs unsupported |
| `mining.shooting_star` | Mining 10; present, accessible non-Wilderness star with eligible current layer | Usable pickaxe; 1 free slot | Idle session with verified star; dynamic eligibility unsupported and expires after 30 seconds |
| `fishing.fly_barbarian_village` | Fishing 20 | Fly rod 309; at least 1 feather 314; 1 free slot | Rod/feathers already carried; random catch and subsequent handling are outside the record |
| `fishing.tempoross_mass` | Fishing 35 | Harpoon 311, rope 954, hammer 2347, two filled buckets 1929; 7 free slots | Ordinary group loadout already carried; this scopes a seven-fish opening, not the universal participation minimum |
| `agility.draynor_rooftop` | Agility 1 | Known inventory, 0 free slots required | Minimal setup; sustained pace/weight/run energy unmeasured |
| `agility.varrock_rooftop` | Agility 30 | Known inventory, 0 free slots required | Inventory-light training; falls cause damage, with CAUTION classification |
| `agility.canifis_rooftop` | Agility 40; Priest in Peril completion capability | Known inventory, 0 free slots required | Access already verified; capability alias unsupported, falls retain CAUTION |
| `crafting.molten_glass` | Crafting 1 | One loose sand bucket 1783 and soda ash 1781; 0 extra free slots | Carried raw materials at the public Edgeville furnace; no acquisition or travel feasibility claim |
| `crafting.oil_lamp` | Crafting 12 | Glassblowing pipe 1785 and loose glass 1775; 0 extra slots | Carried glass before the vial tier; retains the produced lamp, no disposal assumption |
| `crafting.vial` | Crafting 33 | Glassblowing pipe 1785 and loose glass 1775; 0 extra slots | Higher pre-40 glassblowing tier; empty vials do not prove filled potion inputs |

All IDs have the `method.` prefix. All three Mining methods keep better tools optional: `capability.tool.usable_pickaxe` describes actual usable possession, including level, charges and unlocks, without demanding a particular upgrade. A rune pickaxe is not silently interpreted as another tool. This avoids inventing an OR predicate or an item registry in Java, at the cost of an explicit observation gap.

Canifis uses the established method-schema capability convention. Although normalized quest observations exist, method schema v3 does not accept numeric quest facts and no capability alias provider exists. We did not change that schema to make this record appear ready. Cam Torum access requires reaching the access stage, not just any started Perilous Moons state. No capability is synthesized from its declaration.

Rooftop run support and recovery support are optional metadata, not prerequisites. Unknown support earns no bonus. Food and graceful do not become universal entry requirements; readiness does not certify current health or an indefinitely sustainable session. Optional marks of grace are not a required inventory output or guaranteed storage unlock.

## Capacity, flow and efficiency

The three Crafting records use unchanged schema v4:

- One sand bucket + one soda ash -> one molten glass + one empty bucket. Two observed input positions become two output positions.
- One molten glass -> one empty oil lamp (4525).
- One molten glass -> one empty vial (229).

All flow resources explicitly declare `ONE_SLOT_PER_UNIT`. Quantity must agree with directly observed occupied positions before capacity is trusted. A purported stack of 500 loose glass in one position makes the flow UNKNOWN. Each flow can fit a completely full inventory containing the required loose inputs because it reuses their actual positions. No quantity is equated to slots without that declaration and observation check.

Gathering and Agility remain schema v3 without resource flows. Fly fishing consumes feathers but has mixed/random catches; it is not represented as a deterministic fish recipe. Mining has no bounded input-to-output transformation. The ordinary free-slot checks are honest starting limits; they never imply future disposal. Calcified rocks require a free slot even when shards already stack. Mining/fly-fishing records pause when capacity fills. Reward claiming, secondary XP, food cooking, star finding and subsequent resource use are not simulated.

There are eleven ordinary verified-setup profiles using the existing Herblore baseline convention, and three optional rune-pickaxe profiles. The ordinary profile is an explicit scoring input for a verified runnable setup; the existing composition has no implicit efficiency fallback. These are coarse editorial values, not invented measured rates. The optional profiles describe a real tool-cycle distinction and require current exact rune possession plus level 41. UNKNOWN, historical or absent optional possession cannot select them. No tick-manipulation or graceful bonus profile was added.

**No new XP/hour ranges are published.** The reviewed guide rates require sustained disposal, replenishment, high levels, run support or full activity cycles that these scoped starts do not verify. Single-action recipes and idle-course metadata cannot honestly establish those assumptions. No midpoint or zero-rate fallback is invented. Working-capacity arrays are empty; the pack does not invent comfortable batch targets.

Setup and transition estimates retain neutral fixed metadata. Existing `SetupScoringInputs` derives actual deficits and free-slot pressure. Attention and efficiency remain editorial, and the existing strategic comparison still omits method efficiency: shared score is `3G - S - 2T - 2D - 4R - 3U`. Equal shared scores use the existing kind/ID tie rule; the pack cannot promise that the fastest rooftop wins. Binary direct relevance, scorer weights and all nine-factor diagnostics remain unchanged.

## Source review

Current Wiki HTML was read directly on the review date because some search results contained older revisions. In particular, Draynor starts at 1, not the older guide's 10; the directly fetched UIM guide also reflects the update. Calcified-deposit handling differed between cached and current guidance, so no disposal instruction was imported.

Each JSON record contains its own review date, source URLs and scoped notes. Exact IDs were checked against the pinned public top-level [RuneLite 1.12.38 gameval ItemID](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java) and validated against that dependency in offline tests. No nested certificate constants, notes or substitutions are inferred.

Additional factual sources:

- [Mount Karuulm mine](https://oldschool.runescape.wiki/w/Mount_Karuulm_mine), [iron rocks](https://oldschool.runescape.wiki/w/Iron_rocks), [Mining tool table](https://oldschool.runescape.wiki/w/Mining), [calcified rocks](https://oldschool.runescape.wiki/w/Calcified_rocks), [Cam Torum mine](https://oldschool.runescape.wiki/w/Cam_Torum_mine), [Shooting Stars](https://oldschool.runescape.wiki/w/Shooting_Stars).
- [Fishing](https://oldschool.runescape.wiki/w/Fishing), [Tempoross](https://oldschool.runescape.wiki/w/Tempoross), [Tempoross strategy](https://oldschool.runescape.wiki/w/Tempoross/Strategies). The latter supports the ordinary tool quantities and seven-fish opening scope; it does not establish universal seven-slot participation.
- [Draynor](https://oldschool.runescape.wiki/w/Draynor_Village_Rooftop_Course), [Varrock](https://oldschool.runescape.wiki/w/Varrock_Rooftop_Course), [Canifis](https://oldschool.runescape.wiki/w/Canifis_Rooftop_Course) for levels, start landmarks and failures.
- [Molten glass](https://oldschool.runescape.wiki/w/Molten_glass), [empty oil lamp](https://oldschool.runescape.wiki/w/Empty_oil_lamp), [vial](https://oldschool.runescape.wiki/w/Vial), [Edgeville](https://oldschool.runescape.wiki/w/Edgeville). Edgeville furnace has no Varrock Diary gate. The UIM Crafting guide's furnace loop explicitly retains empty buckets.

Remaining uncertainty concerns actual account setup, travel, sustained speed and dynamic access, not fabricated quantities or a claimed safe storage strategy. LOW/CAUTION classifications are coarse; neither guarantees item safety or handles death-storage compatibility.

## Strategic evidence

Production-backed scenarios cover all four newly represented families without candidate-generation code:

1. A verified deficit independently makes each family relevant; an explicitly ready member can win. Construction and satisfied/unrelated skills receive zero RFD progress.
2. At Fishing 40 with rod/feathers, four missing Tempoross setup predicates remain diagnostic. READY fly fishing wins. Adding the harpoon, rope, hammer and two water buckets makes Tempoross win when fly fishing has the same explicit 0.05 uncertainty estimate throughout. The nominal efficiency contrast is 0.60 vs 0.50, not a claimed XP comparison.
3. Fishing 53 removes its reward, allowing carried guam cleaning for Herblore 3 to win. Each other newly covered skill is also tested at its RFD target.
4. Ready Pirate Pete and Fishing compete: quest score 3 beats fly fishing 2.7 with explicit 0.1 training uncertainty; explicit quest transition 0.2 makes its score 2.6, below ready training at 3. No default quest estimates are added.
5. Missing Fishing, Crafting and Herblore produce three relevant families. Explicit estimates favor the ready fly setup; reversing input order preserves every candidate score/order and the winner.
6. Unknown inventory prevents an actionable winner despite relevance 1. Known absence remains a measured preparation deficit, not UNKNOWN.
7. A higher-scoring vial action with unknown output occupancy remains diagnostic while a ready oil-lamp action wins. Full-inventory flows, the returned bucket, feather stacks, optional profiles and stale star observations have dedicated checks.

Test capability proofs are synthetic and clearly labelled; they do not install live observers.

## Architecture and validation

`ProductionMethodCatalog` is the sole new core Java class. It reads a bundled plain-text index, invokes the existing strict parser for each catalog, rejects missing/invalid/duplicate paths and cross-file method IDs, and returns immutable ID-sorted records. Tests verify that the index exactly matches packaged catalogs. It does not load at plugin startup.

`MethodDefinitionLoader`, `GoalDefinitionLoader`, `RuneLiteAccountObserver`, `ResourceFlow`, `StrategicDecision`, `GoalContext`, scorer weights and Construction/Herblore resources are unchanged. No watchpoint class grew. No game-method names, selected-skill branches or training-method constants were added to production Java. The cost of breadth is principally data; unsupported observations remain a material follow-up boundary.

Changed files: four skill JSON catalogs, `catalogs.txt`, the one generic loader, three new test classes, the existing JAR inspection test, this report, and README/architecture/schema status documentation. Synthetic resource files are unchanged and excluded from the JAR.

The clean build runs the full test suite, source-size report and artifact checks. Validation covers malformed/unknown fields, duplicate IDs, impossible skill/slot levels, quantity and XP ranges, missing/invalid review metadata, invalid canonical items, unresolved profile facts and malformed flows. The existing Construction, Herblore, goal and strategic suites remain in the run. Final measured results are recorded below.

## Recommended next milestone

**RFD Cooking and Thieving Coverage v1:** add 6–8 reviewed UIM methods for Cooking 70 and Thieving 53 through the existing goal/planner, with explicit random-outcome and preparation boundaries. Keep this a breadth slice; add no goal, preflight framework, acquisition routing or UI. Tool/access observation gaps remain visible separately rather than being concealed in coverage counts.

## Final measured results

`./gradlew clean build` succeeded. All **177 tests** passed, with zero failures, errors or skips. This includes strict catalog loading, the complete existing suites and actual JAR inspection. `javaSourceSize` ran as part of `check`. `git diff --check`, a changed-file privacy scan, exact pinned item-identity verification and production-resource inspection passed. No commit or push was performed.

| Metric | Before | After | Delta |
| --- | ---: | ---: | ---: |
| Core Java files | 39 | 40 | +1 |
| Core Java lines | 4,029 | 4,094 | +65 |
| Core Java characters | 161,842 | 164,347 | +2,505 |
| Approximate core Java tokens (characters / 4) | 40,461 | 41,087 | +626 |
| All Java files, including tests | 61 | 65 | +4 |
| All Java lines | 8,364 | 9,058 | +694 |
| Approximate all-Java tokens | 96,933 | 106,685 | +9,752 |
| Main resource files | 3 | 8 | +5 |
| Main resource bytes | 89,099 | 159,797 | +70,698 |
| Test resource bytes | 10,341 | 10,341 | 0 |
| All resource bytes | 99,440 | 170,138 | +70,698 |

Main resources now contain six method catalogs, one catalog index and the unchanged RFD goal. The JAR contains all eight and no synthetic resources or compiled test classes. Resource bytes exclude documentation and Java. Token estimates are independently rounded character counts, not tokenizer measurements.

The ten largest Java files, ranked by characters as in `javaSourceSize`:

| File | Characters | Lines |
| --- | ---: | ---: |
| `src/test/java/com/uimatlas/state/AccountStateFactsTest.java` | 23,386 | 378 |
| `src/test/java/com/uimatlas/data/ProductionConstructionCatalogTest.java` | 21,391 | 406 |
| `src/test/java/com/uimatlas/data/StrategicDecisionProductionTest.java` | 21,280 | 364 |
| `src/test/java/com/uimatlas/data/ProductionCapacityTest.java` | 20,662 | 358 |
| `src/main/java/com/uimatlas/data/MethodDefinitionLoader.java` | 16,961 | 305 |
| `src/test/java/com/uimatlas/data/RfdSkillCoverageTest.java` | 15,896 | 249 |
| `src/test/java/com/uimatlas/data/HerbloreReadinessTest.java` | 13,939 | 257 |
| `src/test/java/com/uimatlas/recommendation/SetupScoringInputsTest.java` | 13,755 | 262 |
| `src/test/java/com/uimatlas/data/ConstructionReadinessTest.java` | 13,405 | 263 |
| `src/test/java/com/uimatlas/state/RuneLiteAccountObserverTest.java` | 12,914 | 263 |

Core growth is comfortably below the +2,500-token milestone preference and far below the repository warning thresholds. The largest core parser did not change. Most Java growth is scenario/validation testing rather than production orchestration.
