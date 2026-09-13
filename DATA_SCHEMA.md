# UIM Atlas Data Schema

## Goal

Represent RuneScape/UIM knowledge as validated resource data so adding game coverage does not require proportional Java growth.

The exact serialization format can be chosen during implementation, but bundled versioned JSON is the preferred starting point because it is straightforward to validate, diff, generate, and load in a RuneLite plugin.

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
