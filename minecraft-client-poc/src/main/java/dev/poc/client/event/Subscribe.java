package dev.poc.client.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a single-argument method as an event handler.
 *
 * <p>Lower {@link #priority()} values run first. Handlers that mutate an event
 * (cancel it, rewrite a field) should sit low; handlers that only observe should
 * sit high so they see the final state.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    int HIGHEST = -1000;
    int NORMAL = 0;
    int MONITOR = 1000;

    int priority() default NORMAL;

    /** When false the handler is skipped once another handler has cancelled the event. */
    boolean receiveCancelled() default false;
}
