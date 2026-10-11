package com.canreadit.shared.internal;

import com.canreadit.shared.RateLimit;
import com.canreadit.shared.RateLimiter;
import com.canreadit.shared.TooManyRequestsException;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Token buckets in Redis (Bucket4j, compare-and-swap). Keys are {@code ratelimit:<name>:<hash of key>}, so no IP
 * or email is stored in clear, and each bucket expires once it would be full again. The Redis connection opens on
 * first use and is retried after a failure, so the app starts and serves without Redis.
 */
@Component
class RedisRateLimiter implements RateLimiter, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(1);
    private static final long WARN_EVERY_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final long RECONNECT_AFTER_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final LettuceConnectionFactory connections;
    private final Settings settings;
    private final AtomicLong lastWarning = new AtomicLong(System.nanoTime() - WARN_EVERY_NANOS);
    private @Nullable StatefulRedisConnection<String, byte[]> connection;
    private @Nullable ProxyManager<String> buckets;
    private long nextConnectAttempt = System.nanoTime();

    RedisRateLimiter(LettuceConnectionFactory connections, Settings settings) {
        this.connections = connections;
        this.settings = settings;
    }

    @Override
    public void check(RateLimit limit, String key) {
        if (!settings.enabled()) {
            return;
        }
        ConsumptionProbe probe;
        try {
            probe = buckets()
                    .builder()
                    .build(bucketKey(limit, key), () -> configuration(limit))
                    .tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            warnUnavailable(e);
            return;
        }
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        }
    }

    static String bucketKey(RateLimit limit, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "ratelimit:" + limit.name() + ":" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static BucketConfiguration configuration(RateLimit limit) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth ->
                        bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .build();
    }

    private synchronized ProxyManager<String> buckets() {
        if (buckets != null && connection != null && connection.isOpen()) {
            return buckets;
        }
        closeConnection();
        // While Redis is down, don't make every request wait for a connection attempt.
        if (System.nanoTime() - nextConnectAttempt < 0) {
            throw new IllegalStateException("Redis was unreachable moments ago");
        }
        nextConnectAttempt = System.nanoTime() + RECONNECT_AFTER_NANOS;
        if (!(connections.getNativeClient() instanceof RedisClient client)) {
            throw new IllegalStateException("Rate limits need a standalone Redis client");
        }
        StatefulRedisConnection<String, byte[]> opened =
                client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        opened.setTimeout(REDIS_TIMEOUT);
        connection = opened;
        buckets = Bucket4jLettuce.casBasedBuilder(opened)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .requestTimeout(REDIS_TIMEOUT)
                .build();
        return buckets;
    }

    /** At most one warning a minute: when Redis is down, every request would log one. */
    private void warnUnavailable(RuntimeException e) {
        long now = System.nanoTime();
        long last = lastWarning.get();
        if (now - last >= WARN_EVERY_NANOS && lastWarning.compareAndSet(last, now)) {
            log.warn("Rate limits are not enforced, Redis unavailable: {}", e.toString());
        }
    }

    private synchronized void closeConnection() {
        if (connection != null) {
            connection.close();
        }
        connection = null;
        buckets = null;
    }

    @Override
    public void destroy() {
        closeConnection();
    }

    /** @param enabled off only where no client should ever be limited (load tests) */
    @ConfigurationProperties("app.rate-limits")
    record Settings(boolean enabled) {}
}
