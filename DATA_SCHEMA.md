# UIM Atlas Data Schema

## Goal

Represent RuneScape/UIM knowledge as validated resource data so adding game coverage does not require proportional Java growth.

The exact serialization format can be chosen during implementation, but bundled versioned JSON is the preferred starting point because it is straightforward to validate, diff, generate, and load in a RuneLite plugin.

## Implemented foundation: synthetic method schema v1

This section describes the implemented format. Later sections remain conceptual guidance for future game data, not an alternative accepted format.

`MethodDefinitionLoader.loadSynthetic(Reader)` accepts strict JSON with these required root fields:

- `schemaVersion`: integer `1`
- `dataKind`: exactly `SYNTHETIC_TEST_ONLY`
- `facts`: objects containing a unique stable `id`
- `methods`: a nonempty array of method definitions

The synthetic catalog is `src/test/resources/uimatlas/methods/synthetic-methods.json`, containing three invented exercises. It is excluded from the plugin JAR. Method IDs must begin with `synthetic.method.` and display names with `Synthetic `. The loader is not wired into plugin startup. Production catalogs use the separate v3/v4/v5 entry point below; changing the data-kind label alone cannot enable them.

Stable IDs use lowercase letters, digits, underscores and dot-separated segments: `[a-z][a-z0-9_]*(\.[a-z0-9_]+)+`. Every requirement fact and produced resource ID must resolve in the catalog's `facts` declarations. These declarations validate references, not observation availability. Synthetic v1 facts contain only `id`. Production `inventory.item.<itemId>.usable_slots` facts additionally require `capacitySemantics: FREE_PLUS_OBSERVED_EXACT_ITEM_SLOTS`; that field is forbidden on every other fact. Unknown fact observations remain unknown. Item and goal loaders receive caller-supplied canonical item/quest boundaries rather than embedding registries in Java.

Every method requires:

| Field | Shape / units |
| --- | --- |
| `id`, `displayName` | Stable ID and synthetic display label |
| `category`, `activity` | Nonblank category and skill/activity metadata; no game enum |
| `start` | Nonblank `location`, `contact`, `instruction` labels; no route execution |
| `hardRequirements` | Array of numeric fact predicates; all must pass |
| `preparation` | Array of predicates describing actual setup needs |
| `freeInventorySlots` | `AT_LEAST` integer predicate for `inventory.free_slots` |
| `setupItems` | Positive integer `AT_LEAST` predicates for explicitly scoped possession facts |
| `consumes` | Positive integer `AT_LEAST` predicates for inputs needed to begin one declared batch; description states the batch |
| `produces` | Objects with declared `resourceId`, positive numeric `quantity`, nonblank `basis` (for example, per synthetic batch) |
| `stopConditions` | Nonempty array of predicates; metadata for future focus handling, not start gates |
| `style` | `attention` in [0,1], nonblank `playStyle`, boolean `tickManipulation` |
| `xpRate` | Nonnegative `minimum` and `maximum` XP/hour, maximum >= minimum, nonblank `assumptions` |
| `costs` | `storageUnlockValue` and `inventoryDisruption` in [0,1]; nonnegative `setupMinutes`, `transitionMinutes`; nonblank `assumptions` |
| `danger` | `LOW`, `CAUTION`, `HIGH`, or `UNKNOWN`; generic classification, no safety guarantee |
| `reason` | Nonblank short explanation metadata |

Every predicate requires `fact`, `comparison` (`AT_LEAST`, `EQUAL`, `AT_MOST`), nonnegative finite `target`, nonblank `description`, boolean `allowLastObserved`, integer `maxAgeSeconds` in [0, 2147483647], and boolean `safetyRelevant`. Boolean facts use 0/1; quantities, levels and XP use numeric facts. `AccountStateFacts` explicitly maps supported normalized quest states to numeric completion/started facts under the contract below. Individual requirement lists remain conjunctions; goal milestone dependencies provide the first validated graph structure.

Preparation, free slots, setup items and consumed inputs form one preparation group. Repeated fact IDs within that group are rejected, preventing accidental double use or undercounting of one supply. Definitions must express the combined need under one fact until richer resource accounting is justified. Safety-relevant predicates must be hard requirements and cannot accept last-observed confidence.

The evaluator consumes `FactLookup` (or the existing `Map<String, Observation<Double>>` entry point) and an explicit evaluation time. Callers must supply a coherent snapshot, retain the source/time/confidence of the underlying observation, and use separate fact IDs for distinct possession/storage scopes. An observed absence may be zero; an unobserved quantity must not be zero. The account adapter below supports observed skills, quests, Quest points, capabilities, containers and inventory/equipment facts without inferring unobserved storage.

Freshness is checked independently of confidence: future timestamps, expired observations, missing observations, explicit unknown confidence, and nonfinite/negative observed values all produce `UNKNOWN`. Age exactly at the declared limit is accepted. `LAST_OBSERVED` needs explicit permission and must still pass the age check; it is never sufficient for a safety-relevant predicate. The current observation model has no user-confirmed/conflicted state; future adapters must preserve these conservatively rather than upgrading them to verified.

Method evaluation precedence is `BLOCKED` (known failed hard gate), then `UNKNOWN`, then `NEEDS_PREP`, then `AVAILABLE`. Missing hard gates, missing prep and unknown predicates remain in separate immutable diagnostic lists even when another status takes precedence. Only known failed preparation predicates enter preparation output. Unknown danger independently yields `UNKNOWN`. Method stop conditions remain metadata; goal completion and candidate filtering are handled separately by Goal Context v1.

Validation rejects missing/extra/duplicate JSON fields, nulls, wrong types, unsupported versions/enums, duplicate IDs, unresolved fact references, invalid numeric ranges, empty stop lists and weakened safety policies with field-path errors. Parsing has a depth limit of 32. Validation uses RuneLite's existing Gson dependency; there is no additional runtime library, reflection-based domain deserialization, or separate JSON Schema dependency. Domain constructors copy collections; resource validation is owned by the loader.

### Production Construction catalog v1 (schema v3)

`src/main/resources/uimatlas/methods/construction-v1.json` contains exactly three curated records: novice oak Mahogany Homes contracts, adept teak contracts, and limestone attack stones using an existing flamtaer bag. Catalog revision v1 is distinct from serialization version `3`. Data ships in the JAR; there are no runtime fetches, startup loading, live candidates or UI changes.

`MethodDefinitionLoader.loadProduction(Reader, Set<Integer> canonicalItemIds)` accepts supported production schema versions 3, 4 and 5 with `dataKind: PRODUCTION`. It preserves strict v1 validation, removes method-level `xpRate` for production, and requires these additional method fields. Production v2 remains rejected. Construction remains schema v3; Herblore and the first transformation catalogs use v4; alternative-group catalogs and resource sinks use v5. Synthetic v1 keeps its original fields and behavior:

| Field | Contract |
| --- | --- |
| `optionalSetup` | Array of ordinary predicates, retained as metadata only. Neither evaluation nor setup scoring reads these. Missing/unknown optional facts cannot create preparation, uncertainty or blockers. No safety-relevant predicates, duplicates, or overlap with required facts. |
| `workingCapacity` | Array of current-verification predicates describing a working batch assumption. Diagnostic only, never an eligibility gate or setup-scoring input. Empty means no working assumption is encoded. |
| `efficiencyProfiles` | Array of conditional profiles described below. Empty means efficiency is unresolved. |
| `sources` | Nonempty array of `{url, reviewedAt, notes}` objects. URL must be absolute HTTPS without user information; review date must be a real ISO `YYYY-MM-DD` date; notes must be nonblank. Every object rejects unknown/missing fields. Review date means human/development source review, not source publication date or account observation freshness. |

