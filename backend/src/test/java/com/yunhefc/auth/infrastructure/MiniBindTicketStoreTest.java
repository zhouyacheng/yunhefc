package com.yunhefc.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhefc.common.security.Hashing;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class MiniBindTicketStoreTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final MiniBindTicketStore store;

    MiniBindTicketStoreTest() {
        when(redis.opsForValue()).thenReturn(ops);
        store = new MiniBindTicketStore(redis);
    }

    @Test
    void saveStoresCiphertextAndConsumeRoundTrips() {
        SavedTicket saved = save("openid-1");

        assertThat(saved.storedValue()).doesNotContain("openid-1");
        when(ops.getAndDelete(saved.key())).thenReturn(saved.storedValue());

        assertThat(store.consume(saved.ticket())).isEqualTo("openid-1");
    }

    @Test
    void consumeReturnsNullForExpiredOrAlreadyConsumedTicket() {
        when(ops.getAndDelete("yunhefc:auth:mini-bind:" + Hashing.sha256("gone"))).thenReturn(null);

        assertThat(store.consume("gone")).isNull();
    }

    @Test
    void tamperedValueIsRejected() {
        SavedTicket saved = save("openid-1");
        char[] chars = saved.storedValue().toCharArray();
        chars[0] = chars[0] == 'A' ? 'B' : 'A';
        when(ops.getAndDelete(saved.key())).thenReturn(new String(chars));

        assertThat(store.consume(saved.ticket())).isNull();
    }

    @Test
    void onlyOneConcurrentConsumeSucceeds() throws Exception {
        SavedTicket saved = save("openid-1");
        AtomicBoolean consumed = new AtomicBoolean();
        when(ops.getAndDelete(saved.key()))
                .thenAnswer(invocation -> consumed.compareAndSet(false, true) ? saved.storedValue() : null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> first = pool.submit(() -> {
                start.await();
                return store.consume(saved.ticket());
            });
            Future<String> second = pool.submit(() -> {
                start.await();
                return store.consume(saved.ticket());
            });
            start.countDown();
            List<String> results = Arrays.asList(
                    first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("openid-1", null);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void consumeRejectsBlankOrOversizedTicketsWithoutTouchingRedis() {
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume("  ")).isNull();
        assertThat(store.consume("x".repeat(129))).isNull();
    }

    private SavedTicket save(String openid) {
        String ticket = store.save(openid);
        String key = "yunhefc:auth:mini-bind:" + Hashing.sha256(ticket);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(key), value.capture(), eq(MiniBindTicketStore.TTL));
        return new SavedTicket(ticket, key, value.getValue());
    }

    private record SavedTicket(String ticket, String key, String storedValue) {
    }
}
