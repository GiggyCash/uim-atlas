package com.uimatlas.ui;

import com.uimatlas.planning.PlanningService;
import com.uimatlas.recommendation.GoalState;
import com.uimatlas.recommendation.MethodDefinition;
import com.uimatlas.recommendation.MethodEvaluator;
import com.uimatlas.recommendation.QuestAction;
import com.uimatlas.recommendation.StrategicAction;
import com.uimatlas.recommendation.StrategicDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.Value;

/** Immutable UI text derived from structured planning output; Swing only renders this value. */
@Value
public class PlannerViewModel
{
    @Value
    public static class GoalOption
    {
        String id;
        String displayName;

        @Override
        public String toString()
        {
            return displayName;
        }
    }

    @Value
    public static class Option
    {
        String title;
        String status;
        String reason;
    }

    String account;
    String accountState;
    List<GoalOption> goals;
    String selectedGoalId;
    String status;
    String next;
    String start;
    String reason;
    List<String> why;
    String handoff;
    List<Option> alternatives;

    public PlannerViewModel(String account, String accountState, List<GoalOption> goals,
        String selectedGoalId, String status, String next, String start, String reason, List<String> why,
        String handoff, List<Option> alternatives)
    {
        this.account = account;
        this.accountState = accountState;
        this.goals = List.copyOf(goals);
        this.selectedGoalId = selectedGoalId;
        this.status = status;
        this.next = next;
        this.start = start;
        this.reason = reason;
        this.why = List.copyOf(why);
        this.handoff = handoff;
        this.alternatives = List.copyOf(alternatives);
    }

    public static PlannerViewModel from(AccountSummary account, PlanningService.Result planning)
    {
        List<GoalOption> goals = planning.getGoals().stream()
            .map(value -> new GoalOption(value.getId(), value.getDisplayName()))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
        String goalId = planning.getSelectedGoal() == null ? null : planning.getSelectedGoal().getId();
        if (planning.getPrimary() != null)
        {
            return primary(account, planning, goals, goalId);
        }
        String status;
        String next;
        switch (planning.getStatus())
        {
            case SELECT_GOAL:
                status = "SELECT GOAL";
                next = "Choose a goal to begin.";
                break;
            case WAITING_FOR_ACCOUNT:
                status = "NEEDS INFO";
                next = "Waiting for account state.";
                break;
            case UNSUPPORTED_ACCOUNT:
                status = "NEEDS INFO";
                next = "Ultimate Ironman account required.";
                break;
            case GOAL_COMPLETE:
                status = "COMPLETE";
                next = planning.getSelectedGoal().getDisplayName() + " complete";
                break;
            default:
                status = "NEEDS INFO";
                next = "Atlas needs more information before recommending a next action.";
        }
        return new PlannerViewModel(account.getAccount(), account.getStatus(), goals, goalId,
            status, next, null, reason(planning.getReason()), List.of(), null, options(planning));
    }

    private static PlannerViewModel primary(AccountSummary account, PlanningService.Result planning,
        List<GoalOption> goals, String goalId)
    {
        StrategicAction action = planning.getPrimary().getAction();
        List<String> why = new ArrayList<>();
        String next;
        String start = null;
        String reason;
        String status;
        String handoff = null;
        if (action instanceof StrategicAction.Method)
        {
            StrategicAction.Method methodAction = (StrategicAction.Method) action;
            MethodDefinition method = methodAction.getResult().getMethod();
            next = method.getDisplayName();
            start = method.getStart().getLocation();
            status = "SETUP READY";
            GoalState.Check target = target(planning.getPrimary(), methodAction,
                planning.getSelectedGoal().getId());
            String targetText = target == null ? null : number(target.getRequirement().getTarget())
                + " " + title(method.getActivity());
            reason = target == null ? "This method advances the selected goal and its setup is verified."
                : planning.getSelectedGoal().getDisplayName() + " still needs "
                    + targetText + ". Your setup is ready.";
            if (targetText != null)
            {
                why.add("Goal: " + planning.getSelectedGoal().getDisplayName() + " still needs " + targetText + ".");
            }
            List<String> setup = Stream.concat(
                methodAction.getResult().getEvaluation().getPreparationAnyOf().stream()
                    .filter(value -> value.getResult() == MethodEvaluator.GroupResult.SATISFIED)
                    .flatMap(value -> value.getAlternatives().stream()
                        .filter(alternative -> alternative.getResult() == MethodEvaluator.GroupResult.SATISFIED)
                        .limit(1).flatMap(alternative -> alternative.getAlternative().getRequirements().stream())
                        .filter(requirement -> !requirement.getFact().startsWith("skill.")).limit(1))
                    .map(value -> userClause(value.getDescription())),
                Stream.concat(method.getPreparation().stream(),
                    Stream.concat(method.getSetupItems().stream(), method.getConsumes().stream()))
                    .map(value -> userClause(value.getDescription())))
                .limit(2).collect(java.util.stream.Collectors.toList());
            why.add("Setup: " + (setup.isEmpty() ? "Encoded setup requirements are verified."
                : String.join(" ", setup)));
            why.add("Start: " + start + ".");
            methodAction.getResult().getEfficiency().getTrustedXpRate().ifPresent(rate -> why.add(
                "Trusted range: " + number(rate.getMinimum()) + "–" + number(rate.getMaximum()) + " XP/hour."));
            if (targetText != null)
            {
                why.add("Recheck: at " + targetText + ".");
            }
            why.add("Location: Travel and current proximity have not been verified.");
        }
        else
        {
            QuestAction quest = (QuestAction) action;
            next = quest.getHandoff().getDisplayName();
            status = "HANDOFF";
            reason = "This milestone is on the active " + planning.getSelectedGoal().getDisplayName()
                + " frontier, with its encoded strategic prerequisites satisfied.";
            why.add("Goal: This is the next available milestone in "
                + planning.getSelectedGoal().getDisplayName() + ".");
            why.add("Handoff: Encoded dependencies and strategic prerequisites are satisfied.");
            why.add("Preflight: Quest items and combat readiness have not been verified.");
            handoff = "Use Quest Helper to continue this quest.";
        }
        return new PlannerViewModel(account.getAccount(), account.getStatus(), goals, goalId,
            status, next, start, reason, why, handoff, options(planning));
    }

