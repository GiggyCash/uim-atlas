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

/** Immutable, UI-ready semantics. Swing renders this value and emits intent only. */
@Value
public class PlannerViewModel
{
    public enum SurfaceState { READY, SELECT_GOAL, WAITING, UNSUPPORTED, COMPLETE, NEEDS_INFO, NO_ACTION }

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
    public static class Detail
    {
        String label;
        String text;
    }

    @Value
    public static class Option
    {
        String title;
        String method;
        String status;
        String reason;
    }

    String account;
    String accountState;
    List<GoalOption> goals;
    String selectedGoalId;
    String goalName;
    String goalProgress;
    SurfaceState surfaceState;
    String status;
    String next;
    String method;
    String start;
    String reason;
    List<Detail> why;
    MethodDefinition.RouteTarget routeTarget;
    List<Option> alternatives;

    public PlannerViewModel(String account, String accountState, List<GoalOption> goals,
        String selectedGoalId, String goalName, String goalProgress, SurfaceState surfaceState,
        String status, String next, String method, String start, String reason, List<Detail> why,
        MethodDefinition.RouteTarget routeTarget, List<Option> alternatives)
    {
        this.account = account;
        this.accountState = accountState;
        this.goals = List.copyOf(goals);
        this.selectedGoalId = selectedGoalId;
        this.goalName = goalName;
        this.goalProgress = goalProgress;
        this.surfaceState = surfaceState;
        this.status = status;
        this.next = next;
        this.method = method;
        this.start = start;
        this.reason = reason;
        this.why = List.copyOf(why);
        this.routeTarget = routeTarget;
        this.alternatives = List.copyOf(alternatives);
    }

    public static PlannerViewModel from(AccountSummary account, PlanningService.Result planning)
    {
        List<GoalOption> goals = planning.getGoals().stream()
            .map(value -> new GoalOption(value.getId(), value.getDisplayName()))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
        String goalId = planning.getSelectedGoal() == null ? null : planning.getSelectedGoal().getId();
        String goalName = planning.getSelectedGoal() == null ? null : planning.getSelectedGoal().getDisplayName();
        String progress = progress(planning);
        if (planning.getPrimary() != null)
        {
            return primary(account, planning, goals, goalId, goalName, progress);
        }

        SurfaceState surface;
        String status;
        String next;
        switch (planning.getStatus())
        {
            case SELECT_GOAL:
                surface = SurfaceState.SELECT_GOAL;
                status = "SELECT GOAL";
                next = "Choose a goal";
                break;
            case WAITING_FOR_ACCOUNT:
                surface = SurfaceState.WAITING;
                status = "WAITING";
                next = "Waiting for account";
                break;
            case UNSUPPORTED_ACCOUNT:
                surface = SurfaceState.UNSUPPORTED;
                status = "UNSUPPORTED";
                next = "Ultimate Ironman required";
                break;
            case GOAL_COMPLETE:
                surface = SurfaceState.COMPLETE;
                status = "GOAL COMPLETE";
                next = goalName + " complete";
                break;
            default:
                boolean noAction = planning.getReason().getKind() == PlanningService.ReasonKind.NO_COVERED_ACTION;
                surface = noAction ? SurfaceState.NO_ACTION : SurfaceState.NEEDS_INFO;
                status = noAction ? "NO ACTION" : "NEEDS INFO";
                next = noAction ? "No actionable recommendation" : "Atlas needs information";
        }
        return new PlannerViewModel(account.getAccount(), account.getStatus(), goals, goalId, goalName,
            progress, surface, status, next, null, null, reason(planning.getReason()), List.of(), null,
            options(planning));
    }

