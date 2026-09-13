package com.uimatlas.ui;

import com.uimatlas.state.AccountState;
import lombok.Value;

/** UI-ready strings; Swing does not query the client or decide state readiness. */
@Value
public class AccountSummary
{
    String account;
    String status;
    String totalLevel;
    String inventory;

    public static AccountSummary from(AccountState state)
    {
        return new AccountSummary(
            state.getAccountMode().isKnown() ? state.getAccountMode().getValue().getDisplayName() : "Unknown",
            state.isLoaded() ? "Loaded" : state.isLoggedIn() ? "Partial" : "Not logged in",
            state.totalLevel().isPresent() ? Integer.toString(state.totalLevel().getAsInt()) : "Unknown",
            state.getInventory().isKnown() ? state.getInventory().getValue().occupiedSlots() + " / 28" : "Unknown");
    }
}
