package com.nextportal.api.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
        if redis.call('HGET', KEYS[1], 'refresh') ~= ARGV[1] then return 0 end
        redis.call('HSET', KEYS[1], 'refresh', ARGV[2], 'access', ARGV[3])
        redis.call('EXPIRE', KEYS[1], ARGV[4])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
        redis.call('HSET', KEYS[1], 'member', ARGV[1], 'version', ARGV[2], 'refresh', ARGV[3], 'access', ARGV[4])
        redis.call('EXPIRE', KEYS[1], ARGV[5])
        return 1
        """, Long.class);

    private String key(String sid) { return "next-portal:session:" + sid; }

    public void create(String sid, long member, long version, String refresh, String access, Duration ttl) {
        redis.execute(CREATE, List.of(key(sid)), "" + member, "" + version, refresh, access, "" + ttl.toSeconds());
    }
    public Optional<Session> find(String sid) {
        var data = redis.<String, String>opsForHash().entries(key(sid));
        return Optional.of(data).filter(value -> value.keySet().containsAll(List.of("member", "version", "refresh", "access")))
            .map(value -> new Session(Long.parseLong(value.get("member")), Long.parseLong(value.get("version")),
                value.get("refresh"), value.get("access")));
    }
    public boolean rotate(String sid, String previous, String refresh, String access, Duration ttl) {
        return Long.valueOf(1).equals(redis.execute(ROTATE, List.of(key(sid)), previous, refresh, access, "" + ttl.toSeconds()));
    }
    public void delete(String sid) { redis.delete(key(sid)); }
    public record Session(long memberId, long version, String refreshHash, String accessId) { }
}
