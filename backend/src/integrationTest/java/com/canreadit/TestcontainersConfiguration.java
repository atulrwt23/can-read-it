package com.canreadit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Real infrastructure for integration tests, matching infra/local/docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String BUCKET = "canreadit-media";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine");
    }

    @Bean
    GenericContainer<?> seaweedfs() {
        return new GenericContainer<>("chrislusf/seaweedfs:4.47")
                .withCommand("server", "-dir=/data", "-s3", "-s3.config=/etc/seaweedfs/s3.json")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("seaweedfs-s3.json"), "/etc/seaweedfs/s3.json")
                .withExposedPorts(8333)
                .waitingFor(Wait.forHttp("/status").forPort(8333));
    }

    @Bean
    DynamicPropertyRegistrar mediaProperties(GenericContainer<?> seaweedfs) {
        return registry -> {
            String endpoint = "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333);
            registry.add("app.media.s3.endpoint", () -> endpoint);
            registry.add("app.media.s3.bucket", () -> BUCKET);
            registry.add("app.media.s3.access-key", () -> "canreadit");
            registry.add("app.media.s3.secret-key", () -> "canreadit-secret");
            registry.add("app.media.public-base-url", () -> endpoint + "/" + BUCKET);
        };
    }
}
