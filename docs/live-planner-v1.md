# Live Planner v1

Reviewed: 2026-09-15

## Product boundary

Live Planner v1 connects the existing production planner to the RuneLite sidebar. It loads the indexed production goal and method resources, evaluates one current `AccountState`, and presents one primary action only when the existing strategic result is already actionable. It does not add goals, methods, acquisition routes, storage plans, combat tactics, gameplay automation or executable plugin integrations.

The live composition is:

```text
RuneLiteAccountObserver -> AccountState -> PlanningService
  -> AccountStateFacts -> GoalContext -> StrategicDecision
  -> PlannerViewModel -> UimAtlasPanel
```

`PlanningService` loads goals through `uimatlas/goals/catalogs.txt` and all methods through `uimatlas/methods/catalogs.txt`. The bundled `uimatlas/items/catalog-item-ids-1.12.38.txt` is the strict runtime item-ID boundary; build tests require it to equal every exact item fact in the production catalogs and verify each entry against the pinned public RuneLite `ItemID` API. Java contains no selected skill list, selected method list or Recipe-for-Disaster planning branch.

## Live Planner v1.1 smoke-test corrections

The first real-client recommendation exposed two separate defects. Strategic candidates all had binary goal progress and were sorted only by their shared score, so long-term aggregate skill training could tie or outrank a milestone that the evaluated graph already marked `AVAILABLE`. The UI then labelled a method `READY` despite having no location-compatibility proof, and fixed-width Swing HTML allowed target and option text to clip.

The generic correction retains score arithmetic and adds structural priority before score: `FRONTIER_HANDOFF`, `FRONTIER_UNBLOCKER`, then `GOAL_PROGRESS`. `FRONTIER_HANDOFF` comes from an available milestone. `FRONTIER_UNBLOCKER` requires a matched missing prerequisite on a blocked milestone whose dependencies are all complete. Unknown or dependency-blocked milestones cannot create this priority. Score, action kind and stable ID continue to order candidates inside one tier.

Method presentation now says `SETUP READY`, shows `start.location` separately, and states that travel/current proximity is unverified. Goal and recheck text comes from the matched requirement. Setup text comes from verified requirement checks. Raw method reasons, cost assumptions, start instructions and editorial stop-condition descriptions are not rendered. Plain wrapping text areas measure their actual parent width, and every alternative renders title, status and reason on separate lines.

## Recommendation eligibility

A live primary recommendation requires all of these:

1. the selected goal is incomplete;
2. the candidate has positive derived goal progress;
3. the existing strategic score is present;
4. readiness is exactly `READY` for a method or `READY_TO_HANDOFF` for a quest milestone.

`READY_WITH_PREP`, unresolved preparation, unknown requirements, stale/future facts, hard blockers, missing scores and coverage gaps cannot occupy the primary slot. The normal no-result text is: “Atlas needs more information before recommending a next action.” A structured reason distinguishes known missing preparation, unknown state, stale state, blockers, absent scoring inputs and uncovered actions.

Live Planner v1.1 orders eligible actions by generic goal proximity before comparing scores: an available milestone on the active frontier, then a method that directly satisfies a missing requirement on a blocked frontier milestone, then general goal-level progress. The existing score remains the comparator within each tier. Unknown milestones cannot create unblocker priority, and tiers never bypass actionability.

## Scoring-input provenance

The existing scorer weights and arithmetic are unchanged.

### Production methods

| Factor | Live Planner v1 value and provenance | Classification |
| --- | --- | --- |
| `GOAL_PROGRESS` | `GoalContext` combines a verified current unmet `skill.<activity>.level` goal requirement with the method's data-defined activity and current skill gate. | Verified account state + production goal/method data |
| `STORAGE_UNLOCK_VALUE` | Explicit `0`. No account-specific live storage-benefit provider exists, and all current production records also declare neutral storage value. Zero disables this reward; it cannot make a candidate win. | Deliberately unavailable/conservatively neutral |
| `CURRENT_INVENTORY_FIT` | `SetupScoringInputs` derives it from current trusted inventory/equipment facts and data-defined setup/consume/free-slot requirements. | Verified account state + production method data |
| `METHOD_EFFICIENCY` | `MethodEfficiency` supplies it only from the highest applicable production profile whose requirements are currently verified. No applicable profile means no score. Trusted XP is display metadata from that selected profile only. | Verified account state + production method data |
| `SETUP_COST` | `SetupScoringInputs` derives it from current carried deficits and data-defined setup requirements/cost metadata. Unknown inputs leave derivation unresolved. | Verified account state + production method data |
| `TRANSITION_COST` | Derived by the same setup component from current setup burden, free-slot pressure and data-defined transition metadata. | Verified account state + production method data |
| `INVENTORY_DISRUPTION` | Derived from observed free-slot pressure and the data-defined disruption hint. | Verified account state + production method data |
| `RISK` | The production method's validated danger floor is supplied and the scorer enforces the same floor. No extra account-specific danger or combat-safety estimate is invented. Unknown safety requirements still block actionability. | Production method data; contextual live risk unavailable |
| `UNCERTAINTY` | Explicit `0` in the scorer's narrow optional-estimate sense because Live Planner adds no separate estimate. Required unknown, stale, future or conflicted state still blocks evaluation, profile selection, scoring or actionability before this factor can help. | Deliberately unavailable/neutral only for optional estimate uncertainty |

