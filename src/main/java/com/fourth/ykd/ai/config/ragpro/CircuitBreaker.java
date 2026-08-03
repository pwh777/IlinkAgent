package com.fourth.ykd.ai.config.ragpro;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义轻量级熔断器注解。
 *
 * <p>当连续失败次数达到 {@link #failureThreshold()} 时，熔断器打开。
 * 在打开期间所有调用直接短路降级，不执行实际逻辑。
 * 经过 {@link #openTimeoutMs()} 毫秒后熔断器转为半开，允许一次探测调用通过。
 * 如果探测成功则关闭熔断器恢复正常；如果失败则立即重新打开。
 * </p>
 *
 * <p>使用示例：
 * <pre>{@code
 *   @CircuitBreaker(name = "rag-search", failureThreshold = 5, openTimeoutMs = 30000)
 *   public List<Document> search(String question) { ... }
 * }</pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CircuitBreaker {

    /** 熔断器唯一名称，用于区分不同熔断实例 */
    String name() default "default";

    /** 连续失败多少次后打开熔断器 */
    int failureThreshold() default 5;

    /** 熔断器打开后，多少毫秒后转为半开状态 */
    long openTimeoutMs() default 30000;
}