Loaded definitions retain immutable `dataKind`, `optionalSetup`, and `sources`. The existing convenience constructor and synthetic loader explicitly produce synthetic definitions with empty curation metadata. The evaluator and scorer remain unchanged and contain no Construction knowledge. `MethodEfficiency` evaluates working/profile diagnostics separately and explicitly composes an efficiency input for the existing scorer. Production `MethodDefinition.getXpRate()` is null (legacy synthetic metadata only); production rates reside exclusively in profile `Optional<XpRate>` values.

Production IDs start with `method.`. All production string values reject the word markers `synthetic`, `test`, and `fixture` (case insensitive, including underscore-separated labels). Facts must use the supported skill, slot, exact-item namespaces, generic boolean `capability.*` IDs, or the capacity/container namespaces below. Capability declarations define a contract, not an observation or inferred unlock. Unknown fact references are rejected. Real skill level predicates require integer targets in [1,99], inventory slot targets integers in [0,28], and capability targets 0 or 1; these shared namespace checks also protect synthetic fixtures using real namespaces. Profile XP/hour remains a finite nonnegative range with maximum >= minimum and explicit assumptions. No midpoint is substituted.

Item-ID existence has an explicit **caller-supplied canonical boundary**. Every item fact declaration must use a canonical nonnegative decimal integer ID accepted by that set. There is no permissive production overload. Live loading reads `uimatlas/items/catalog-item-ids-1.12.38.txt`, an exact bundled boundary for the item facts used by the current production catalogs. Offline build tests require that set to equal the catalog facts and verify every entry against public top-level constants from the pinned RuneLite `gameval.ItemID` dependency. This validates existence, not item substitutability, acquisition, noted equivalence or container contents. No method-specific item constants or registry is added to Java, and runtime reflection is unnecessary.

The JAR test loads every packaged method resource through the production loader and requires the exact intended catalog. It also compares all compiled test-class paths against JAR entries and rejects synthetic resource names. Consequently placing a fixture in the production method directory fails the build rather than silently packaging advice. `test` depends on `jar` so ordinary test runs exercise the artifact too.

#### Scope and assumptions of the three records

- Real Construction baselines are 20, 50 and 59 respectively. Boost-dependent strategies are excluded deliberately. The first two records only check basic tools, a loose plank starting minimum and a steel-bar reserve; the reserve is preparation, not an assertion that every contract consumes a bar. They do not claim to fund a complete assigned contract. No axe, sawmill access or particular teleport is a gate for working with already-carried planks. Replenishment routes require future preflight.
- Ordinary hammer/saw, loose materials, and exact Morytania legs 3 possession are preparation. Alternative tools, tier 4 legs, noted items and nested supplies are not silently substituted. Absence means `NEEDS_PREP`, not a failed level/unlock gate.
- Limestone is scoped to the documented shop loop with an **already-carried** flamtaer bag. Bag absence is a hard blocker; unknown, historical or expired bag state is UNKNOWN. Dangerous bag acquisition is excluded, not emitted as preparation. The danger classification is CAUTION; it does not assess death-storage compatibility or guarantee safe travel. No death, retrieval, combat or disposal instructions are generated.
- A limestone build consumes ten loose bricks and produces 200 base Construction XP. The guide's 20 brick positions describe collection capacity, not 20 additional empty positions while already carrying bricks. The numeric `inventory.item.3420.usable_slots >= 20` preparation predicate measures those positions from ordinary inventory, without assuming container contents. Five empty slots plus ten carried brick positions gives 15 usable positions and a five-position shortfall; twenty carried bricks can satisfy it with zero empty slots. The next shop cycle's budget and usable house teleport are also explicit preparation checks. Bag presence never verifies bag contents.
- Novice now retains only the sack-equipped 60,000–72,000/hour benchmark (balloon/ring examples), behind verified profile assumptions; the former combined 50,000–72,000 range mixed no-sack and sack setups. Tick manipulation remains optional for participation. Adept retains the conditional 75,000–85,000 casual-effort range. Limestone retains 70,000–80,000 behind a full-cycle profile. No rate is returned for an unresolved profile, ordinary batch, or helper-only batch. These are conditional source estimates, not measured account forecasts.
- Attention values are coarse editorial style hints, not measured fractions of active time. Fixed setup/transition minutes and inventory-disruption hints are explicitly neutral/unestimated; zero does not claim zero real cost. Storage-unlock value stays zero: Construction alone earns no arbitrary permanent-storage bonus. No POH/STASH milestone table is introduced.
- Stops are supply **recheck** boundaries, not claims of total exhaustion: zero loose planks, or fewer than ten loose bricks. Nested materials may still exist. No 1–99 route, fixed goal or automatic completion handling is added.

#### Supported and unsupported required facts

All three methods need `capability.poh.owned`. A positive logged-in RuneLite server house-location varbit now supplies verified ownership through `AccountState` and `AccountStateFacts`. Zero is not documented by RuneLite as a trustworthy non-ownership value, so it remains UNKNOWN rather than becoming false. Limestone additionally needs these facts; all remain UNKNOWN with the current adapter:

| Fact | Role / meaning |
| --- | --- |
| `capability.poh.games_room` | Hard: accessible games room stone space |
| `capability.diary.morytania_hard` | Hard: completed hard Morytania Diary |
| `capability.razmire.serum_208` | Hard: permanently restored Razmire |
| `capability.travel.house_teleport` | Preparation: usable teleport and supplies for the loop |
| `capability.supplies.limestone_restock_budget` | Preparation: coins for the next 80-brick purchase cycle at actual shop prices |

Requirement descriptions carry these semantics in the resource itself. Optional capabilities are also unsupported and ignored by eligibility; profiles can require those same facts without turning them into method gates. Every predicate uses an explicit 300-second maximum age and current verification; this is a conservative catalog policy, not a Wiki claim. Production tests supply explicitly constructed observations only to exercise evaluator branches; they do not install an observation provider or fabricate a live winner.

### Capacity and efficiency contract

**Hard feasibility != working capacity != efficient capacity.**

- **Runnable now:** `AVAILABLE` means all encoded hard and preparation predicates passed, including the declared starting stock and genuinely required capacity. `hardRequirements` remain blockers; `freeInventorySlots` and capacity predicates in `preparation` remain preparation constraints. This is readiness for the scoped start, not a guarantee of completing a contract or replenishing supplies.
- **Workable batch:** `workingCapacity` describes a useful ordinary batch assumption and returns each predicate's satisfied/missing/unknown diagnostic. Both Mahogany Homes records use 14 ordinary plank positions as an editorial working target, based on the guide's 14/20-position examples. Missing this target does not make smaller batches impossible. A verified helper profile can establish a different efficiency assumption, but it does not erase the separate ordinary working-capacity diagnostic. Capacity describes room, not materials already obtained.
- **Trusted efficiency:** every predicate in a profile must pass current verification and freshness, and the method must be `AVAILABLE`, before that profile can be selected. A profile can describe better payload without claiming XP/hour. Profiles do not alter evaluator status, preparation output, setup costs, transition costs or inventory disruption. For `NEEDS_PREP`, matches remain diagnostic but selection, derived efficiency and trusted XP are absent; no speculative preparation is assumed.

Each efficiency profile requires:

| Field | Contract |
| --- | --- |
| `id` | Stable ID, unique within the method |
| `priority` | Nonnegative integer, unique within the method; larger wins |
| `requirements` | Nonempty conjunction of ordinary predicates; unique facts within the profile; `allowLastObserved: false`, `safetyRelevant: false` |
| `efficiency` | Explicit normalized [0,1] `METHOD_EFFICIENCY` input, with its basis explained in `notes` |
| `notes` | Nonblank source/assumption explanation; references the method's curation sources |
| `xpRate` | Optional `{minimum, maximum, assumptions}` using the existing validated range shape; omission means no rate estimate, never zero XP |

