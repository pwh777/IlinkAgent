package com.fourth.ykd.ai.config.ragpro;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 启用 AOP 代理（{@code @Aspect} 所必需）以及 Spring Retry
 * （{@code @Retryable / @Recover}），并指定显式的执行顺序：
 *
 * <pre>{@code
 *   CircuitBreakerAspect (@Order(1))
 *     → @Retryable (order = 2)
 *       → RagMetricsAspect (@Order(3))
 *         → 实际方法
 * }</pre>
 *
 * <p>
 * {@code exposeProxy = true} 允许使用 {@code AopContext.currentProxy()}，
 * 用于类内自调用场景：即某个 public 方法需要调用同类中另一个带注解的方法时，
 * 通过暴露代理对象保证注解生效。
 * </p>
 */
@Configuration
//开启AOP代理能够在业务逻辑中插入一些熔断，重试，日志监控，统计耗时之类的公共逻辑
@EnableAspectJAutoProxy(exposeProxy = true)
//开启重试机制
@EnableRetry(order = 2)
public class RagAopConfig {
}