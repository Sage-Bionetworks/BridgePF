package org.sagebionetworks.bridge.exceptions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For exceptions that are not server-side exceptions (thus not logged as errors) and
 * that do not log stack traces.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NoStackTraceException {
}
