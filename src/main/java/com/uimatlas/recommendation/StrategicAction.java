package com.uimatlas.recommendation;

import lombok.Value;

/** Common selection surface; detailed method/quest results remain composed, not flattened. */
public interface StrategicAction
{
    enum Kind { METHOD, QUEST_MILESTONE }
    enum Readiness { READY, READY_TO_HANDOFF, READY_WITH_PREP, BLOCKED, UNRESOLVED }

    String getId();
    Kind getKind();
    Readiness getReadiness();

    default boolean isActionable()
    {
        return getReadiness() == Readiness.READY || getReadiness() == Readiness.READY_TO_HANDOFF
            || getReadiness() == Readiness.READY_WITH_PREP;
    }

    @Value
    class Method implements StrategicAction
    {
        RecommendationDecision.CandidateResult result;
        GoalContext.MethodRelevance relevance;

        @Override
        public String getId()
        {
            return result.getMethod().getId();
        }

        @Override
        public Kind getKind()
        {
            return Kind.METHOD;
        }

        @Override
        public Readiness getReadiness()
        {
            if (result.getActionability().isActionable())
            {
                return result.getActionability().getStatus() == MethodActionability.Status.ACTIONABLE
                    ? Readiness.READY : Readiness.READY_WITH_PREP;
            }
            return result.getEvaluation().getStatus() == MethodEvaluator.Status.BLOCKED
                ? Readiness.BLOCKED : Readiness.UNRESOLVED;
        }
    }
}