Session fit is not a current `MethodScorer` factor and Live Planner v1 has no session provider. Goal value is represented only by the existing binary `GOAL_PROGRESS`; no distance, urgency or invented goal reward is added.

### Quest milestones

| Factor | Live Planner v1 value and provenance | Classification |
| --- | --- | --- |
| `GOAL_PROGRESS` | `1` only when the data-defined milestone completion is currently verified unmet. Unknown completion awards nothing. | Verified account state + production goal data |
| `SETUP_COST` | `0` for displaying a manual handoff. | Manual-handoff action boundary |
| `TRANSITION_COST` | `0` because the panel performs no travel, click or state transition. | Manual-handoff action boundary |
| `INVENTORY_DISRUPTION` | `0` because the handoff moves no item. | Manual-handoff action boundary |
| `RISK` | `0` for displaying text; it is not a claim that quest execution is safe. Encoded strategic safety requirements still gate readiness. | Manual-handoff action boundary; execution risk unavailable |
| `UNCERTAINTY` | `0` for the deterministic display action. Quest-item and combat preflight remain explicitly outside this score. | Manual-handoff action boundary; execution uncertainty unavailable |

The quest action declares `availability: MANUAL_ONLY`. The panel says “Use Quest Helper to continue this quest” and states that quest item and combat readiness remain for manual preflight. It does not open Quest Helper. Tests assert all five zero inputs contribute exactly zero, the handoff remains manual, and the RFD finale cannot become primary while its encoded combat-readiness capability is false or unknown.

## Panel behavior

The panel shows:

- a generic goal selector populated from loaded goal definitions;
- one `Next`, `Reason` and `Status` result;
- a collapsible “Why this?” explanation using matched goal requirements, verified setup facts and structured start data;
- a collapsible list of at most five alternatives labelled `SETUP READY`, `HANDOFF`, `NEEDS PREP`, `UNKNOWN` or `BLOCKED` with a structured loss reason.

Recipe for Disaster is the only production option today. Selection is transient and stores only its generic goal ID; it is not associated with an account and is not persisted. No selection shows a goal-selection state. Verified goal completion shows “Recipe for Disaster complete” with no artificial next action.

Method explanations include the matched target, structured start location, trusted selected-profile XP range where available, a target-derived recheck point, verified setup requirements and an explicit statement that travel/current proximity is unverified. Raw catalog reason, assumptions, start instruction and editorial stop-condition prose remain off the player surface. `SETUP READY` means the encoded requirements and setup are ready; it does not verify current location. Mahogany Homes receives no special Java path. Quest explanations describe only verified strategic prerequisites and the manual Quest Helper continuation.

## Refresh, freshness and account lifecycle

RuneLite events mark skill, inventory, equipment, quest and supported container families dirty. A game tick refreshes the immutable snapshot on RuneLite's client thread and recalculates only when relevant state changed or the planner's earliest relevant fact expiry is due. The expiry path maps generic fact namespaces back to their supported observer family. Planning captures its cutoff after observation so a newly timestamped fact cannot appear to come from the future.

Planning and view-model construction run outside Swing. Only the immutable `PlannerViewModel` crosses to Swing's event-dispatch thread, where `UimAtlasPanel` renders it. Swing listeners send only the selected goal ID back to the client thread. No paint callback evaluates the planner and there are no web requests.