Safety requirements belong in method hard gates. Working predicates also require current verification and unique facts. A fact may recur across working/profile groups or ordinary requirements because these are independent assessments, not additional consumption. Profile requirements need not repeat the method's hard/preparation predicates: selection already requires the method to be ready. Unknown/missing/extra/null fields, duplicate profile IDs/priorities, fractional priorities, empty requirement lists, malformed references, invalid ranges and weakened freshness policies are rejected.

Selection is deterministic: evaluate profiles by descending explicit priority; select the first fully verified profile only when the method is ready. Overlap with different priorities is intentional (a benchmark may also satisfy a helper profile). Equal priorities are rejected even if their conditions appear disjoint, so JSON insertion order never resolves ambiguity. Checks are sorted by fact ID. An unknown higher profile does not prevent selecting an independently verified lower profile. No verified profile means an explicit empty selection/input, not a neutral efficiency value.

`MethodEfficiency.derive(method, facts, now)` caches each requested observation for one call, uses the same snapshot and instant for eligibility and profiles, and returns immutable evaluation, working checks, ordered profile matches, and optional selected profile. Every check retains its predicate, original observation (including rejected stale values), and result; it does not short-circuit away unknowns when another predicate is missing. A selected profile plus its matching checks and notes explains the efficiency input. `getTrustedXpRate()` exposes a range only from that selected profile. Inspecting raw definition metadata does not establish a trusted rate.

`result.withExplicitFactors(...)` requires exactly goal progress, storage value, risk and uncertainty. It returns those four plus the selected profile's explicit `METHOD_EFFICIENCY`, or no map when unresolved. Supplying an existing efficiency value is an error, even when identical. Feed that optional five-factor map to `SetupScoringInputs.Result.withExplicitFactors(...)`, then to the unchanged `MethodScorer` / `MethodRanker`. Retain both derivation results for explanations; never replace an absent map with defaults. Existing callers with explicit efficiency inputs and synthetic v1 methods remain unchanged.

The production scores 0.25 (14 loose planks), 0.5 (full matching sack) and 0.75 (complete benchmark setup) are **uncalibrated editorial batching/efficiency inputs**, not measured throughput, XP conversion or universal comparisons across skills. Priorities, not these scores, select profiles. Five free ordinary slots plus a verified carried sack containing 28 matching planks can select the helper profile without awarding a published XP range. Ownership, spare container space, wrong-type planks, unknown contents, stale observations or missing facts cannot substitute for that payload.

Benchmark profiles require additional verified setup predicates. The source does not establish a universal numerical inventory minimum for its Mahogany Homes XP examples: the 14-plank check is an editorial lower check, and unsupported `capability.setup.oak_collection_benchmark` / `capability.setup.teak_collection_benchmark` explicitly require the full described collection, transport, supplies, payload and effort assumptions. Slot count alone must never synthesize either capability. Noted supplies, log baskets and transport can improve other future profiles but are not participation requirements. The limestone rate additionally requires a verified 60-brick bag payload, 20 loose bricks and run support; the last is a conservative profile assumption, optional for the method itself.

#### Capacity and future container fact IDs

| Fact | Meaning |
| --- | --- |
| `inventory.item.<itemId>.usable_slots` | Ordinary free slots plus occupied positions holding that exact item ID. Production declaration requires `capacitySemantics: FREE_PLUS_OBSERVED_EXACT_ITEM_SLOTS`. Only inventory; bounded integer [0,28]. Counts each observed slot entry once, never its quantity. A stack of 500 contributes one position; ten distinct occupied positions contribute ten. Does not infer stackability, noted equivalence, future freed slots, other items' disposability or nested capacity. The data opt-in asserts that positions already occupied by this exact payload may count toward the declared loop capacity. |
| `container.<id>.owned` | Verified ownership boolean 0/1; does not prove current possession, accessibility, contents or usable capacity. |
| `container.<id>.contents.<itemId>.quantity` | Nonnegative integer exact contents of the identified container; never the count of carried container items or a default of zero. Accessibility/direct usability must be independently established where needed. |
| `container.<id>.free_capacity` | Nonnegative integer currently spare units in a container's declared shared capacity pool. Not current payload, a maximum-capacity constant, or a guarantee that arbitrary items can enter it. Heterogeneous capacity needs a future schema extension. |

Container `<id>` is one lowercase stable segment `[a-z][a-z0-9_]*`; exact item IDs use the existing canonical boundary, including contents-only references. `AccountState` holds a generic map of `ContainerState`, whose ownership, typed contents and free capacity are independent observations. `AccountStateFacts` projects only known entries and preserves each field's source, time and confidence. A missing container, field or typed content entry is UNKNOWN, never zero.

