package com.uimatlas.recommendation;

import com.uimatlas.state.Observation;

/** Numeric facts from one immutable snapshot, addressed without knowledge of their storage. */
@FunctionalInterface
public interface FactLookup
{
    /** Unsupported or unavailable facts return an explicit unknown observation. */
    Observation<Double> get(String factId);
}
