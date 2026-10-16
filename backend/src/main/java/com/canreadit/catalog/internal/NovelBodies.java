package com.canreadit.catalog.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NovelBodies extends JpaRepository<NovelBodyEntity, UUID> {}