    private static PlannerViewModel primary(AccountSummary account, PlanningService.Result planning,
        List<GoalOption> goals, String goalId, String goalName, String progress)
    {
        StrategicAction action = planning.getPrimary().getAction();
        List<Detail> why = new ArrayList<>();
        String next;
        String methodName = null;
        String start = null;
        String reason;
        String status;
        MethodDefinition.RouteTarget routeTarget = null;
        if (action instanceof StrategicAction.Method)
        {
            StrategicAction.Method methodAction = (StrategicAction.Method) action;
            MethodDefinition method = methodAction.getResult().getMethod();
            GoalState.Check target = target(planning.getPrimary(), methodAction, goalId);
            String skill = title(method.getActivity());
            String targetLevel = target == null ? null : number(target.getRequirement().getTarget());
            next = targetLevel == null ? method.getDisplayName() : "Train " + skill + " to " + targetLevel;
            methodName = method.getDisplayName();
            start = method.getStart().getLocation();
            status = "SETUP READY";
            reason = targetLevel == null ? "This method directly advances " + goalName + "."
                : goalName + " still needs " + targetLevel + " " + skill + ".";
            why.add(new Detail("Goal", targetLevel == null ? "Direct progress for " + goalName + "."
                : goalName + " needs " + targetLevel + " " + skill + "."));
            why.add(new Detail("Setup", setup(methodAction, method)));
            why.add(new Detail("Method", method.getDisplayName() + "."));
            why.add(new Detail("Destination", start + "."));
            why.add(new Detail("Boundary", "Travel and current proximity have not been verified."));
            routeTarget = method.getStart().getRouteTarget().orElse(null);
        }
        else
        {
            QuestAction quest = (QuestAction) action;
            next = quest.getHandoff().getDisplayName();
            status = "QUEST";
            reason = "Directly available " + goalName + " milestone.";
            why.add(new Detail("Goal", "Directly available milestone."));
            why.add(new Detail("Readiness", "Encoded strategic prerequisites are satisfied."));
            why.add(new Detail("Boundary", "Quest items and combat readiness are not fully verified."));
        }
        return new PlannerViewModel(account.getAccount(), account.getStatus(), goals, goalId, goalName,
            progress, SurfaceState.READY, status, next, methodName, start, reason, why, routeTarget,
            options(planning));
    }

    private static String setup(StrategicAction.Method action, MethodDefinition method)
    {
        List<String> setup = Stream.concat(
            action.getResult().getEvaluation().getPreparationAnyOf().stream()
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
        return setup.isEmpty() ? "Encoded carried setup is verified." : String.join(" ", setup);
    }

    private static List<Option> options(PlanningService.Result planning)
    {
        return planning.getAlternatives().stream().map(value ->
        {
            StrategicDecision.Candidate candidate = value.getCandidate();
            StrategicAction action = candidate.getAction();
            if (action instanceof StrategicAction.Method)
            {
                StrategicAction.Method method = (StrategicAction.Method) action;
                GoalState.Check target = target(candidate, method, planning.getSelectedGoal().getId());
                String title = target == null ? method.getResult().getMethod().getDisplayName()
                    : title(method.getResult().getMethod().getActivity()) + " to "
                        + number(target.getRequirement().getTarget());
                return new Option(title, method.getResult().getMethod().getDisplayName(),
                    optionStatus(action), reason(value.getReason()));
            }
            return new Option(((QuestAction) action).getHandoff().getDisplayName(), null,
                optionStatus(action), reason(value.getReason()));
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
            return "QUEST";
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
        return "NEEDS INFO";
    }

    private static GoalState.Check target(StrategicDecision.Candidate candidate,
        StrategicAction.Method method, String goalId)
    {
        Stream<GoalState.Check> preferred = candidate.getFrontierRequirements().isEmpty()
            ? method.getRelevance().getMatchedRequirements().stream().filter(value -> value.getScopeId().equals(goalId))
            : candidate.getFrontierRequirements().stream();
        GoalState.Check result = preferred.filter(value -> value.getRequirement().getFact().startsWith("skill."))
            .max(java.util.Comparator.comparingDouble(value -> value.getRequirement().getTarget())).orElse(null);
        return result == null ? method.getRelevance().getMatchedRequirements().stream()
            .filter(value -> value.getRequirement().getFact().startsWith("skill."))
            .max(java.util.Comparator.comparingDouble(value -> value.getRequirement().getTarget())).orElse(null)
            : result;
    }

    private static String progress(PlanningService.Result planning)
    {
        if (planning.getStrategic() == null)
        {
            return null;
        }
        GoalState state = planning.getStrategic().getContext().getGoalState();
        return state.getCompletedMilestones() + " / " + state.getTotalMilestones() + " milestones";
    }

    private static String reason(PlanningService.Reason value)
    {
        switch (value.getKind())
        {
            case REOBSERVATION_REQUIRED:
                return value.getDescription() + " A fresh observation is needed.";
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
