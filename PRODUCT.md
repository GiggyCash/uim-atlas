# UIM Atlas Product Specification

## Product definition

UIM Atlas is a live planning and quality-of-life assistant for Ultimate Ironman accounts.

It observes what RuneLite can safely observe, remembers supported state with explicit freshness, understands UIM-specific storage and transition costs, and recommends one strong next method or activity.

It should feel like a knowledgeable UIM coach that understands the player's exact situation, not a generic leveling guide.

## North star

> Given this exact UIM, this exact inventory, these known storages, this goal, and this play style, what should the player do next, and what is the safest, least painful way to get there?

## Product philosophy

> Observe deeply. Think deeply. Guide simply.

The engine may be complex. The default UI should not be.

UIM Atlas should keep the player focused on Old School RuneScape, not on UIM Atlas.

## What the product is responsible for

UIM Atlas should:

- understand current account state
- understand current inventory pressure
- understand observed storage and storage capabilities
- understand major UIM milestones and prerequisite graphs
- choose a specific method or activity, not merely a target level
- account for setup and transition cost
- account for item reacquisition difficulty
- account for UIM risk
- give heavy strategic value to useful POH and STASH progression
- tell the player only what preparation is actually missing
- explain the few factors that made the recommendation win
- know when a previous recommendation is no longer valid
- become quiet once the player is actively doing the recommended activity
- rescore at sensible task boundaries
- celebrate real RuneScape progress

## What the product should not become

UIM Atlas should not:

- automate gameplay
- duplicate Quest Helper step-by-step quest execution
- duplicate a full Mahogany Homes/Tempoross/Wintertodt/etc. helper when a specialist plugin already does that well
- duplicate a full storage browser if an integration can provide the data
- become a giant static guide panel
- force the mathematically highest XP/hr method regardless of UIM setup cost
- invent confidence where state is unknown
- manufacture engagement with fake rewards or punitive streaks

## First-run onboarding

Keep onboarding short.

### 1. Welcome

Explain that UIM Atlas reads observable account state, current inventory, stats, quests, timers, and storage it has observed or received from supported integrations.

Explain:

- it does not automate gameplay
- unknown storage remains unknown
- risky UIM actions are treated conservatively

### 2. Play style

Options:

- Relaxed
- Balanced
- Efficient
- Max Effort

Optional toggle:

- Avoid tick manipulation

This is a scoring preference, not a permanent restriction. It remains editable in settings.

### 3. Main goal

Keep the list intentionally small:

- Automatic
- Recipe for Disaster
- Fire Cape
- Song of the Elves
- Quest Cape
- 2000 Total
- Max

Automatic is the default.

### 4. Safety preference

Options:

- Never suggest deathpiling/deathbanking
- Only suggest when clearly beneficial
- Allow when required for the chosen route

Suggested default: `Only suggest when clearly beneficial`.

### 5. Optional integrations

Examples:

- Dude, Where's My Stuff?
- Inventory Setups
- Wise Old Man
- SOTW/BOTW prioritization

Do not force external integrations to use the plugin.

## Account scan

After onboarding, show a short state summary rather than a configuration wall.

Example concepts:

- UIM detected
- skills loaded
- quests loaded
- inventory observed
- equipment observed
- storage integrations available/unavailable

State confidence concepts:

- VERIFIED NOW
- LAST OBSERVED
- USER CONFIRMED
- UNKNOWN
- NEEDS RECHECK

The normal recommendation card should not show these labels unless they matter to the decision.

## Main recommendation model

A normal recommendation should answer the useful parts of:

- WHAT method/activity
- WHERE to start
- WHO to speak to, if that is genuinely useful
- PREP that is still missing
- STOP CONDITION
- WHY this won
- AFTER, when a follow-up unlock/action matters

UIM Atlas does not need to explain the complete action loop if a specialist plugin already handles execution.

### Bad recommendation

`Train Construction to 42.`

### Better recommendation

`Mahogany Homes`

- Start: Amy, Falador
- Missing prep: teak planks, steel bars
- Stop at: 42 Construction
- Why: unlocks a useful POH storage capability, fits the current inventory, low transition cost, no dangerous storage required
- After: build the newly unlocked storage upgrade

The exact start point and prep should adapt to the player. Do not show teleports or items that are already satisfied when they are not actionable.

## Requirements behavior

Split requirements into three concepts.

### Hard gates

Examples:

- skill levels
- quests
- diaries
- areas
- spellbooks
- transport/access unlocks
- account restrictions

When satisfied, hide them from the default UI.

When unsatisfied, either:

- invalidate the method, or
- create a meaningful prerequisite recommendation

Do not display obvious text such as `52 Fletching required` when the player already has 52 Fletching.

### Preparation requirements

Only show what the player needs to change now.

Examples:

- retrieve an item
- buy/craft/gather an input
- store a carried item
- free a slot
- change spellbook

### Unknown requirements

If a critical requirement cannot be verified:

- avoid the method, or
- show a clear check-needed state

Never silently assume it is satisfied.

## POH and STASH strategy

POH and STASH progression should receive heavy scoring weight because they can permanently reduce UIM inventory pressure and future setup friction.

Storage-unlock value can include:

