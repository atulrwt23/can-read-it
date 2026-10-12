package com.canreadit.identity.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Request bodies of the sign-in endpoints. */
final class AuthRequests {

    /** Handles: 3–30 letters, digits or underscores (matches the database check). */
    static final String HANDLE = "^[A-Za-z0-9_]{3,30}$";

    private AuthRequests() {}

    /**
     * @param password 10–128 characters, no composition rules (CLAUDE.md section 7)
     * @param botToken the Turnstile widget's token, when the bot check is on
     */
    record Register(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 10, max = 128) String password,
            @NotNull @Pattern(regexp = HANDLE) String handle,
            @Size(max = 2048) @Nullable String botToken) {}

    /** A one-time code from an email: 6 digits. */
    static final String CODE = "^[0-9]{6}$";

    record Login(
            @NotBlank @Size(max = 254) String email,
            @NotNull @Size(max = 128) String password) {}

    record EmailStart(
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 2048) @Nullable String botToken) {}

    /** @param handle used only when the code creates an account; otherwise one is generated */
    record EmailVerify(
            @NotBlank @Size(max = 254) String email,
            @NotNull @Pattern(regexp = CODE) String code,
            @Pattern(regexp = HANDLE) @Nullable String handle) {}

    record ForgotPassword(
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 2048) @Nullable String botToken) {}

    record ResetPassword(
            @NotBlank @Size(max = 254) String email,
            @NotNull @Pattern(regexp = CODE) String code,
            @NotNull @Size(min = 10, max = 128) String newPassword) {}
}
