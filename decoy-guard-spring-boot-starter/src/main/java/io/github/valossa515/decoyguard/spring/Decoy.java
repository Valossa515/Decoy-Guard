package io.github.valossa515.decoyguard.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean as the decoy implementation of one or more interfaces.
 *
 * <p>Callers keep injecting the interface. While the current session is NORMAL they get the real
 * implementation; while it is DURESS every call is transparently routed to this bean instead.
 * Decoy beans are never injected directly, so they cannot leak into normal sessions.
 *
 * <pre>{@code
 * @Decoy
 * @Service
 * class DecoyAccountService implements AccountService { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Decoy {

    /**
     * Interfaces this bean stands in for. Empty means every interface the bean implements.
     */
    Class<?>[] value() default {};
}