    private static List<Option> options(PlanningService.Result planning)
    {
        return planning.getAlternatives().stream().map(value ->
        {
            StrategicDecision.Candidate candidate = value.getCandidate();
            StrategicAction action = candidate.getAction();
            String title = action instanceof StrategicAction.Method
                ? ((StrategicAction.Method) action).getResult().getMethod().getDisplayName()
                : ((QuestAction) action).getHandoff().getDisplayName();
            return new Option(title, optionStatus(action), reason(value.getReason()));
        }).collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    private static String optionStatus(StrategicAction action)
    {
        if (action.getReadiness() == StrategicAction.Readiness.READY)
        {
            return "SETUP READY";
        }
        if (action.getReadiness() == StrategicAction.Readiness.READY_TO_HANDOFF)
        {
            return "HANDOFF";
        }
        if (action.getReadiness() == StrategicAction.Readiness.BLOCKED)
        {
            return "BLOCKED";
        }
        if (action instanceof StrategicAction.Method)
        {
            StrategicAction.Method method = (StrategicAction.Method) action;
            if (method.getResult().getEvaluation().getStatus() == MethodEvaluator.Status.NEEDS_PREP
                || !method.getResult().getPreparation().getDeficits().isEmpty())
            {
                return "NEEDS PREP";
            }
        }
        return "UNKNOWN";
    }

    private static GoalState.Check target(StrategicDecision.Candidate candidate,
        StrategicAction.Method method, String goalId)
    {
        Stream<GoalState.Check> preferred = candidate.getFrontierRequirements().isEmpty()
            ? method.getRelevance().getMatchedRequirements().stream()
                .filter(value -> value.getScopeId().equals(goalId))
            : candidate.getFrontierRequirements().stream();
        GoalState.Check result = preferred.max(java.util.Comparator.comparingDouble(
            value -> value.getRequirement().getTarget())).orElse(null);
        return result == null ? method.getRelevance().getMatchedRequirements().stream()
            .max(java.util.Comparator.comparingDouble(value -> value.getRequirement().getTarget())).orElse(null)
            : result;
    }

    private static String reason(PlanningService.Reason value)
    {
        switch (value.getKind())
        {
            case REOBSERVATION_REQUIRED:
                return value.getDescription() + " Needs a fresh observation.";
            case UNKNOWN_REQUIREMENT:
                return "Atlas cannot verify: " + value.getDescription();
            case MISSING_PREPARATION:
                return "Preparation unresolved: " + value.getDescription();
            case BLOCKED:
                return "Blocked: " + value.getDescription();
            default:
                return value.getDescription();
        }
    }

    private static String title(String value)
    {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String userClause(String value)
    {
        int semicolon = value.indexOf(';');
        int sentence = value.indexOf(". ");
        int end = semicolon < 0 ? sentence : sentence < 0 ? semicolon : Math.min(semicolon, sentence);
        String result = (end < 0 ? value : value.substring(0, end)).trim();
        return result.endsWith(".") ? result : result + ".";
    }

    private static String number(double value)
    {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
