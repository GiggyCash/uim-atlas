package com.uimatlas.state;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.Value;

/** A fact and its last observation time. Values supplied here must be immutable. */
@Value
public class Observation<T>
{
    public enum Confidence { VERIFIED_NOW, LAST_OBSERVED, UNKNOWN }

    T value;
    String source;
    Instant observedAt;
    Confidence confidence;

    private Observation(T value, String source, Instant observedAt, Confidence confidence)
    {
        this.value = value;
        this.source = source;
        this.observedAt = observedAt;
        this.confidence = confidence;
    }

    public static <T> Observation<T> unknown()
    {
        return new Observation<>(null, null, null, Confidence.UNKNOWN);
    }

    public static <T> Observation<T> verified(T value, String source, Instant time)
    {
        return new Observation<>(Objects.requireNonNull(value), Objects.requireNonNull(source),
            Objects.requireNonNull(time), Confidence.VERIFIED_NOW);
    }

    public static <K, V> Observation<Map<K, V>> map(Map<K, V> value, String source, Instant time)
    {
        return verified(Map.copyOf(value), source, time);
    }

    public boolean isKnown()
    {
        return confidence != Confidence.UNKNOWN;
    }

    public Observation<T> lastObserved()
    {
        return isKnown() ? new Observation<>(value, source, observedAt, Confidence.LAST_OBSERVED) : this;
    }
}
