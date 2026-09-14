package com.uimatlas.recommendation;

import lombok.Value;

/** Final safety gate between evaluation/preparation and a live-actionable domain result. */
public final class MethodActionability
{
    public enum Status { ACTIONABLE, ACTIONABLE_WITH_PREP, NOT_ACTIONABLE }
    public enum Reason { READY, SUPPORTED_PREPARATION, UNRESOLVED_PREPARATION, BLOCKED, UNKNOWN }

    @Value
    public static class Result
    {
        Status status;
        Reason reason;

        public boolean isActionable()
        {
            return status != Status.NOT_ACTIONABLE;
        }
    }

    public Result decide(MethodEvaluator.Evaluation evaluation, PreparationFeasibility.Result preparation)
    {
        if (evaluation.getStatus() == MethodEvaluator.Status.BLOCKED)
        {
            return new Result(Status.NOT_ACTIONABLE, Reason.BLOCKED);
        }
        if (evaluation.getStatus() == MethodEvaluator.Status.UNKNOWN)
        {
            return new Result(Status.NOT_ACTIONABLE, Reason.UNKNOWN);
        }
        if (evaluation.getStatus() == MethodEvaluator.Status.AVAILABLE
            && preparation.getStatus() == PreparationFeasibility.Status.READY)
        {
            return new Result(Status.ACTIONABLE, Reason.READY);
        }
        if (evaluation.getStatus() == MethodEvaluator.Status.NEEDS_PREP
            && preparation.getStatus() == PreparationFeasibility.Status.FEASIBLE_PREP)
        {
            return new Result(Status.ACTIONABLE_WITH_PREP, Reason.SUPPORTED_PREPARATION);
        }
        return new Result(Status.NOT_ACTIONABLE, Reason.UNRESOLVED_PREPARATION);
    }
}