- permanent slots relieved
- current carried items made storable
- future methods made easier
- future gear transitions reduced
- clue setups simplified
- useful transport unlocked
- number of future recommendations improved
- milestone synergy

The engine must still compare this value against:

- Construction/resource cost
- setup time
- distance from the selected milestone
- whether the unlocked storage is actually useful to this account

Do not blindly recommend Construction just because storage is generally valuable.

## Inventory as strategy

Two accounts with identical stats may receive different recommendations because they carry different items.

The engine should reason about:

- free slots now
- minimum free slots for a method
- comfortable free slots
- expected slots after the method
- items consumed
- items produced
- items that become storable
- items that are awkward to reacquire
- whether the existing setup can be reused

A slightly slower method can correctly win if it avoids a large UIM transition.

## Item disposition

When useful, UIM Atlas may advise:

- keep
- store
- use/consume
- reclaim later
- alch
- drop/dispose

Disposal guidance is safety-sensitive.

It must consider:

- future quests
- clue/STASH use
- diaries
- milestones
- nearby methods
- POH set requirements
- reclaimability
- deterministic reacquisition
- shop/travel cost
- RNG/rare-drop difficulty
- minigame currency cost
- player-protected items

Users should be able to protect an item from disposal-style recommendations.

## Transition cost

A faster method is not automatically better.

Conceptual decision model:

`progress value + unlock value + synergy + session fit - transition cost - inventory pressure - reacquisition cost - risk`

Transition cost may include:

- travel
- storage swaps
- looting-bag changes
- gearing
- unnoting
- gathering supplies
- reclaiming items
- rebuilding a setup
- expected time to recover the old setup afterward

`Keep doing what you're doing` is a valid and often excellent recommendation.

## Focus state

Once the player begins a recommendation, UIM Atlas should become quieter.

Example:

`CURRENT: Herblore 67 -> 70`

Show useful progress such as:

- current/target level
- XP remaining
- session XP
- current XP/hr when reliable
- next review point

Do not continuously tempt the player with small recommendation changes while the current plan remains valid.

## Recommendation invalidation

If the player ignores the recommendation or materially changes account state, the plugin should adapt without scolding.

Examples of meaningful invalidation:

- inventory changed substantially
- player started a different recognizable activity
- storage state changed
- quest completed
- level target reached
- Slayer task changed/completed
- death/storage state changed
- selected goal changed

Quietly discard stale assumptions and rescore.

## Session intent

Long-term play style and short-term session intent are different.

A future lightweight session modifier may include:

- Normal
- AFK
- 30 minutes
- Focused

This should influence scoring temporarily without rewriting onboarding preferences.

## Timers and opportunities

Candidate opportunities include:

- birdhouse runs
- herb runs
- tree runs
- farming contracts
- relevant clue opportunities
- other recurring UIM tasks

Ready opportunities should normally enter a queue rather than interrupt immediately.

Prefer surfacing them at natural task boundaries such as:

- target level reached
- Slayer task completed
- boss trip ended
- quest completed
- current activity stopped
- significant inventory transition
- player requests a new suggestion

## Wise Old Man

WOM integration is optional.

Possible uses:

- progress history
- XP graphs
- SOTW/BOTW competition context
- personal bests

Competition focus is a scoring factor, not an override.

A competition should win only when it reasonably aligns with the account's milestone, setup, risk, and transition cost.

## Engagement and celebration

Celebrate real OSRS achievements better. Do not invent replacement rewards.

Appropriate celebration levels:

### Small

- prerequisite level reached
- preparation completed
- timer task completed

### Medium

- quest prerequisite completed
- useful POH/STASH unlock
- significant milestone skill threshold
- first meaningful boss kill

### Large

- Recipe for Disaster
- Fire Cape
- Song of the Elves
- Quest Cape
- 2000 Total
- Max

Possible settings:

- milestone animations
- achievement sounds
- level celebrations
- personal best notifications
- celebration intensity: Subtle / Standard / High

Avoid fake currencies, random rewards, streak punishment, login rewards, and FOMO.

## Visual direction

Working aesthetic: **Modern Gielinor Utility**.

Characteristics:

- RuneLite-native dark charcoal shell
- warm gold/amber primary accent
- soft white primary text
- muted gray secondary text
- muted green for verified/success
- amber for caution
- red only for genuine danger
- OSRS item/skill icons where they reduce text
- thin separators
- minimal card clutter
- compact spacing
- restrained animation

The primary recommendation should receive most of the visual attention.

Avoid:

- neon RGB
- heavy gradients
- glassmorphism
- large web-dashboard cards
- constant pulsing/shimmering
- excessive navigation

## Safety-sensitive UX

Dangerous deathpile/deathbank recommendations should be rare and visually distinct.

When surfaced, show:

- clear danger label
- current assumptions
- potential item-loss consequence
- relevant state confidence
- explicit acknowledgement when required

The plugin cannot guarantee item safety and should never imply that it can.

## Product success criteria

UIM Atlas is succeeding when:

- a player can glance at the panel and know the best next thing to do
- the recommendation is specific enough to act on
- the recommendation reflects the player's actual inventory and storage
- permanent UIM storage progression is valued correctly
- the plugin knows when it is uncertain
- risky mechanics are conservative and understandable
- specialist plugins still do specialist jobs
- the player spends more time playing OSRS than managing the plugin