The first provider is the carried plank sack. Current carriage remains the ordinary exact item fact `inventory.item.24882.quantity`. When that inventory item is present, the observer can establish ownership=true and reads the [RuneLite 1.12.38 named server varbits](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java) `PLANK_SACK_PLAIN`, `PLANK_SACK_OAK`, `PLANK_SACK_TEAK`, `PLANK_SACK_MAHOGANY`, `PLANK_SACK_CAMPHOR`, `PLANK_SACK_IRONWOOD` and `PLANK_SACK_ROSEWOOD` through [`Client.getServerVarbitValue`](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Client.html#getServerVarbitValue(int)). These named values supply the seven exact item quantities. The shared 28-plank capacity comes from the [official Construction Contracts poll](https://oldschool.runescape.com/polls/2020/1606) and is consistent with the current OSRS Wiki. Free capacity is derived only after every typed count is in [0,28] and their sum is at most 28. Any read failure, invalid count or invalid total leaves contents and free capacity UNKNOWN. Inventory absence establishes only not-currently-carried and cannot establish global non-ownership, so `container.plank_sack.owned` also remains UNKNOWN. No state persists across logout, hop, profile change or account-hash change.

The ordinary `usable_slots` projection uses only the immutable inventory slot map and preserves its source, timestamp and confidence. It never divides, expands or otherwise translates item quantity into positions, and it needs no stackability registry. Empty observed inventory yields 28 ordinary positions; unknown, future or malformed inventory yields UNKNOWN. It does not require equipment. Last-observed values retain their confidence and cannot satisfy current-only production predicates. Production validation requires the explicit capacity-semantics declaration so a catalog cannot silently reuse this derived value for an unrelated item or loop.

### Production Herblore catalog v1 (schema v4)

`src/main/resources/uimatlas/methods/herblore-v1.json` contains six sourced records: cleaning carried grimy guam leaves; making attack, energy and prayer potions from exact carried inputs; converting an exact 4-dose super energy with four amylase crystals; and one narrowly scoped Mammoth-Might order at Mastering Mixology. The first five can use ordinary inventory observation today. Mixology remains UNKNOWN unless Children of the Sun completion, the current MMM order and the hopper's exact mox quantity are all supplied by trustworthy future providers. No order-widget or hopper observation was added.

Schema v4 preserves all production-v3 method fields and changes only `consumes` and `produces` for methods with a bounded resource flow:

- Every `consumes` object adds required `slotSemantics` while retaining its full `Requirement` shape.
- Every `produces` object adds required `slotSemantics`; its quantity must be a positive integer.
- Both arrays must be nonempty. Duplicate inputs, duplicate outputs and an item appearing in both groups are rejected for this deliberately bounded first model.
- `ONE_SLOT_PER_UNIT` quantities must not exceed 28 in one batch. Every inventory flow item must have a declared `inventory.item.<itemId>.occupied_slots` fact. Canonical item-ID validation applies to quantity and position facts.
- Schema v3 is not reinterpreted. Its existing `consumes` and `produces` objects keep their old shape and `MethodDefinition.resourceFlow` is empty.

`slotSemantics` has exactly three values:

| Value | Trust contract |
| --- | --- |
| `ONE_SLOT_PER_UNIT` | Explicit data assertion that each exact item unit occupies one ordinary slot. The live snapshot must confirm `quantity == occupied_slots`; otherwise analysis is UNKNOWN. |
| `ONE_SHARED_STACK` | Explicit data assertion that all units share one ordinary slot. The snapshot must confirm zero/zero or positive quantity/exactly one occupied slot; otherwise analysis is UNKNOWN. |
| `NO_INVENTORY_SLOT` | Quantity comes from a supported external scope and neither occupies nor releases an ordinary inventory position. It is rejected for `inventory.item.*` facts. The first use is `container.mixology_hopper.contents.30005.quantity`. |

These declarations are per resource record, not a global stackability registry. The engine never infers a declaration from quantity and never treats a quantity as a slot count without `ONE_SLOT_PER_UNIT` plus matching direct occupancy. Exact occupied positions come from the immutable normalized inventory slot map. A stack of 500 has quantity 500 and occupied positions 1. Missing inventory, invalid positions, stale/future observations, duplicate positions for a declared shared stack, or a quantity/position contradiction produce UNKNOWN with the original observations retained.

The calculator models exactly one declared consume-then-produce batch. After verified inputs are consumed, their released ordinary positions may hold outputs. A `ONE_SLOT_PER_UNIT` output needs one position per produced unit. A `ONE_SHARED_STACK` output uses an already-observed compatible exact output stack when present and otherwise needs one new position. `NO_INVENTORY_SLOT` outputs create no ordinary inventory pressure. The result retains deterministic fact-sorted checks and, when known, input positions released, output positions required, resulting free positions and an exact additional-position shortfall.

This is not a full inventory simulator. It does not infer noted/unnoted equivalence, substitutions, item disposal, acquisition, container transfer, dose combination, decanting, storage, or intermediate route feasibility. Output pressure is a preparation input only. A known slot deficit does not prove Atlas can create that slot. Missing or unsupported input facts remain missing/UNKNOWN through the existing evaluator and preparation layers.

The five ordinary carried-input records deliberately attach no hourly XP range. Wiki per-action XP and recipes are retained in source notes, but an hourly rate depends on batching, unnoting/noting and setup that this snapshot does not establish. The MMM record also omits the published high-level Mixology rates because it covers one verified order rather than the full strategy. Its current-order requirement has a 30-second freshness limit; other Herblore observations use 300 seconds. Efficiency values are coarse editorial normalized inputs for an already-carried single batch, not XP conversions or universal cross-skill claims. External goal/storage/risk/uncertainty factors remain caller supplied.

### RFD Skill Coverage Pack v1 (no schema change)

At RFD Skill Coverage Pack v1, four additional catalogs used the existing contracts: Mining (3), Fishing (2) and Agility (3) used v3; Crafting (3) used v4 for carried glassmaking/glassblowing flows. RFD Skill Coverage Pack v2 adds eight skill catalogs and twenty-one methods. The bundled `uimatlas/methods/catalogs.txt` now lists fourteen method JSON resources containing forty-one production methods. `ProductionMethodCatalog` validates index paths, delegates to `loadProduction`, rejects duplicate method IDs across files and returns immutable ID-sorted records. No filesystem scan or skill-specific registration is needed.

The first pack added no hourly XP claims. The v2 pack also avoids hourly claims whose sustained setup is not established. See the historical [v1 audit](docs/rfd-skill-coverage-v1.md) and current [v2 method, source, readiness and coverage audit](docs/rfd-skill-coverage-v2.md).

### Production requirement alternatives (schema v5)

Schema v5 preserves every production-v4 field and adds required root `requirementGroups` and method `preparationAnyOf` arrays; either array may be empty. A method references a reusable catalog-local group with exactly `{ "group": "requirement_group..." }`. Every declared group must be referenced. Group IDs, alternative IDs and fact references are stable validated identifiers. A group needs a nonblank description and at least two uniquely named alternatives. Every alternative contains a nonempty conjunction of ordinary requirements with distinct fact IDs. Safety requirements are forbidden in preparation alternatives because safety remains a hard gate.

Evaluation is deterministic by group ID, alternative ID and leaf fact ID. One fully verified alternative satisfies the group even if other alternatives are missing or unknown. If every alternative has a known failure, the group is missing preparation. If none is satisfied and at least one still could be satisfied after resolving UNKNOWN observations, the group remains UNKNOWN. Known failures stay in their alternative diagnostics and are not upgraded by another unknown branch. `PreparationFeasibility` retains every non-satisfied group as unresolved because no acquisition provider resolves a group in this milestone.

The Mining and Woodcutting catalogs use this primitive for ordinary pickaxes and axes. Each alternative combines an exact current `carried.item.<id>.quantity` predicate with its exact skill-use level. Equipment and inventory observations feed the existing generic `carried` facts. Tool identities and use levels remain data; there is no Mining, Woodcutting, pickaxe or axe switch in production Java. The same primitive also expresses the lockpick-or-hair-clip requirement for stealing artefacts.

Schema v5 retains explicit v4 slot semantics and additionally permits a flow with nonempty `consumes` and empty `produces`. This represents a bounded resource sink whose retained inventory output is genuinely absent. Firemaking uses it for one carried log: the exact input position is released and no ash is fabricated in inventory. Output-only flows are invalid. Schema v4 continues to require both inputs and outputs when a flow is present.

Production activities must be uppercase stable identifiers. Loader validation rejects malformed activities, unused/duplicate/unresolved groups, groups with fewer than two alternatives, duplicate alternative IDs or leaf facts, safety predicates in preparation groups, invalid canonical item IDs, invalid levels/quantities/slots, malformed sources and unsupported flow shapes. All schema versions still reject unknown fields and source/test markers in production resources.

### Production goals v1

`src/main/resources/uimatlas/goals/recipe-for-disaster-v1.json` is the first separate goal resource. `GoalDefinitionLoader` accepts strict schema version `1` with `dataKind: PRODUCTION`, declared `facts`, and a nonempty `goals` array. Production method schema and `MethodDefinitionLoader` are unchanged.

Each goal has a stable `goal.*` ID, display name, one completion predicate, root strategic requirements, a nonempty milestone array, optional reward metadata, and nonempty source/review metadata. Each milestone has a globally unique stable `milestone.*` ID, display name, completion predicate, dependency references, prerequisite predicates and its own nonempty source list. Completion predicates must be `EQUAL 1` quest-completion or capability facts. Requirement facts must be declared and use one of these bounded namespaces:

| Goal fact | Contract |
| --- | --- |
| `skill.<skill>.level` | Integer target in [1,99] |
| `quest.<RuneLiteQuestId>.complete` | Boolean 0/1 target; canonical quest ID required |
| `quest.<RuneLiteQuestId>.started` | Boolean 0/1 target; IN_PROGRESS or FINISHED satisfies value 1 |
| `account.quest_points` | Integer target within the caller-supplied current maximum |
| `capability.<...>` | Boolean 0/1 target; unsupported providers remain UNKNOWN |

The loader rejects unknown/missing fields, duplicate goal or milestone IDs, duplicate dependency references, unresolved references, cycles, malformed facts, quest IDs outside the caller's canonical boundary, impossible levels/Quest-point ranges, invalid completion definitions, and missing source dates. Kahn topological ordering uses ascending milestone ID whenever independent nodes are available, so resource insertion order cannot affect evaluation.

Recipe for Disaster contains the opening chapter, eight independently available freeing chapters after the opening, and the final Culinaromancer chapter depending on all eight. Direct quest prerequisites are sufficient because a verified completed parent quest semantically proves its own transitive prerequisites. The root retains the Wiki's aggregate skill targets, the 175 Quest-point requirement and an unsupported safety-relevant no-Prayer combat capability. Sir Amik retains the 107 Quest-point gate and uses `quest.85.started`, preserving the distinction between started and complete Legends' Quest. Quest item collection, combat execution, dialogue, coordinates and walkthrough steps remain Quest Helper scope.

Boostable RFD skill values are stored as explicitly described conservative unboosted planning targets. Atlas does not yet observe whether a particular boost can be obtained and sustained. This can recommend training beyond the theoretical boosted minimum, but it cannot falsely mark an unsupported boost as ready. Nonboostable requirements retain their exact listed levels. The Evil Dave chapter does not make 25 Cooking a gate because the current subquest page lists it as recommended; the aggregate RFD Cooking target already covers later chapters.

`GoalEvaluator` returns immutable `GoalState` with `COMPLETE`, `IN_PROGRESS`, or `UNKNOWN`, categorized checks with original observations, milestone statuses (`COMPLETE`, `AVAILABLE`, `BLOCKED`, `UNKNOWN`), a deterministic current frontier, and completed/total milestone counts. It never emits a fabricated percentage. Final completion takes precedence. Otherwise any required unknown makes the goal UNKNOWN; fully observed incomplete state is IN_PROGRESS. Missing and unknown remain distinct in diagnostics.

`GoalContext` derives normalized `GOAL_PROGRESS = 1.0` only when a production method's lowercase `activity` matches a verified missing `skill.<activity>.level` goal prerequisite and the method's own hard gate for that skill is currently satisfied. All other methods receive `0.0`; a satisfied or UNKNOWN target cannot award progress. This is a direct-relevance indicator, not a distance estimate or XP-rate calculation. Only positive-relevance methods become recommendation candidates. Unsupported missing/unknown requirements remain structured coverage gaps and never cause an unrelated method to be substituted.

Callers still explicitly supply `STORAGE_UNLOCK_VALUE`, `RISK`, and `UNCERTAINTY` for every generated candidate. Existing setup, method-efficiency, resource-flow, preparation, actionability, scorer weights and ranking behavior are unchanged. Goal context delegates candidates to `RecommendationDecision`, so unresolved preparation remains non-actionable even when goal progress is positive. A complete goal or an uncovered goal state can return no actionable recommendation.

#### Strategic actions v1 (no schema change)

`StrategicDecision.decide(goalContextResult, questFactors)` operates over one evaluated snapshot. It does not parse goal or method JSON. A milestone whose validated completion is `quest.<id>.complete EQUAL 1` can supply a `QUEST_MILESTONE` action. Only milestones in the selected goal graph are considered; complete milestones are omitted. Missing quest prerequisites without their own defined milestone remain coverage gaps, not automatically generated quest actions. Dependencies and requirement results come from `GoalEvaluator` unchanged.

Each candidate retains a `StrategicAction`, structural priority, direct frontier requirements, derived goal progress, optional shared score and missing external factors. Method actions compose the existing result with its method relevance; quest actions compose the milestone state, dependency states and original missing/unknown checks. Dependency explanations follow incomplete ancestors, stopping at verified completed stages, so transitive uncertainty stays visible without reopening already-proven prerequisites. The manual handoff contains `{questId, displayName, target: QUEST_HELPER, availability: MANUAL_ONLY}`. The quest ID is the RuneLite quest ID from the goal's completion fact, not an external plugin's private identifier. No handoff field is executable.

Readiness uses `READY` / `READY_WITH_PREP` for the existing actionable method states, `READY_TO_HANDOFF` for an `AVAILABLE` quest milestone, and `BLOCKED` / `UNRESOLVED` for ineligible actions. Ready handoff proves only strategic prerequisites for beginning that chapter. It does not assert a complete quest-item loadout, acquisition route, safe combat setup or permission for dangerous inventory transitions. Required UNKNOWN facts always prevent handoff, even if the caller supplies a low optional uncertainty estimate.

The strategic formula is `3G - S - 2T - 2D - 4R - 3U`, using the current default weights already owned by `MethodScorer`. For methods, the original signed contributions for these six factors are selected unchanged, including the definition's risk floor. Their full nine-factor score and method-only ranking remain attached. For quests, `G = 1` only when that chapter's completion is verified unmet, otherwise `0`; unknown completion is not evidence of progress. `S`, `T`, `D`, `R`, and `U` are explicit normalized [0,1] inputs for the modeled action. The general domain API has no defaults: missing inputs leave the quest unscored and invalid inputs are rejected. Live Planner v1 models only display of a manual handoff, so it explicitly supplies zero for all five because that display performs no setup, transition, inventory change, combat or uncertain automated action. Those zero contributions do not describe executing the quest. `MANUAL_ONLY` handoff text and the UI state that quest item/combat readiness remain for manual preflight preserve that boundary; any encoded unknown strategic prerequisite or safety capability still blocks the handoff.

Quest scores are produced only for ready handoffs with all five estimates present. A blocked or unknown quest may retain known relevance but has no score and cannot win. Method score absence remains absence, with the original derivation diagnostics explaining why. A relevant method's high score cannot make unresolved preparation actionable. A goal-complete result contains no strategic candidates.

Ordering uses three structural tiers before score: `FRONTIER_HANDOFF`, `FRONTIER_UNBLOCKER`, then `GOAL_PROGRESS`. A frontier handoff is an `AVAILABLE` milestone from the evaluated goal graph. A frontier unblocker is a method whose matched missing requirement belongs to a `BLOCKED` milestone with every dependency complete. Unknown milestones cannot create unblocker priority. Within one tier, ordering remains descending shared score, then ascending action-kind name, then ascending stable action ID; unscored diagnostics use negative-infinity score and cannot win. Selection takes the first scored, positive-relevance, actionable candidate. Readiness remains an independent gate rather than a scoring bonus. Neither JSON insertion order nor fact/map order resolves ties. The priority derivation reads only generic graph structure, scope IDs and requirement matches.

The original `GoalContext` is retained in the result, including all method-only coverage gaps and zero-relevance catalog entries. Thus a skill requirement without a currently supported matching method stays a gap, while a defined, ready quest chapter can now offer a strategic action for a previously method-uncovered completion requirement. No unrelated training is substituted. These domain values do not authorize continued use after the snapshot becomes stale or the account changes.

#### Preparation feasibility and actionability

- `AVAILABLE + READY` -> `ACTIONABLE`.
- `NEEDS_PREP + FEASIBLE_PREP` -> `ACTIONABLE_WITH_PREP`.
- `AVAILABLE/NEEDS_PREP + UNRESOLVED_PREP` -> `NOT_ACTIONABLE`.
- `BLOCKED` / `UNKNOWN` -> `NOT_ACTIONABLE`.

`PreparationFeasibility` retains exact known deficits from evaluator preparation and working-capacity checks, plus unresolved predicates and hard blockers. Exact means only that the delta is measurable. It does not mean Atlas knows what may safely leave inventory or how to acquire an item. A known deficit becomes `FEASIBLE_PREP` only when an explicit provider supplies a current verified true observation for that exact fact within the requirement's age limit. The support observation and its provenance remain in the result. No such live provider ships in this milestone, so the default support map is empty. This leaves slot freeing, acquisition, unknown storage and unknown capabilities unresolved without adding an evaluator status or a full PreparationPlanner.

`RecommendationDecision` runs the complete generic domain chain from `AccountState` and retains every candidate's evaluation, setup derivation, selected efficiency profile and trusted XP, score contributions, preparation result and actionability. Scored candidates use the existing ranker order; unscored diagnostics follow by method ID. The best actionable result is the first scored actionable candidate, not necessarily the highest raw score. If none qualify, the optional result is empty: “No actionable recommendation yet.” The class has no Construction branches, client access or UI call site.

### Account state fact contract

`new AccountStateFacts(accountState, asOf)` creates an immutable `FactLookup` from normalized domain values only. Pass it directly to `MethodEvaluator.evaluate(method, facts, now)`, with `now >= asOf` (normally use the same instant). It does not read a clock, client, service or external plugin. Create a new adapter when choosing to evaluate a new account snapshot; an existing adapter never follows later account updates or resets.

IDs are case-sensitive and stable:

| Fact ID | Value |
| --- | --- |
| `skill.<skill>.level` | Observed real level, never boosted level |
| `skill.<skill>.xp` | Observed total XP |
| `quest.<questId>.complete` | 1 for FINISHED, 0 for verified NOT_STARTED/IN_PROGRESS; UNKNOWN state stays unknown |
| `quest.<questId>.started` | 1 for verified IN_PROGRESS/FINISHED, 0 for verified NOT_STARTED; UNKNOWN stays unknown |
| `account.quest_points` | Server-observed Quest-point total captured with the quest snapshot |
| `inventory.occupied_slots` | Number of occupied slots, independent of stack quantities |
| `inventory.free_slots` | 28 minus occupied slots, only from an available inventory observation |
| `inventory.item.<itemId>.usable_slots` | Ordinary free positions plus positions occupied by this exact item; see capacity contract above |
| `inventory.item.<itemId>.occupied_slots` | Direct count of ordinary positions currently holding that exact item ID; each observed slot entry counts once, independent of quantity |
| `inventory.item.<itemId>.quantity` | Sum of quantities for that exact item ID in inventory |
| `equipment.item.<itemId>.quantity` | Sum of quantities for that exact item ID in equipment |
| `carried.item.<itemId>.quantity` | Inventory plus equipment quantity, requiring both observations |
| `capability.poh.owned` | 1 only from verified POH ownership; 0 only if a future provider can verify absence; missing/unavailable state is unknown |
| `container.<id>.owned` | Verified ownership 1/0 where a provider can establish it; plank-sack absence from inventory is UNKNOWN |
| `container.<id>.contents.<itemId>.quantity` | Verified exact typed contents; an omitted type or failed read is UNKNOWN |
| `container.<id>.free_capacity` | Verified spare units in the provider's declared capacity pool |

`<skill>` is the normalized account skill key lowercased with `Locale.ROOT`, for example `CONSTRUCTION` becomes `construction`. No per-skill mappings or RuneLite enums are introduced. Missing skills are unknown; there is no default level or XP. The observer already excludes the aggregate skill. `<itemId>` is a canonical nonnegative decimal Java integer (`0` through `2147483647`, no sign or leading zeroes except `0` itself), matching the normalized item model. IDs work generically without asserting that an ID exists in the game. Exact IDs remain distinct, including noted/unnoted and other variants; no equivalence or nested-container content is inferred. Malformed or unsupported IDs return unknown.

An observed empty inventory gives 0 occupied and 28 free slots. Unknown inventory gives unknown slot facts and item quantities. A stack consumes one slot regardless of quantity; duplicate item stacks in different slots contribute all their quantities. Exact-item occupied-slot facts count those positions directly and never inspect quantity. Summation uses `long` before conversion to the evaluator's numeric `Double` observations, avoiding 32-bit quantity overflow. Missing items in a known inventory/equipment observation are known zero with that container's metadata. A missing item in an unknown container remains unknown. Inventory slots outside the normalized 0–27 range make free, total-occupied and exact-item occupied-slot facts unknown; item quantity facts still describe the observed contents.

Direct skill, container quantity and slot facts preserve the input source string, confidence and timestamp unchanged. Negative skill values and observations dated after `asOf` yield explicit unknowns. This cutoff also prevents a future-dated carried constituent being hidden by the older combined timestamp. Unknown observations have no invented value, source or timestamp, following the existing `Observation` model.

Carried quantities require complete inventory **and** equipment observations, even when one source already contains enough items or both quantities would be zero. Either unknown source makes the combined fact unknown. Both `VERIFIED_NOW` sources produce `VERIFIED_NOW`; any `LAST_OBSERVED` source makes the result `LAST_OBSERVED`. The combined time is the older input timestamp, and its source string is `carried sum [inventory: <inventory source>; equipment: <equipment source>]`. This is derived provenance, not a new observation event. Sources remain separately queryable through their scoped facts. The model currently supports one timestamp/source string per observation, not a structured provenance graph.

Confidence is not a freshness exemption: preserved `VERIFIED_NOW` observations can expire. The evaluator rejects facts exceeding a predicate's age limit and accepts `LAST_OBSERVED` only when explicitly permitted. Historical free-slot values and historical absent-item zeroes keep their historical confidence; they do not assert current free space or absence. The cutoff does not impose a global maximum age or renew stale observations.

Capability values are held in a generic `Observation<Map<String, Boolean>>` keyed by complete stable `capability.*` fact IDs. `AccountStateFacts` accepts only lowercase multi-segment capability IDs and maps verified booleans to numeric 1/0 without changing their source, confidence or timestamp. A missing key is unknown, including when another capability was observed. Historical, future and expired capability observations follow the same `Requirement` rules as every other fact.

Quest projections accept only nonnegative IDs from the normalized quest map. `FINISHED` maps completion and started to 1; `IN_PROGRESS` maps started to 1 and completion to 0; `NOT_STARTED` maps both to 0. A `QuestStatus.UNKNOWN` entry yields neither fact. All projected quest facts preserve the shared source, timestamp and confidence. The live snapshot uses RuneLite's supported `Quest.getState(Client)` abstraction and marks the collection `LAST_OBSERVED` because there is no universal quest-stage event. Production RFD predicates explicitly allow that confidence for at most 24 hours. Future or expired snapshots remain UNKNOWN.

Quest points are read with `Client.getVarpValue(VarPlayerID.QP)` when quests refresh, matching RuneLite's built-in achievement-diary `QuestPointRequirement`. Negative or failed reads are UNKNOWN. The derived `account.quest_points` fact preserves `LAST_OBSERVED` source/time/confidence. The observer stores neither character names nor account identifiers; all quest and Quest-point state clears through the existing logout, hop, profile and account-hash reset path.

The live `RuneLiteAccountObserver` currently provides only verified `capability.poh.owned = 1`, using a positive result from `Client.getServerVarbitValue(VarbitID.POH_HOUSE_LOCATION)` while logged in with an available account boundary. RuneLite supplies a named house-location varbit but no documented ownership enum or verified-zero contract, so zero, negative values and read failures are UNKNOWN. The observer does not fabricate false or persist a last-observed value. Every logged-in game tick replaces the capability observation; all existing logout, hop, reconnect, profile and account-switch resets clear it. The exact API sources are the [RuneLite 1.12.38 generated varbit constants](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java) and [Client server-varbit API](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-api/src/main/java/net/runelite/api/Client.java).

No other storage, POH room/furniture, STASH, looting-bag, deathbank or integration facts are exposed. Partial account readiness does not suppress independently observed supported sections. The adapter relies on normalized snapshot/reset behavior at the observation boundary. Production method loading, schema v3, ordinary capacity semantics and scoring weights are unchanged.

### Scoring scaffold contract

`MethodScorer` accepts an evaluation and all nine explicit normalized [0,1] factors. It returns no score for `BLOCKED` or `UNKNOWN`; `NEEDS_PREP` remains eligible for comparison as a plan requiring preparation. It does not become ready merely because it scores well.

| Input | Placeholder weight |
| --- | ---: |
| Goal progress | +3 |
| Storage/unlock value | +3 |
| Current inventory fit | +2 |
| Method efficiency | +1 |
| Setup cost | -1 |
| Transition cost | -2 |
| Inventory disruption | -2 |
| Risk | -4 |
| Uncertainty | -3 |

The score is the sum of weighted contributions, retained individually for explanation. Weights are centralized in `MethodScorer.Factor` and replaceable through the scorer constructor (complete map, finite magnitudes <=100, original benefit/cost direction retained; zero disables a factor). Equal scores remain equal in the scorer; `MethodRanker` orders exact ties by ascending method ID. Switching policy remains future work.

Risk uses the larger of the supplied input and the definition's classification floor: LOW=0, CAUTION=0.5, HIGH=1. UNKNOWN danger is ineligible regardless of weights. Optional estimate uncertainty may be penalized; required-state uncertainty cannot be traded against rewards.

The setup derivation below supplies four account-specific factors. With a selected goal, `GoalContext` derives goal progress under the production-goal rule above. Live Planner v1 supplies method risk from the production danger floor, sets storage value to zero because no account-specific storage-benefit provider exists, and sets optional-estimate uncertainty to zero because it makes no additional estimate. These zeros cannot unlock a requirement or profile: required missing, unknown, stale or future state still prevents scoring/actionability. Method efficiency comes only from an applicable verified production profile. Missing or invalid required values remain errors or absent scores, never defaults. Raw XP/hour is not mixed directly with normalized efficiency. Potential storage value in a definition remains metadata, not proof that this account benefits from an unlock. Synthetic fixtures are never loaded into live production scoring.

`MethodRanker.Candidate` pairs a definition with an immutable copy of explicit score inputs. `rank(candidates, facts, now)` evaluates all definitions at the supplied instant and delegates eligibility and input validation to `MethodScorer`. Excluded candidates need no usable score inputs because no score is calculated; eligible candidates with missing or invalid factors fail the call. Duplicate method IDs are rejected. `RankingResult` retains the evaluation time, eligible candidates sorted by descending score then ID, and unscored excluded diagnostics sorted by ID. Its best is optional; alternatives contain only the remaining eligible candidates. Known missing preparation remains explicit and may win; missing required information cannot be offset by rewards. Negative eligible scores may still win. Original candidate inputs, requirement diagnostics and all signed contributions remain available as structured immutable values. No ranking result or player-specific scoring inputs are added to JSON, and the resource schema is unchanged.

### Account-specific setup scoring

`SetupScoringInputs.derive(method, facts, now)` derives only current inventory fit, setup cost, transition cost and inventory disruption. `FactLookup` supplies the observed carried setup through the method's existing scoped quantity predicates and `inventory.free_slots`. Use one coherent immutable snapshot and the same explicit instant for derivation, evaluation and ranking. There is no second container model or account adapter dependency in the component.

The result contains either all four normalized factors or no factors, plus unresolved requirements and the original observations used (including source, timestamp and confidence). `result.withExplicitFactors(...)` accepts exactly the other five normalized factors and returns an optional complete immutable map that feeds `MethodScorer` or `MethodRanker.Candidate` directly. It rejects omitted factors, attempts to override derived factors, and invalid values. An unresolved result cannot create a complete scoring map; callers must retain its diagnostics and defer that candidate until usable observations exist. Do not replace an absent map with defaults. The existing evaluator independently owns hard-gate eligibility and preparation output.

The first deliberately coarse formula uses no prices or acquisition knowledge. Let `clamp(x) = min(1, max(0, x))`:

- For each `setupItems` and `consumes` predicate, `d_i = max(0, 1 - observedQuantity / targetQuantity)`. Surplus earns no extra credit. A half-carried batch contributes 0.5; an observed missing item contributes 1. Each declared item/batch need has equal weight, irrespective of stack size.
- `D = sum(d_i)`, `N = number of setupItems + consumes`, and `P = count of known unsatisfied preparation predicates`. Arbitrary preparation predicates are binary burdens; quantity-aware inputs belong in `setupItems`/`consumes`.
- `B = D + P`. Four equivalent wholly missing needs represent full normalized preparation burden; fractions make increasing missing quantities nondecreasing in cost, up to saturation.
- `F = observed free slots`, `R = declared minimum free slots`, `L = max(0, R - F) / 28`, and `O = (28 - F) / 28`. `L` measures the share of inventory capacity needing rearrangement to reach the declared minimum. It identifies no items or permissible actions.
- `currentInventoryFit = clamp((N == 0 ? 1 : 1 - D/N) * (1 - L))`.
- `setupCost = clamp(B/4 + (B > 0 ? setupMinutes/30 : 0))`.
- `inventoryDisruption = clamp(L + costs.inventoryDisruption * O)`.
- `transitionCost = clamp(setupCost + inventoryDisruption + (B > 0 or L > 0 ? transitionMinutes/30 : 0))`.

The existing disruption metadata is a pressure hint scaled by observed occupancy, not an exact prediction of produced-item slots. Thirty declared minutes represent full normalized cost; these are explicit placeholder scales, not calibrated gameplay time predictions. Setup and transition minutes apply only when the corresponding change is needed, preventing repeated preparation charges for an already fitting setup. Transition includes setup burden intentionally, while the scorer retains its separate setup and disruption penalties and unchanged weights. An intrinsically disruptive method can still have positive transition friction with its setup already carried. Unrelated carried items incur no item-change penalty when capacity suffices and the disruption hint is zero.

Every consumed observation uses the existing `Requirement.evaluate` freshness/confidence checks. Missing, unknown, disallowed historical, future, expired, negative or nonfinite quantities leave the result unresolved. Unknown inventory never becomes empty or 28 free slots, even for methods without setup items. Free slots must additionally be an integer in [0,28]. Equipment matters for `equipment` and `carried` facts; unknown equipment prevents deriving those inputs, while inventory-only methods do not require an irrelevant equipment observation. Historical observations are accepted only under their declared predicate policy and retain their provenance. This required derivation uncertainty is **not** converted into the scorer's optional-estimate `UNCERTAINTY` penalty and cannot be offset by rewards.

Requirements and quantity accumulation are ordered by fact ID. No clock reads, mutable global state, map-order dependence or random values are used. The v1 JSON shape is unchanged. The component expects validated definitions and checks the slot/quantity predicate shapes needed by its formula.

Limits: no equipped-slot compatibility, extra acquisition-slot prediction, substitute items, usefulness/protection inference, retrieval, travel, reacquisition, cleanup or disposal planning. A missing quantity expresses generic obtaining/rearranging burden only. Setup scoring itself does not use `produces`; schema-v4 `ResourceFlow` performs the separate, explicitly declared slot calculation retained by `RecommendationDecision`. Setup scoring introduces no goal, XP, storage or risk derivation. Efficiency profile derivation is separate and does not change this formula. Synthetic tests cover the full account-state/evaluation/derivation/scoring/ranking chain: the slightly less efficient method wins with its setup carried, and changing only the inventory reverses the winner.

## General rules

Every top-level data file or record family should support:

- stable IDs
- schema version
- source/provenance metadata where appropriate
- review/freshness metadata for curated UIM knowledge
- references by stable ID rather than duplicated embedded copies

Do not optimize the schema for the first five methods only. Optimize for hundreds of methods without one-off Java logic.

## Resource layout

Suggested starting layout:

```text
src/main/resources/uimatlas/
  methods/
  milestones/
  items/
  storage/
  quests/
  opportunities/
  locations/
```

Large families can be split by skill or topic later.

## Shared concepts

### Stable ID

Examples:

```text
method.mahogany_homes.adept
milestone.recipe_for_disaster
storage.poh.costume_room
storage.stash.varrock_church
item.dragon_defender
opportunity.birdhouse_run
```

IDs should remain stable even if display names change.

### Requirement

A generic requirement model should be able to express:

- skill level
- quest state
- diary state
- item ownership/count
- current item possession
- capability/unlock
- location/area access
- spellbook
- storage capability
- free inventory slots
- account-mode restriction
- custom condition only when a generic representation is truly insufficient

Conceptual shape:

```json
{
  "type": "SKILL_LEVEL",
  "skill": "CONSTRUCTION",
  "minimum": 50
}
```

Requirements may support `allOf`, `anyOf`, and `noneOf` composition.

Avoid adding bespoke Java subclasses for every requirement type unless the generic evaluator genuinely cannot model it.

### State confidence requirement

Safety-sensitive definitions may require a confidence threshold.

Example concept:

```json
{
  "type": "STORAGE_STATE",
  "storageId": "storage.deathbank.hespori",
  "requiredState": "EMPTY",
  "minimumConfidence": "VERIFIED_NOW"
}
```

## Method definition

A method is a real activity or training approach the player can go do.

Minimum conceptual fields:

```json
{
  "id": "method.mahogany_homes.adept",
  "displayName": "Mahogany Homes",
  "category": "TRAINING",
  "primarySkill": "CONSTRUCTION",
  "start": {
    "npc": "Amy",
    "locationId": "location.falador.mahogany_homes"
  },
  "hardRequirements": [],
  "preparation": [],
  "inventory": {
    "minimumFreeSlots": 0,
    "comfortableFreeSlots": 0
  },
  "style": {
    "relaxed": 0.6,
    "balanced": 1.0,
    "efficient": 0.9,
    "maxEffort": 0.6,
    "tickManipulation": false
  },
  "danger": {
    "class": "SAFE",
    "deathbankPolicy": "ALLOWED"
  },
  "outputs": [],
  "secondaryXp": [],
  "synergies": [],
  "transitionTags": [],
  "sources": []
}
```

The example values are illustrative only, not authoritative game data.

### Method fields to support over time

- `id`
- display name
- category
- primary skill/activity
- level range
- start NPC/location/activity entrance
- hard requirements
- actual preparation requirements
- minimum and comfortable free slots
- required setup items
- optional setup items
- consumes
- produces
- XP/hr range and assumptions
- secondary XP
- attention/intensity profile
- tick-manipulation flag
- danger class
- deathbank policy
- looting-bag implications
- storage assumptions
- transport assumptions
- sidecar-compatible methods
- resource value
- milestone tags
- transition tags
- stop-condition templates
- specialist-plugin handoff hints
- sources/review metadata

## Stop conditions

A recommendation should have a meaningful stopping condition.

Examples:

- target skill level
- target XP
- unlock achieved
- resource quantity reached
- quest completed
- task completed
- reward purchased
- storage built

Conceptual shape:

```json
{
  "type": "SKILL_LEVEL",
  "skill": "CONSTRUCTION",
  "target": 42,
  "reason": "Unlocks the selected storage capability"
}
```

The target may be generated dynamically from the milestone/prerequisite engine rather than hard-coded inside the method.

## Milestone definition

Milestones represent meaningful player outcomes, not individual training steps.

Conceptual shape:

```json
{
  "id": "milestone.song_of_the_elves",
  "displayName": "Song of the Elves",
  "requirements": [],
  "unlockValue": 1.0,
  "automaticPriority": 0.8,
  "sources": []
}
```

Milestone requirements can reference:

- quests
- skills
- items
- capabilities
- sub-milestones

The graph should support dependency resolution without embedding route logic in Java.

## Quest definition

UIM Atlas should model only the quest facts required for planning and preflight. It should not duplicate Quest Helper steps.

Possible fields:

- stable quest ID
- display name
- prerequisites
- skill requirements
- important UIM free-slot needs
- dangerous fights
- Entrana/equipment restrictions
- notable temporary-item pressure
- relevant death/storage constraints
- start NPC/location
- milestone tags
- source metadata

## Storage definition

Storage is both a location and a capability.

Conceptual fields:

- storage ID
- category
- display name
- capability requirements
- observation source(s)
- item eligibility rules
- set-completion rules where relevant
- capacity constraints
- retrieval constraints
- safety/loss semantics
- location
- downstream value tags

Categories may include:

- POH
- STASH
- looting bag
- carryable container
- Tool Leprechaun
- minigame/activity storage
- coffers/currency stores
- deathbank/item retrieval service
- deathpile
- ship/cargo storage
- world storage

### POH/STASH unlock value

Storage definitions may expose values used by the storage planner, such as:

- potential permanent slots relieved
- item families supported
- gear sets supported
- clue relevance
- travel capability
- future-method tags

The final score should still be computed from the player's actual account state.

## Item lifecycle rule

The item model should support UIM-specific disposition and reacquisition decisions.

Conceptual fields:

- canonical item ID/family
- storable locations
- set-storage requirements
- quest relevance
- clue/STASH relevance
- diary relevance
- milestone relevance
- reclaim method
- purchase method
- craft/gather method
- deterministic vs RNG reacquisition
- travel/setup burden
- rarity/reacquisition class
- substitutes
- disposal restrictions/warnings
- protected-by-default flag for obviously dangerous cases

### Reacquisition class

Use a simple explainable classification before attempting overly precise time models.

Example levels:

- TRIVIAL
- EASY
- MODERATE
- COSTLY
- RARE
- UNIQUE_OR_IRREVERSIBLE

Optional estimated time/cost can be layered on later.

Do not use GE value as a proxy for UIM replacement difficulty.

## Preparation action

Preparation should be generated from the delta between method needs and current state.

Conceptual action types:

- KEEP
- RETRIEVE
- STORE
- GATHER
- BUY
- CRAFT
- UNNOTE
- EQUIP
- UNEQUIP
- USE
- ALCH
- DROP
- RECLAIM
- TRAVEL
- CHECK_STATE

Safety-sensitive actions such as DROP/ALCH should require item-lifecycle validation and may be disabled when confidence is insufficient.

## Transition tag

Transition tags help estimate setup friction without one-off code.

Examples:

- needs_empty_inventory
- needs_many_free_slots
- requires_looting_bag_change
- requires_poh_regear
- requires_spellbook_swap
- requires_deathbank
- produces_many_unstackables
- inventory_compressing
- reuses_current_setup

Tags are hints, not substitutes for explicit requirements and item flows.

## Danger model

Suggested danger classes:

- SAFE
- LOW_RISK
- DANGEROUS_DEATH
- WILDERNESS
- PVP
- SPECIAL_STORAGE_RISK

Deathbank policy can be separate:

- ALLOWED
- FORBIDDEN
- CONDITIONAL
- UNKNOWN

Conditional cases should include a machine-readable condition and human-facing reason.

## Opportunity definition

Recurring opportunities may include:

- birdhouse runs
- herb runs
- tree runs
- farming contracts
- clue opportunities

Possible fields:

- ID
- readiness source
- expected duration
- transition cost profile
- reward/resource value
- milestone tags
- interruptibility policy
- natural-boundary preference

## Source metadata

Curated UIM knowledge should record where it came from.

Conceptual shape:

```json
{
  "sourceType": "OSRS_WIKI",
  "url": "https://oldschool.runescape.wiki/...",
  "reviewedAt": "2026-09-13",
  "notes": "UIM-specific method assumptions reviewed"
}
```

Source metadata is primarily for maintainability and review, not normal player UI.

## Schema versioning

Each data family should support schema versioning.

When the schema changes:

- update validators
- migrate bundled data in the same change
- reject unsupported schema versions loudly in development/tests
- do not silently reinterpret old fields

## Validation

Automated validation should catch at least:

- duplicate IDs
- unresolved references
- invalid skill/quest/item identifiers
- invalid numeric ranges
- malformed danger/storage policy
- missing stop-condition support where required
- impossible milestone cycles
- unknown preparation action types
- missing source metadata for curated safety-sensitive records

## Data-size philosophy

Large resource files are acceptable when they encode useful game knowledge cleanly. Large repetitive Java files are not.

When adding new game coverage, the expected growth should normally be:

```text
mostly data
+ small/no generic Java changes
+ tests/fixtures
```

If every new method requires a new Java class or new switch branch, the architecture is drifting in the wrong direction.
