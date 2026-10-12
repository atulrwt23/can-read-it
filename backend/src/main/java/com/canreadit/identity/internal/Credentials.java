package com.canreadit.identity.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface Credentials extends JpaRepository<Credential, UUID> {}
