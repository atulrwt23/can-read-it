package com.canreadit.identity.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthSessions extends JpaRepository<AuthSession, UUID> {

    /** Locks the session, so a refresh and a revocation of the same session never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findForUpdate(@Param("id") UUID id);
}
