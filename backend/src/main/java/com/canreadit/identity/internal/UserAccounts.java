package com.canreadit.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Email and handle are citext columns. Comparing them with a plain string parameter would compare as text (case
 * sensitive), so these queries cast the parameter to citext.
 */
interface UserAccounts extends JpaRepository<UserAccount, UUID> {

    @Query(value = "select * from identity.users where email = cast(:email as citext)", nativeQuery = true)
    Optional<UserAccount> findByEmail(@Param("email") String email);

    @Query(
            value = "select exists (select 1 from identity.users where email = cast(:email as citext))",
            nativeQuery = true)
    boolean emailTaken(@Param("email") String email);

    @Query(
            value = "select exists (select 1 from identity.users where handle = cast(:handle as citext))",
            nativeQuery = true)
    boolean handleTaken(@Param("handle") String handle);

    @Query(
            value =
                    "select id from identity.users where status = 'PENDING_DELETION' and deletion_requested_at <= :cutoff",
            nativeQuery = true)
    List<UUID> findDueForErasure(@Param("cutoff") Instant cutoff);
}
