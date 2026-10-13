package com.canreadit.catalog.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ChapterEntities extends JpaRepository<ChapterEntity, UUID> {

    /** Loads the chapter and locks its row ({@code for update}) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChapterEntity c where c.id = :id")
    Optional<ChapterEntity> lockById(UUID id);
}
