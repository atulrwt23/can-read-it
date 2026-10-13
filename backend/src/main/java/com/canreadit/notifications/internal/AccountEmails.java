package com.canreadit.notifications.internal;

import com.canreadit.identity.AccountEmailRequested;
import com.canreadit.notifications.EmailMessage;
import com.canreadit.notifications.EmailSender;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends the account emails identity asks for: one-time codes and notices. It runs on its own thread, after the
 * request, so sending never slows a response or reveals which branch it took. A failed send is logged and dropped:
 * the code expires in minutes anyway, and the reader can ask for a new one.
 */
@Component
class AccountEmails {

    private static final Logger log = LoggerFactory.getLogger(AccountEmails.class);

    private final EmailSender sender;
    private final Brand brand;

    AccountEmails(EmailSender sender, Brand brand) {
        this.sender = sender;
        this.brand = brand;
    }

    @Async
    @EventListener
    void on(AccountEmailRequested request) {
        try {
            sender.send(render(request, brand.name()));
        } catch (RuntimeException e) {
            log.warn("Could not send a {} email: {}", request.kind(), e.toString());
        }
    }

    // TODO(product): add links back to the site once it has a stable address (SITE_URL), and an HTML version.
    static EmailMessage render(AccountEmailRequested request, String brand) {
        long minutes = request.codeValidFor().toMinutes();
        return switch (request.kind()) {
            case SIGN_IN_CODE ->
                codeEmail(
                        request,
                        "Your " + brand + " sign-in code",
                        "Enter this code to sign in to " + brand + ":",
                        minutes,
                        "If you didn't ask for it, you can ignore this email. Nobody can sign in without the code.");
            case SIGN_UP_CODE ->
                codeEmail(
                        request,
                        "Your " + brand + " sign-up code",
                        "Enter this code to create your " + brand + " account:",
                        minutes,
                        "If you didn't ask for it, you can ignore this email. No account is created without the code.");
            case CONFIRM_EMAIL_CODE ->
                codeEmail(
                        request,
                        "Confirm your email for " + brand,
                        "Enter this code to confirm your email and sign in to " + brand + ":",
                        minutes,
                        "If you didn't create a " + brand + " account, you can ignore this email.");
            case PASSWORD_RESET_CODE ->
                codeEmail(
                        request,
                        "Reset your " + brand + " password",
                        "Enter this code to choose a new " + brand + " password:",
                        minutes,
                        "If you didn't ask for it, you can ignore this email. Your password stays the same.");
            case ACCOUNT_EXISTS ->
                new EmailMessage(request.to(), "You already have a " + brand + " account", """
                    Someone, probably you, tried to create a %1$s account with this email address. \
                    It already has one.

                    Sign in with your password, or ask for a sign-in code to be sent to this address. \
                    If you forgot your password, you can reset it from the sign-in page.

                    If it wasn't you, you can ignore this email. Nothing has changed.
                    """.formatted(brand));
            case DELETION_SCHEDULED ->
                new EmailMessage(request.to(), "Your " + brand + " account will be deleted", """
                    You asked to delete your %1$s account. It will be erased in %2$d days, \
                    with everything in it.

                    Changed your mind? Sign in before then and the deletion is cancelled.

                    If you didn't ask for this, sign in now and change your password.
                    """.formatted(
                                brand, request.codeValidFor().toDays()));
        };
    }

    private static EmailMessage codeEmail(
            AccountEmailRequested request, String subject, String intro, long minutes, String footer) {
        String code = Objects.requireNonNull(request.code(), "a code email needs a code");
        return new EmailMessage(request.to(), subject, """
                %s

                %s

                The code works for %d minutes.

                %s
                """.formatted(intro, code, minutes, footer));
    }
}
