package com.fourth.ykd.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 断路器切面 —— 位于 {@code @Retryable} 之外的最外层。
 * <p>
 * 按断路器名称记录失败次数。当断路器打开时，调用会被短路，
 * 直接返回合理的降级值（空列表 / null / 空字符串），
 * 直到打开超时时间结束且半开探测成功。
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class CircuitBreakerAspect {

    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    @Around("@annotation(cb)")
    public Object around(ProceedingJoinPoint pjp, CircuitBreaker cb) throws Throwable {
        String name = cb.name();
        CircuitState state = circuits.computeIfAbsent(name,
                k -> new CircuitState(cb.failureThreshold(), cb.openTimeoutMs()));

        if (state.tryAcquire()) {
            try {
                Object result = pjp.proceed();
                state.recordSuccess();
                return result;
            } catch (Throwable t) {
                state.recordFailure();
                if (state.isOpen()) {
                    log.warn("[RAG][CIRCUIT] circuit OPEN for={}, failures={}, returning fallback",
                            name, state.failures.get());
                }
                return fallback(pjp);
            }
        } else {
            log.warn("[RAG][CIRCUIT] circuit OPEN for={}, short-circuiting", name);
            return fallback(pjp);
        }
    }

    private static Object fallback(ProceedingJoinPoint pjp) {
        Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
        if (returnType == List.class) {
            return Collections.emptyList();
        }
        if (returnType == String.class) {
            return "";
        }
        return null;
    }

    // ─── inner state machine ────────────────────────────────

    private enum Status { CLOSED, OPEN, HALF_OPEN }

    private static class CircuitState {
        final int failureThreshold;
        final long openTimeoutMs;
        final AtomicInteger failures = new AtomicInteger(0);
        final AtomicLong openedAt = new AtomicLong(0);
        volatile Status status = Status.CLOSED;

        CircuitState(int failureThreshold, long openTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.openTimeoutMs = openTimeoutMs;
        }

        synchronized boolean tryAcquire() {
            if (status == Status.CLOSED) return true;
            if (status == Status.HALF_OPEN) return true;
            // OPEN — check timeout
            if (System.currentTimeMillis() - openedAt.get() > openTimeoutMs) {
                status = Status.HALF_OPEN;
                return true;
            }
            return false;
        }

        synchronized void recordSuccess() {
            failures.set(0);
            status = Status.CLOSED;
        }

        synchronized void recordFailure() {
            int count = failures.incrementAndGet();
            if (count >= failureThreshold) {
                status = Status.OPEN;
                openedAt.set(System.currentTimeMillis());
            }
        }

        synchronized boolean isOpen() {
            return status == Status.OPEN;
        }
    }
}