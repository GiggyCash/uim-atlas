package com.uimatlas.state;

import java.util.Objects;
import javax.inject.Singleton;

/** Single client-thread writer, immutable snapshots safe to read from Swing. */
@Singleton
public class AccountStateService
{
    private volatile AccountState snapshot = AccountState.empty();

    public AccountState getSnapshot()
    {
        return snapshot;
    }

    public void publish(AccountState state)
    {
        snapshot = Objects.requireNonNull(state);
    }

    public void reset()
    {
        snapshot = AccountState.empty();
    }
}