Logout, loading, world hop, profile change, RuneScape-profile change, account-hash change and plugin disable/re-enable clear the snapshot and displayed result before another account can contribute facts. The transient account hash is used only as an in-memory reset boundary. No character name, account hash, inventory dump or other personal identifier is persisted or logged. Debug output is change-coalesced and contains only selected goal ID, aggregate method/candidate counts and selected action ID.

Requirements continue to enforce their own provenance, confidence and maximum age. Stale and future observations cannot satisfy them. When a known supported observation approaches expiry, the observer re-reads its source family; a failed re-read remains unknown. Quest state remains `LAST_OBSERVED` under the existing 24-hour production contract.

## Observation gaps deliberately left unknown

Live Planner v1 adds freshness-triggered refresh for already supported generic fact families. It adds no new game-state inference. The following remain unsupported unless already established by the existing observer:

- RFD finale combat/no-Prayer readiness;
- method access/activity flags such as Cam Torum, crashed-star accessibility and Mixology order state;
- diary and quest-derived capability aliases used by some method records;
- benchmark setup, restock-budget, noted-supply, log-basket, travel and run-support capabilities;
- account-specific storage value, acquisition feasibility, disposal, death storage, looting-bag state and long-term combat-resource sustainability;
- current quest item loadouts and quest combat safety.

Exact current skills, inventory, equipment, quests, Quest points, carried item alternatives, free slots, plank-sack state and positive POH ownership remain available through the existing supported observations. A production family may have coverage while every candidate for one snapshot still remains unresolved.

## Deliberate no-recommendation cases

Live Planner returns no primary action when there is no selected goal, the client is logged out/loading, account mode is unknown, the account is not UIM, the goal is complete, no covered method currently advances an unmet target, or every relevant method/quest is blocked, unscored, stale, unknown or needs unresolved preparation. This also includes combat methods without their bounded current ammunition/runes, methods without an applicable verified efficiency profile, and the RFD finale without verified encoded combat readiness.

## Responsibility and size review

`PlanningService` remains one cohesive production composition and policy boundary: catalog ownership, one-snapshot orchestration, live-primary eligibility, structured candidate reasons and relevant-fact expiry. Moving one of those private traversals into a new class would reduce its line count while increasing total Java and adding another boundary with no independent caller. Presentation mapping is already isolated in `PlannerViewModel`, Swing rendering in `UimAtlasPanel`, and RuneLite observation in `RuneLiteAccountObserver`; no further extraction is justified for v1.

Live Planner grows beyond the preferred milestone estimate because the first production-facing slice needs four distinct boundaries that were previously absent: strict runtime catalog bootstrapping, structured planning output/freshness, presentation mapping, and event/lifecycle integration. V1.1 adds one small structural-priority concept to `StrategicDecision` because the live failure demonstrated that shared score alone discarded existing goal-frontier meaning. It does not change scorer arithmetic, goal/method loaders or game-data Java.

## Automated verification scope

Tests cover production loading, deterministic and reversed-order results, actionable method and manual quest primaries, unresolved and no-action results, goal selection/completion, known-missing versus unknown versus stale explanations, safety-blocked finale behavior, panel reset, observer freshness-family refresh, account lifecycle reset, all-13 RFD skill coverage, Construction, Herblore, strategic decisions, unchanged scorer weights, privacy and JAR production/test separation.

Live Planner v1 baseline verification on 2026-09-15:

- `./gradlew clean build --no-daemon`: passed in 26 seconds;
- all 200 tests: passed, with zero failures, errors or skips;
- `javaSourceSize`: passed;
- core Java: 40 files / 4,272 lines / 174,125 characters / about 43,532 tokens before; 43 files / 5,283 lines / 216,188 characters / about 54,047 tokens after; delta +3 files / +1,011 lines / +42,063 characters / about +10,515 tokens;
- production resources: 16 files / 245,728 bytes before; 18 files / 246,124 bytes after; delta +2 files / +396 bytes;
- `git diff --check`: passed;
- privacy scans for developer paths/usernames and non-placeholder email addresses: no matches;
- JAR: 311,032 bytes, all 14 indexed production method JSON catalogs present, the RFD goal plus goal index and 75-entry item-ID boundary present, and no synthetic/fixture/test resource entries;
- no commit or push performed.

Live Planner v1.1 correction verification on 2026-09-15:

