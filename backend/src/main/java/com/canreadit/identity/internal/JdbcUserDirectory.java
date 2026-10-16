package com.canreadit.identity.internal;

import com.canreadit.identity.UserDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcUserDirectory implements UserDirectory {

    private final JdbcClient jdbc;

    JdbcUserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Contact> contacts(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("""
                        select id, email, display_name from identity.users
                        where id in (:ids) and status = 'ACTIVE'
                          and email is not null and email_verified_at is not null
                        """)
                .param("ids", List.copyOf(userIds))
                .query((rs, n) -> new Contact(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")))
                .list()
                .stream()
                .collect(Collectors.toMap(Contact::userId, Function.identity()));
    }
}
