package com.canreadit.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Who other modules may contact, without reading identity's tables. */
public interface UserDirectory {

    /**
     * Contact details for the given users who can be emailed: active accounts with a confirmed email. Others
     * (unconfirmed, suspended, banned, deleting or deleted) are absent, so an address nobody proved to own is never
     * mailed.
     */
    Map<UUID, Contact> contacts(Collection<UUID> userIds);

    record Contact(UUID userId, String email, String displayName) {}
}
