package com.canreadit.identity.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Request bodies of the {@code /me} endpoints. */
final class AccountRequests {

    /** Display names: 1–60 characters (the database check), no control characters. */
    static final String DISPLAY_NAME = "^[^\\p{Cntrl}]*[^\\p{Cntrl}\\s][^\\p{Cntrl}]*$";

    private AccountRequests() {}

    record UpdateProfile(
            @Pattern(regexp = AuthRequests.HANDLE) @Nullable String handle,

            @Size(min = 1, max = 60) @Pattern(regexp = DISPLAY_NAME) @Nullable
            String displayName) {}

    /** @param currentPassword required when the account already has a password */
    record ChangePassword(
            @Size(max = 128) @Nullable String currentPassword,
            @NotNull @Size(min = 10, max = 128) String newPassword) {}

    record DeleteAccount(@Size(max = 128) @Nullable String currentPassword) {}
}
