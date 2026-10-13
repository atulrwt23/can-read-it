package com.canreadit.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserIdentities extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    List<UserIdentity> findByUserId(UUID userId);
}
