# Route target audit v1

Reviewed 2026-09-15. This audit covers all 41 production methods present in UIM Atlas at review time. A route target is a stable destination for an explicit Shortest Path request; it is not proof that travel is available, safe, optimal, or already underway.

## Protocol audit

The current Plugin Hub marker pins [Shortest Path](https://github.com/Skretzo/shortest-path) at revision [`9953d52745f711a38c9cdd4a00bb1d0d57d1fdea`](https://github.com/Skretzo/shortest-path/tree/9953d52745f711a38c9cdd4a00bb1d0d57d1fdea). At that exact revision, [`ShortestPathPlugin.onPluginMessage`](https://github.com/Skretzo/shortest-path/blob/9953d52745f711a38c9cdd4a00bb1d0d57d1fdea/src/main/java/shortestpath/ShortestPathPlugin.java#L508) accepts:

- namespace `shortestpath`
- message `path`
- payload key `start`, accepting a RuneLite `WorldPoint`
- payload key `target`, accepting a RuneLite `WorldPoint`

The source has a separate `clear` message, but no route ownership token, acceptance acknowledgement, availability query, or handshake. Atlas therefore sends only `path`, only after a click, and never sends `clear`. A successful local post is described only as “Route requested.”

[Quest Helper revision `a52646118f0e5ea63a6b3331cefa98087a7b4d6c`](https://github.com/Zoinkwiz/quest-helper/tree/a52646118f0e5ea63a6b3331cefa98087a7b4d6c) independently demonstrates the same `shortestpath` / `path` / `start` / `target` `PluginMessage` shape in [`DetailedQuestStep`](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/steps/DetailedQuestStep.java#L890). RuneLite 1.12.38 defines [`PluginMessage`](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/events/PluginMessage.java) as the event intended for Plugin Hub inter-plugin data.

## Coordinate provenance

The following short source labels are used in the table:

- **QH** — a matching activity/object/NPC point in Quest Helper revision `a52646118f0e5ea63a6b3331cefa98087a7b4d6c`. Relevant sources include the [Kourend Easy diary](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendEasy.java), [Kourend Hard diary](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendHard.java), [Ardougne diary](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/achievementdiaries/ardougne/ArdougneEasy.java), [Varrock fishing point](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockEasy.java), [Varrock anvil](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/achievementdiaries/varrock/VarrockElite.java), [Canifis](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/skills/agility/Canifis.java), [Draynor](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/skills/agility/DraynorVillage.java), [Varrock rooftop](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/skills/agility/Varrock.java), [woodcutting](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/skills/woodcutting/WoodcuttingMember.java), and the [Arceuus Library book-search step](https://github.com/Zoinkwiz/quest-helper/blob/a52646118f0e5ea63a6b3331cefa98087a7b4d6c/src/main/java/com/questhelper/helpers/quests/bearyoursoul/BearYourSoul.java).
- **RL** — a stable map, minigame, mooring, dungeon, or clue point in RuneLite tag [`runelite-parent-1.12.38`](https://github.com/runelite/runelite/tree/runelite-parent-1.12.38), matching the client version pinned by this project. Relevant sources are [`MinigameLocation`](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/MinigameLocation.java), [`DungeonLocation`](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/DungeonLocation.java), [`MooringLocation`](https://github.com/runelite/runelite/blob/runelite-parent-1.12.38/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/MooringLocation.java), and the clue-location data.
- **Unsupported** — no sufficiently precise stable destination was established from those reviewed sources. No coordinate is inferred from the human-readable location.

## Method-by-method result

| Method ID | Human-readable location | Route target | Kind | Source / rationale |
| --- | --- | --- | --- | --- |
| `method.agility.canifis_rooftop` | Canifis | `3507, 3489, 0` | Exact start obstacle | QH Canifis rooftop start tree. |
| `method.agility.draynor_rooftop` | Draynor Village | `3103, 3279, 0` | Exact start obstacle | QH Draynor rooftop rough wall. |
| `method.agility.varrock_rooftop` | Varrock | `3221, 3414, 0` | Exact start obstacle | QH Varrock rooftop rough wall. |
| `method.construction.limestone_attack_stones` | Mort'ton supply loop and POH games room | Absent | — | Unsupported: this is a multi-site loop ending in an instanced POH, not one stable activity point. |
| `method.construction.mahogany_homes.adept` | Falador, south of the park | `2989, 3363, 0` | Arrival/contact | RL Mahogany Homes Falador map point. Contracts themselves vary. |
| `method.construction.mahogany_homes.novice` | Falador, south of the park | `2989, 3363, 0` | Arrival/contact | RL Mahogany Homes Falador map point. Contracts themselves vary. |
| `method.cooking.hosidius_mess_meat_pies` | Hosidius Mess servery | `1646, 3631, 0` | Exact building | RL Hosidius mess-hall clue point. |
| `method.cooking.hosidius_mess_pineapple_pizzas` | Hosidius Mess servery | `1646, 3631, 0` | Exact building | RL Hosidius mess-hall clue point. |
| `method.cooking.jugs_of_wine_carried` | Current safe location | Absent | — | Unsupported: deliberately performed wherever the player currently has a safe setup. |
| `method.crafting.molten_glass` | Edgeville furnace | Absent | — | Unsupported: a precise furnace interaction point was not established from the reviewed pinned sources. |
| `method.crafting.oil_lamp` | Current location | Absent | — | Unsupported: current-location processing has no external destination. |
| `method.crafting.vial` | Current location | Absent | — | Unsupported: current-location processing has no external destination. |
| `method.firemaking.carried_oak_logs` | A clear safe east-west line | Absent | — | Unsupported: suitability changes with occupancy and obstacles; no single stable line is encoded. |
| `method.firemaking.carried_regular_logs` | A clear safe east-west line | Absent | — | Unsupported: suitability changes with occupancy and obstacles; no single stable line is encoded. |
| `method.fishing.fly_barbarian_village` | River Lum east of Barbarian Village | `3106, 3428, 0` | Exact activity area | QH matching River Lum fishing point. |
| `method.fishing.tempoross_mass` | Tempoross Cove via the Ruins of Unkah | `3143, 2824, 0` | Surface arrival/entrance | RL Ruins of Unkah mooring; the activity is reached from this stable surface arrival. |
| `method.fletching.arrow_shafts_regular_logs` | Current safe location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.fletching.headless_arrows` | Current safe location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.herblore.attack_potion` | Current location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.herblore.clean_guam` | Current location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.herblore.energy_potion` | Current location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.herblore.mixology.mammoth_might_order` | Alchemical Society, Aldarin | `1388, 2920, 0` | Surface entrance | RL Mastering Mixology surface map point; no underground target is asserted. |
| `method.herblore.prayer_potion` | Current location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.herblore.stamina_potion` | Current location | Absent | — | Unsupported: carried-item processing has no external destination. |
| `method.magic.arceuus_library_books` | Arceuus Library | `1632, 3808, 0` | Exact building | QH Arceuus Library book-search point. |
| `method.magic.fire_strike_sand_crabs` | Hosidius sand-crab coast | `1739, 3468, 0` | Exact activity area | QH matching sand-crab point. |
| `method.magic.mta_telekinetic_theatre` | Mage Training Arena north of Emir's Arena | `3362, 3318, 0` | Surface entrance | RL Mage Training Arena map point; no theatre-instance coordinate is asserted. |
| `method.mining.calcified_cam_torum` | Cam Torum mine | `1435, 3131, 0` | Surface entrance | RL Cam Torum dungeon map point; no underground mining coordinate is asserted. |
| `method.mining.iron_mount_karuulm` | Mount Karuulm surface mine | `1275, 3817, 0` | Exact activity point | QH matching Mount Karuulm iron rock. |
| `method.mining.shooting_star` | Currently verified non-Wilderness star site | Absent | — | Unsupported: the destination is dynamic and observation-dependent. |
| `method.ranged.bronze_darts_sand_crabs` | Hosidius sand-crab coast | `1739, 3468, 0` | Exact activity area | QH matching sand-crab point. |
| `method.ranged.dorgeshuun_crossbow_ammonite_crabs` | Fossil Island ammonite-crab area | Absent | — | Unsupported: the activity spans multiple crab areas and access assumptions; no single reviewed start was selected. |
| `method.smithing.bronze_bar_edgeville` | Edgeville furnace | Absent | — | Unsupported: a precise furnace interaction point was not established from the reviewed pinned sources. |
| `method.smithing.bronze_knives_varrock` | Anvil south of Varrock west bank | `3188, 3426, 0` | Exact activity point | QH matching Varrock anvil point. |
| `method.smithing.iron_platebody_varrock` | Anvil south of Varrock west bank | `3188, 3426, 0` | Exact activity point | QH matching Varrock anvil point. |
| `method.thieving.east_ardougne_cake_stall` | East Ardougne market | `2668, 3311, 0` | Exact activity point | QH matching cake stall. |
| `method.thieving.hosidius_fruit_stalls` | Eastern Hosidius beach house | `1767, 3597, 0` | Exact activity point | QH matching fruit stall. |
| `method.thieving.stealing_artefacts` | Port Piscarilius foodhall | `1845, 3753, 0` | Contact/start | QH Captain Khaled point. Delivery targets vary by assignment. |
| `method.woodcutting.oak_trees_draynor` | Draynor Village | Absent | — | Unsupported: the label covers several trees and no exact reviewed tree was selected. |
| `method.woodcutting.regular_trees_lumbridge` | Lumbridge surface | `3192, 3223, 0` | Exact activity point | QH matching Lumbridge regular tree. |
| `method.woodcutting.teak_castle_wars` | South-west of Castle Wars | `2335, 3048, 0` | Exact activity point | QH matching teak tree. |

Result: **23 present, 18 deliberately absent**. Missing data is intentional and produces no Route action.