- `./gradlew clean build --no-daemon`: passed in 26 seconds;
- all 204 tests: passed, with zero failures, errors or skips;
- `javaSourceSize`: passed;
- core Java: 43 files / 5,283 lines / 216,188 characters / about 54,047 tokens before; 43 files / 5,447 lines / 224,743 characters / about 56,186 tokens after; delta +164 lines / +8,555 characters / about +2,139 tokens;
- production resources unchanged at 18 files / 246,124 bytes;
- `git diff --check` and privacy scan: passed;
- scorer arithmetic and weights unchanged;
- JAR: 318,801 bytes / 150 entries, with all 14 indexed method catalogs, the indexed RFD goal and the 75-entry item boundary present; no synthetic/fixture/test resources;
- no commit or push performed.

Ten largest production Java files after v1.1:

| Characters | Lines | File |
| ---: | ---: | --- |
| 21,748 | 381 | `data/MethodDefinitionLoader.java` |
| 20,630 | 441 | `planning/PlanningService.java` |
| 13,363 | 346 | `state/RuneLiteAccountObserver.java` |
| 11,301 | 265 | `ui/PlannerViewModel.java` |
| 10,866 | 262 | `recommendation/ResourceFlow.java` |
| 10,819 | 231 | `data/GoalDefinitionLoader.java` |
| 10,420 | 292 | `ui/UimAtlasPanel.java` |
| 8,698 | 207 | `state/AccountStateFacts.java` |
| 8,689 | 163 | `recommendation/StrategicDecision.java` |
| 8,179 | 169 | `recommendation/SetupScoringInputs.java` |

## Manual RuneLite smoke test

1. Run `./gradlew run`, enable **UIM Atlas**, and open the compass sidebar. Before login, confirm the panel shows a waiting state and no recommendation from a prior session.
2. Log into a UIM with Recipe for Disaster incomplete. Confirm the account state becomes loaded or honestly partial and the goal selector contains **Recipe for Disaster**.
3. Select **Recipe for Disaster**. Confirm the panel shows either one `SETUP READY` method, one `HANDOFF` milestone, or `NEEDS INFO` with a concrete reason. Confirm it never labels unresolved preparation as the primary action.
4. Expand **Why this?**. For a method, compare its matched target and setup statement with current stats/inventory; show an XP range only when its stated profile is verified. For a quest, confirm the panel says to use Quest Helper manually and does not claim item/combat readiness.
5. Expand **Other options**. Confirm no more than five appear and each has a status plus a reason it lost or remains unavailable.
6. Change a relevant carried setup, such as removing/adding a required current material, rune, ammunition or valid tool alternative. Wait for the next game tick and confirm the recommendation or explanation recalculates without closing the panel.
7. Change a relevant skill level if practical, or use an account where one encoded skill target is already satisfied. Confirm that completed skill family no longer receives RFD progress and another unmet family or ready milestone becomes eligible.
8. Open the in-game quest list to refresh quest observations. Confirm a changed prerequisite/milestone state changes the result after the next safe refresh, while unknown or stale quest state does not unlock a handoff.
9. Hop worlds. During loading confirm the prior recommendation disappears; after login confirm the result is rebuilt from the new snapshot.
10. Log out and back in. Confirm the panel waits while logged out and never retains the previous live action during loading.
11. Switch RuneLite/RuneScape profiles or accounts. Confirm the old recommendation clears before the new snapshot loads. Do not inspect or log names/hashes.
12. Disable and re-enable UIM Atlas. Confirm the panel returns to goal selection with no previous recommendation and no duplicate navigation button.
13. If Recipe for Disaster is verified complete on the test account, confirm the panel shows **Recipe for Disaster complete** and no next action.
14. Test a non-UIM account if available. Confirm Live Planner reports the UIM requirement and offers no primary recommendation.

For the exact v1.1 acceptance retest, use the same account state that exposed the defect: keep several aggregate RFD skill targets incomplete while **Another Cook's Quest** is directly available. Confirm the primary is the quest `HANDOFF`, Mining/Magic/Thieving remain secondary `SETUP READY` options, and each explains that a directly available goal milestone has higher priority. Then make the frontier milestone blocked by one trainable skill prerequisite and confirm a ready method for that exact prerequisite moves above unrelated training. Finally, block the frontier with a non-trainable or unknown prerequisite and confirm general goal training can compete without any blocked/unknown action winning.

## Recommended next milestone

Implement a narrowly scoped **Generic Capability Observation Pack v1** for the highest-impact current production capability gaps that can be proven through stable public RuneLite APIs. Start with a source/API audit and add only multi-method facts with deterministic reset/freshness semantics. Keep acquisition, storage, disposal, death mechanics and quest execution outside that milestone.
