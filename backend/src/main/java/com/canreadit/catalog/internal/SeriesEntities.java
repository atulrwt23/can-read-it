package com.canreadit.catalog.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeriesEntities extends JpaRepository<SeriesEntity, UUID> {}
