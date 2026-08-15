package dev.poc.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Méthode publique à un seul paramètre de type {@link Event}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    int priority() default 0;

    /** Si false, le handler n'est pas appelé pour un événement déjà annulé. */
    boolean receiveCancelled() default false;
}
