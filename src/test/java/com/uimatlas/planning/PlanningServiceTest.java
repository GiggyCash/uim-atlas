package com.uimatlas.planning;

import com.uimatlas.data.ProductionGoalCatalog;
import com.uimatlas.data.ProductionMethodCatalog;
import com.uimatlas.recommendation.GoalDefinition;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodScorer;
import com.uimatlas.recommendation.QuestAction;
import com.uimatlas.recommendation.StrategicAction;
import com.uimatlas.recommendation.StrategicDecision;
import com.uimatlas.state.AccountMode;
import com.uimatlas.state.AccountState;
import com.uimatlas.state.ItemContainerState;
import com.uimatlas.state.ItemStack;
import com.uimatlas.state.LocationState;
import com.uimatlas.state.Observation;
import com.uimatlas.state.QuestStatus;
import com.uimatlas.state.SkillState;
import com.uimatlas.ui.AccountSummary;
import com.uimatlas.ui.PlannerViewModel;
import com.uimatlas.ui.UimAtlasPanel;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanningServiceTest
{
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    public void productionServiceLoadsAllMethodsAndTheDataDefinedGoal()
    {
        PlanningService service = production();
        assertEquals(41, service.getMethodCount());
        assertEquals(1, service.getGoals().size());
        assertEquals("goal.recipe_for_disaster", service.getGoals().get(0).getId());
        assertThrows(UnsupportedOperationException.class, service.getGoals()::clear);
    }

    @Test
    public void currentSnapshotDeterministicallySelectsAnActionableMethod()
    {
        PlanningService service = production();
        AccountState account = account(20, emptyInventory(NOW), NOW, false, Map.of());
        PlanningService.Result first = service.plan(account, goalId(service), NOW);
        PlanningService.Result second = service.plan(account, goalId(service), NOW);
        assertEquals(first, second);
        assertEquals(PlanningService.Status.READY, first.getStatus());
        assertEquals(StrategicAction.Kind.METHOD, first.getPrimary().getAction().getKind());
        assertEquals("method.cooking.hosidius_mess_meat_pies", first.getPrimary().getAction().getId());
        assertEquals(StrategicAction.Readiness.READY, first.getPrimary().getAction().getReadiness());
        assertEquals(NOW.plusSeconds(300).plusNanos(1), first.getRefreshAt());
        assertTrue(first.getRefreshFacts().contains("skill.cooking.level"));
    }

    @Test
    public void readyQuestProducesOnlyAManualHandoffPrimary()
    {
        PlanningService service = production();
        PlanningService.Result result = service.plan(account(99, emptyInventory(NOW), NOW, true,
            Map.of(2310, QuestStatus.NOT_STARTED)), goalId(service), NOW);
        assertEquals(StrategicAction.Kind.QUEST_MILESTONE, result.getPrimary().getAction().getKind());
        assertEquals(StrategicAction.Readiness.READY_TO_HANDOFF, result.getPrimary().getAction().getReadiness());
        assertEquals("milestone.rfd.pirate_pete", result.getPrimary().getAction().getId());
        QuestAction action = (QuestAction) result.getPrimary().getAction();
        assertEquals(QuestAction.Handoff.Availability.MANUAL_ONLY, action.getHandoff().getAvailability());
        assertEquals(3.0, result.getPrimary().getScore().orElseThrow().getContributions()
            .get(MethodScorer.Factor.GOAL_PROGRESS), 0.0);
        for (MethodScorer.Factor factor : List.of(MethodScorer.Factor.SETUP_COST,
            MethodScorer.Factor.TRANSITION_COST, MethodScorer.Factor.INVENTORY_DISRUPTION,
            MethodScorer.Factor.RISK, MethodScorer.Factor.UNCERTAINTY))
        {
            assertEquals(0.0, result.getPrimary().getScore().orElseThrow().getContributions().get(factor), 0.0);
        }

        PlanningService.Result unsafeFinale = service.plan(account(99, emptyInventory(NOW), NOW, false, Map.of()),
            goalId(service), NOW);
        assertNull(unsafeFinale.getPrimary());
        assertEquals(PlanningService.ReasonKind.BLOCKED, unsafeFinale.getReason().getKind());
    }

    @Test
    public void unresolvedAndUncoveredCandidatesNeverBecomePrimary()
    {
        PlanningService service = production();
        PlanningService.Result unknown = service.plan(account(20, Observation.unknown(), NOW, false, Map.of()),
            goalId(service), NOW);
        assertNull(unknown.getPrimary());
        assertEquals(PlanningService.Status.NEEDS_INFO, unknown.getStatus());

        PlanningService.Result uncovered = service.plan(account(1, emptyInventory(NOW), NOW, false, Map.of()),
            goalId(service), NOW);
        assertNull(uncovered.getPrimary());
        assertEquals(PlanningService.Status.NEEDS_INFO, uncovered.getStatus());
    }

    @Test
    public void noGoalAndCompletedGoalNeverProduceArtificialActions()
    {
        PlanningService service = production();
        AccountState account = account(99, emptyInventory(NOW), NOW, true,
            Map.of(2316, QuestStatus.FINISHED));
        assertEquals(PlanningService.Status.SELECT_GOAL, service.plan(account, null, NOW).getStatus());
        PlanningService.Result complete = service.plan(account, goalId(service), NOW);
        assertEquals(PlanningService.Status.GOAL_COMPLETE, complete.getStatus());
        assertNull(complete.getPrimary());
        assertTrue(complete.getStrategic().getCandidates().isEmpty());
    }

    @Test
    public void knownMissingUnknownAndStaleHaveDistinctReasons()
    {
        PlanningService service = production();
        Map<Integer, ItemStack> full = new HashMap<>();
        for (int slot = 0; slot < 28; slot++)
        {
            full.put(slot, new ItemStack(995, 1));
        }
        PlanningService.Result missing = service.plan(account(35,
            Observation.verified(new ItemContainerState(full), "scenario inventory", NOW), NOW, false, Map.of()),
            goalId(service), NOW);
        PlanningService.Result unknown = service.plan(account(35, Observation.unknown(), NOW, false, Map.of()),
            goalId(service), NOW);
        PlanningService.Result stale = service.plan(account(35, emptyInventory(NOW.minusSeconds(301)),
            NOW.minusSeconds(301), false, Map.of()), goalId(service), NOW);
        assertEquals(PlanningService.ReasonKind.MISSING_PREPARATION, missing.getReason().getKind());
        assertEquals(PlanningService.ReasonKind.UNKNOWN_REQUIREMENT, unknown.getReason().getKind());
        assertEquals(PlanningService.ReasonKind.REOBSERVATION_REQUIRED, stale.getReason().getKind());
        assertNull(missing.getPrimary());
        assertNull(unknown.getPrimary());
        assertNull(stale.getPrimary());
    }

    @Test
    public void reversingCatalogsDoesNotChangeOutput() throws Exception
    {
        ClassLoader resources = getClass().getClassLoader();
        List<GoalDefinition> goals = new ProductionGoalCatalog().load(resources, questIds(), 333);
        List<MethodDefinition> methods = new ProductionMethodCatalog().loadBundled(resources);
        PlanningService forward = new PlanningService(goals, methods);
        Collections.reverse(goals = new ArrayList<>(goals));
        Collections.reverse(methods = new ArrayList<>(methods));
        PlanningService reverse = new PlanningService(goals, methods);
        AccountState account = account(20, emptyInventory(NOW), NOW, false, Map.of());
        assertEquals(forward.plan(account, goalId(forward), NOW), reverse.plan(account, goalId(reverse), NOW));
    }

    @Test
    public void activeFrontierQuestOutranksReadyGlobalTrainingRegardlessOfCatalogOrder() throws Exception
    {
        PlanningService service = production();
        AccountState smoke = account(Map.of("COOKING", 20, "MINING", 20, "MAGIC", 20, "THIEVING", 20),
            inventory(Map.of(0, new ItemStack(ItemID.BRONZE_PICKAXE, 1)), NOW), NOW, false,
            Map.of(2307, QuestStatus.NOT_STARTED));
        PlanningService.Result result = service.plan(smoke, goalId(service), NOW);
        assertEquals("milestone.rfd.introduction", result.getPrimary().getAction().getId());
        assertEquals(StrategicDecision.Priority.FRONTIER_HANDOFF, result.getPrimary().getPriority());
        for (String activity : List.of("MINING", "MAGIC", "THIEVING"))
        {
            assertTrue(result.getStrategic().getCandidates().stream().anyMatch(value ->
                value.getAction() instanceof StrategicAction.Method
                    && ((StrategicAction.Method) value.getAction()).getResult().getMethod().getActivity().equals(activity)
                    && value.getAction().getReadiness() == StrategicAction.Readiness.READY));
        }
        List<PlanningService.Alternative> readyMethods = result.getAlternatives().stream().filter(value ->
            value.getCandidate().getAction() instanceof StrategicAction.Method
                && value.getCandidate().getAction().getReadiness() == StrategicAction.Readiness.READY)
            .collect(Collectors.toList());
        assertFalse(readyMethods.isEmpty());
        assertTrue(readyMethods.stream().allMatch(value ->
                value.getReason().getKind() == PlanningService.ReasonKind.LOWER_PRIORITY
                    && value.getReason().getDescription().contains("directly available goal milestone")));

        List<GoalDefinition> goals = new ArrayList<>(service.getGoals());
        List<MethodDefinition> methods = new ArrayList<>(
            new ProductionMethodCatalog().loadBundled(getClass().getClassLoader()));
        Collections.reverse(goals);
        Collections.reverse(methods);
        PlanningService reversed = new PlanningService(goals, methods);
        assertEquals(result, reversed.plan(smoke, goalId(reversed), NOW));
    }

    @Test
    public void directFrontierUnblockerOutranksGeneralTrainingButGeneralTrainingRemainsEligible()
    {
        PlanningService service = production();
        AccountState blockedByCooking = account(Map.of("COOKING", 20, "MINING", 20, "MAGIC", 20),
            inventory(Map.of(0, new ItemStack(ItemID.BRONZE_PICKAXE, 1)), NOW), NOW, false,
            Map.of(2310, QuestStatus.NOT_STARTED));
        PlanningService.Result unblocker = service.plan(blockedByCooking, goalId(service), NOW);
        StrategicAction.Method action = (StrategicAction.Method) unblocker.getPrimary().getAction();
        assertEquals("COOKING", action.getResult().getMethod().getActivity());
        assertEquals(StrategicDecision.Priority.FRONTIER_UNBLOCKER, unblocker.getPrimary().getPriority());
        assertTrue(unblocker.getPrimary().getFrontierRequirements().stream().anyMatch(value ->
            value.getScopeId().equals("milestone.rfd.pirate_pete")
                && value.getRequirement().getFact().equals("skill.cooking.level")));

        AccountState noDirectAction = account(Map.of("MINING", 20),
            inventory(Map.of(0, new ItemStack(ItemID.BRONZE_PICKAXE, 1)), NOW), NOW, false,
            Map.of(2307, QuestStatus.NOT_STARTED, 17, QuestStatus.NOT_STARTED));
        PlanningService.Result general = service.plan(noDirectAction, goalId(service), NOW);
        assertEquals(StrategicAction.Kind.METHOD, general.getPrimary().getAction().getKind());
        assertEquals(StrategicDecision.Priority.GOAL_PROGRESS, general.getPrimary().getPriority());
        assertEquals("MINING", ((StrategicAction.Method) general.getPrimary().getAction())
            .getResult().getMethod().getActivity());
    }

    @Test
    public void methodPresentationShowsExactTargetStartAndOnlyStructuredExplanation() throws Exception
    {
        PlanningService service = production();
        AccountState account = account(Map.of("MINING", 20),
            inventory(Map.of(0, new ItemStack(ItemID.BRONZE_PICKAXE, 1)), NOW), NOW, false,
            Map.of(2307, QuestStatus.NOT_STARTED, 17, QuestStatus.NOT_STARTED));
        PlannerViewModel model = PlannerViewModel.from(AccountSummary.from(account),
            service.plan(account, goalId(service), NOW));
        assertEquals("SETUP READY", model.getStatus());
        assertEquals("Mount Karuulm surface mine", model.getStart());
        assertEquals("Recipe for Disaster still needs 50 Mining. Your setup is ready.", model.getReason());
        String explanation = String.join(" ", model.getWhy());
        assertTrue(explanation.contains("50 Mining"));
        assertTrue(explanation.contains("bronze pickaxe currently carried"));
        assertTrue(explanation.contains("Start: Mount Karuulm surface mine."));
        assertTrue(explanation.contains("Travel and current proximity have not been verified."));
        assertFalse(explanation.contains("no sustained powermining"));
        assertFalse(explanation.contains("review boundary"));
        assertFalse(explanation.contains("A compact, active iron-mining start"));
        AtomicReference<UimAtlasPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            panel.set(new UimAtlasPanel());
            panel.get().setSize(225, 700);
            panel.get().render(model);
            layout(panel.get());
        });
        assertTrue(textAreas(panel.get()).stream().anyMatch(value ->
            value.getText().contains("Recipe for Disaster still needs 50 Mining")));
    }

    @Test
    public void alternativeReasonsUsePriorityAndScoreEvidenceAndNarrowPanelWraps() throws Exception
    {
        PlanningService service = production();
        AccountState agility = account(Map.of("AGILITY", 40), emptyInventory(NOW), NOW, false,
            Map.of(2307, QuestStatus.NOT_STARTED, 17, QuestStatus.NOT_STARTED));
        PlanningService.Result result = service.plan(agility, goalId(service), NOW);
        PlanningService.Alternative varrock = result.getAlternatives().stream().filter(value ->
            value.getCandidate().getAction().getId().equals("method.agility.varrock_rooftop"))
            .findFirst().orElseThrow();
        assertEquals(PlanningService.ReasonKind.SCORE_DISADVANTAGE, varrock.getReason().getKind());
        assertEquals("Higher risk.", varrock.getReason().getDescription());
        assertTrue(result.getAlternatives().stream().noneMatch(value ->
            value.getReason().getDescription().equals("Lower strategic score for this snapshot.")));

        AccountState smoke = account(Map.of("COOKING", 20, "MINING", 20, "MAGIC", 20, "THIEVING", 20),
            inventory(Map.of(0, new ItemStack(ItemID.BRONZE_PICKAXE, 1)), NOW), NOW, false,
            Map.of(2307, QuestStatus.NOT_STARTED));
        PlannerViewModel model = PlannerViewModel.from(AccountSummary.from(smoke),
            service.plan(smoke, goalId(service), NOW));
        assertEquals("HANDOFF", model.getStatus());
        assertTrue(model.getReason().contains("active Recipe for Disaster frontier"));
        assertTrue(String.join(" ", model.getWhy()).contains("have not been verified"));
        assertEquals("Use Quest Helper to continue this quest.", model.getHandoff());

        AtomicReference<UimAtlasPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            panel.set(new UimAtlasPanel());
            panel.get().setSize(225, 900);
            panel.get().render(model);
            layout(panel.get());
        });
        List<JTextArea> text = textAreas(panel.get());
        SwingUtilities.invokeAndWait(() ->
        {
            text.forEach(value -> value.getParent().setVisible(true));
            layout(panel.get());
        });
        assertTrue(text.stream().anyMatch(value -> value.getText().contains("directly available goal milestone")));
        assertTrue(text.stream().noneMatch(value -> value.getText().contains(" · HANDOFF")
            || value.getText().contains(" · SETUP READY")));
        List<JTextArea> laidOut = text.stream().filter(value -> value.getParent().getWidth() > 0)
            .collect(Collectors.toList());
        assertFalse(laidOut.isEmpty());
        assertTrue(laidOut.stream().allMatch(value -> value.getLineWrap()
            && value.getWrapStyleWord() && value.getPreferredSize().width <= value.getParent().getWidth()));
    }

    @Test
    public void resetAndLoadingSnapshotsCannotRetainAPriorRecommendation()
    {
        PlanningService service = production();
        PlanningService.Result ready = service.plan(account(20, emptyInventory(NOW), NOW, false, Map.of()),
            goalId(service), NOW);
        assertNotNull(ready.getPrimary());
        PlanningService.Result reset = service.plan(AccountState.empty(), goalId(service), NOW.plusSeconds(1));
        assertNull(reset.getPrimary());
        assertNull(reset.getStrategic());
        assertEquals(PlanningService.Status.WAITING_FOR_ACCOUNT, reset.getStatus());
    }

    @Test
    public void viewModelExplainsMethodsQuestsAndDistinctUnknownStates()
    {
        PlanningService service = production();
        AccountState methodAccount = account(20, emptyInventory(NOW), NOW, false, Map.of());
        PlannerViewModel method = PlannerViewModel.from(AccountSummary.from(methodAccount),
            service.plan(methodAccount, goalId(service), NOW));
        assertEquals("SETUP READY", method.getStatus());
        assertTrue(method.getReason().contains("70 Cooking"));
        assertFalse(method.getWhy().isEmpty());
        assertTrue(method.getAlternatives().size() <= 5);

        AccountState questAccount = account(99, emptyInventory(NOW), NOW, true,
            Map.of(2310, QuestStatus.NOT_STARTED));
        PlannerViewModel quest = PlannerViewModel.from(AccountSummary.from(questAccount),
            service.plan(questAccount, goalId(service), NOW));
        assertEquals("HANDOFF", quest.getStatus());
        assertEquals("Use Quest Helper to continue this quest.", quest.getHandoff());

        Map<Integer, ItemStack> full = new HashMap<>();
        for (int slot = 0; slot < 28; slot++) { full.put(slot, new ItemStack(995, 1)); }
        AccountState missingAccount = account(35,
            Observation.verified(new ItemContainerState(full), "scenario inventory", NOW), NOW, false, Map.of());
        PlannerViewModel missing = PlannerViewModel.from(AccountSummary.from(missingAccount),
            service.plan(missingAccount, goalId(service), NOW));
        AccountState unknownAccount = account(35, Observation.unknown(), NOW, false, Map.of());
        PlannerViewModel unknown = PlannerViewModel.from(AccountSummary.from(unknownAccount),
            service.plan(unknownAccount, goalId(service), NOW));
        assertTrue(missing.getReason().startsWith("Preparation unresolved:"));
        assertTrue(unknown.getReason().startsWith("Atlas cannot verify:"));
    }

    @Test
    public void panelSelectionAndResetReplaceDisplayedRecommendation() throws Exception
    {
        PlanningService service = production();
        AccountState account = account(20, emptyInventory(NOW), NOW, false, Map.of());
        PlannerViewModel ready = PlannerViewModel.from(AccountSummary.from(account),
            service.plan(account, goalId(service), NOW));
        PlannerViewModel waiting = PlannerViewModel.from(AccountSummary.from(AccountState.empty()),
            service.plan(AccountState.empty(), goalId(service), NOW.plusSeconds(1)));
        AtomicReference<String> selected = new AtomicReference<>();
        AtomicReference<UimAtlasPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            panel.set(new UimAtlasPanel(selected::set));
            panel.get().render(ready);
            panel.get().selectGoal(goalId(service));
            panel.get().render(waiting);
        });
        assertEquals(goalId(service), selected.get());
        assertEquals("NEEDS INFO", panel.get().getDisplayed().getStatus());
        assertEquals("Waiting for account state.", panel.get().getDisplayed().getNext());
    }

    private static PlanningService production()
    {
        return PlanningService.loadProduction(PlanningServiceTest.class.getClassLoader(), questIds(), 333);
    }

    private static Set<Integer> questIds()
    {
        return Arrays.stream(Quest.values()).map(Quest::getId).collect(Collectors.toSet());
    }

    private static String goalId(PlanningService service)
    {
        return service.getGoals().get(0).getId();
    }

    private static Observation<ItemContainerState> emptyInventory(Instant time)
    {
        return Observation.verified(new ItemContainerState(Map.of()), "scenario inventory", time);
    }

    private static Observation<ItemContainerState> inventory(Map<Integer, ItemStack> slots, Instant time)
    {
        return Observation.verified(new ItemContainerState(slots), "scenario inventory", time);
    }

    @SuppressWarnings("deprecation")
    private static AccountState account(int cooking, Observation<ItemContainerState> inventory,
        Instant observedAt, boolean combatReady, Map<Integer, QuestStatus> overrides)
    {
        return account(Map.of("COOKING", cooking), inventory, observedAt, combatReady, overrides);
    }

    @SuppressWarnings("deprecation")
    private static AccountState account(Map<String, Integer> levels, Observation<ItemContainerState> inventory,
        Instant observedAt, boolean combatReady, Map<Integer, QuestStatus> overrides)
    {
        Map<String, SkillState> skills = new LinkedHashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill != Skill.OVERALL)
            {
                int level = levels.getOrDefault(skill.name(), 99);
                skills.put(skill.name(), new SkillState(level, level, 0));
            }
        }
        Map<Integer, QuestStatus> quests = Arrays.stream(Quest.values()).collect(Collectors.toMap(
            Quest::getId, ignored -> QuestStatus.FINISHED, (left, right) -> left, LinkedHashMap::new));
        quests.put(2316, QuestStatus.NOT_STARTED);
        quests.putAll(overrides);
        Map<String, Boolean> capabilities = Map.of("capability.combat.rfd_no_prayer", combatReady);
        return AccountState.builder().loggedIn(true)
            .accountMode(Observation.verified(AccountMode.ULTIMATE_IRONMAN, "scenario mode", observedAt))
            .skills(Observation.map(skills, "scenario skills", observedAt))
            .quests(Observation.map(quests, "scenario quests", observedAt).lastObserved())
            .questPoints(Observation.verified(333, "scenario Quest points", observedAt).lastObserved())
            .capabilities(Observation.map(capabilities, "scenario capabilities", observedAt))
            .inventory(inventory)
            .equipment(Observation.verified(new ItemContainerState(Map.of()), "scenario equipment", observedAt))
            .location(Observation.verified(new LocationState(301, Set.of(WorldType.MEMBERS.name()),
                3200, 3200, 0, 12850, -1, false), "scenario location", observedAt)).build();
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents())
        {
            if (child instanceof Container)
            {
                layout((Container) child);
            }
        }
    }

    private static List<JTextArea> textAreas(Container container)
    {
        List<JTextArea> result = new ArrayList<>();
        for (Component child : container.getComponents())
        {
            if (child instanceof JTextArea)
            {
                result.add((JTextArea) child);
            }
            if (child instanceof Container)
            {
                result.addAll(textAreas((Container) child));
            }
        }
        return result;
    }
}
