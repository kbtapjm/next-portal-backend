package com.nextportal.api.security;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ENABLE_REDIS_TESTS", matches = "true")
class RedisSessionIntegrationTest {
    @Test void atomicRotationExpiryAndRevocationAgainstLocalRedis() throws Exception {
        var connection = new LettuceConnectionFactory("localhost", 6379);
        connection.afterPropertiesSet(); connection.start();
        var template = new StringRedisTemplate(connection);
        var store = new SessionStore(template);
        var sid = "test-" + UUID.randomUUID();
        try {
            store.create(sid, 1, 0, "old-refresh-hash", "old-jti", Duration.ofMinutes(1));
            assertThat(template.getExpire("next-portal:session:" + sid)).isBetween(1L, 60L);
            assertThat(store.find(sid)).isPresent();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = IntStream.range(0, 8).mapToObj(index -> executor.submit(() ->
                    store.rotate(sid, "old-refresh-hash", "next-refresh-hash", "next-jti", Duration.ofMinutes(1)))).toList();
                long successes = 0;
                for (var future : futures) { successes += future.get() ? 1 : 0; }
                assertThat(successes).isEqualTo(1);
            }
            assertThat(store.find(sid).orElseThrow().accessId()).isEqualTo("next-jti");
            store.delete(sid);
            assertThat(store.find(sid)).isEmpty();
        } finally { store.delete(sid); connection.destroy(); }
    }
}
