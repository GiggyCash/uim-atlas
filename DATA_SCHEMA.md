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

The only catalog is `src/test/resources/uimatlas/methods/synthetic-methods.json`, containing three invented exercises. It is excluded from the plugin JAR. Method IDs must begin with `synthetic.method.` and display names with `Synthetic `. The loader is not wired into plugin startup. Production catalogs require a deliberate schema/loader extension, including curated source and review metadata; changing the data-kind label alone cannot enable them.

Stable IDs use lowercase letters, digits, underscores and dot-separated segments: `[a-z][a-z0-9_]*(\.[a-z0-9_]+)+`. Every requirement fact and produced resource ID must resolve in the catalog's `facts` declarations. These declarations validate references, not observation availability. Unknown fact observations remain unknown. There are no real skill, quest, item or location registries yet.

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

Every predicate requires `fact`, `comparison` (`AT_LEAST`, `EQUAL`, `AT_MOST`), nonnegative finite `target`, nonblank `description`, boolean `allowLastObserved`, integer `maxAgeSeconds` in [0, 2147483647], and boolean `safetyRelevant`. Boolean facts use 0/1; quantities, levels and XP use numeric facts. There is no implicit conversion of enum/quest state to numbers. That will require an explicit adapter contract. This slice supports conjunction only, without prerequisite graphs or compound expressions.

Preparation, free slots, setup items and consumed inputs form one preparation group. Repeated fact IDs within that group are rejected, preventing accidental double use or undercounting of one supply. Definitions must express the combined need under one fact until richer resource accounting is justified. Safety-relevant predicates must be hard requirements and cannot accept last-observed confidence.

The evaluator consumes `Map<String, Observation<Double>>` and an explicit evaluation time. Callers must supply a coherent snapshot, retain the source/time/confidence of the underlying observation, and use separate fact IDs for distinct possession/storage scopes. An observed absence may be zero; an unobserved quantity must not be zero. No automatic account-state flattening or storage inference exists.

Freshness is checked independently of confidence: future timestamps, expired observations, missing observations, explicit unknown confidence, and nonfinite/negative observed values all produce `UNKNOWN`. Age exactly at the declared limit is accepted. `LAST_OBSERVED` needs explicit permission and must still pass the age check; it is never sufficient for a safety-relevant predicate. The current observation model has no user-confirmed/conflicted state; future adapters must preserve these conservatively rather than upgrading them to verified.

Evaluation precedence is `BLOCKED` (known failed hard gate), then `UNKNOWN`, then `NEEDS_PREP`, then `AVAILABLE`. Missing hard gates, missing prep and unknown predicates remain in separate immutable diagnostic lists even when another status takes precedence. Only known failed preparation predicates enter preparation output. Unknown danger independently yields `UNKNOWN`. Stop conditions can use the same predicate evaluator later; this slice does not detect completion or filter completed goals.

Validation rejects missing/extra/duplicate JSON fields, nulls, wrong types, unsupported versions/enums, duplicate IDs, unresolved fact references, invalid numeric ranges, empty stop lists and weakened safety policies with field-path errors. Parsing has a depth limit of 32. Validation uses RuneLite's existing Gson dependency; there is no additional runtime library, reflection-based domain deserialization, or separate JSON Schema dependency. Domain constructors copy collections; resource validation is owned by the loader.

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

The score is the sum of weighted contributions, retained individually for explanation. Weights are centralized in `MethodScorer.Factor` and replaceable through the scorer constructor (complete map, finite magnitudes <=100, original benefit/cost direction retained; zero disables a factor). Ties remain ties; candidate ranking and switching policy are future work.

Risk uses the larger of the supplied input and the definition's classification floor: LOW=0, CAUTION=0.5, HIGH=1. UNKNOWN danger is ineligible regardless of weights. Optional estimate uncertainty may be penalized; required-state uncertainty cannot be traded against rewards.

Normalization and account-specific value derivation are deliberately not implemented. Callers must explicitly supply every factor; missing or invalid values are errors, not zero defaults. Raw XP/hour and minutes must not be mixed directly with normalized inputs. Tests use invented fixed denominators solely to demonstrate comparisons. Potential storage value in a definition is metadata, not proof that this account benefits from an unlock. Likewise, setup costs and inventory disruption need future account-specific derivation. No production score is calculated from these fixture defaults.

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
