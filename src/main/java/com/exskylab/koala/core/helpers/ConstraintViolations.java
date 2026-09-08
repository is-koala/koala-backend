package com.exskylab.koala.core.helpers;

import org.hibernate.exception.ConstraintViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    public static boolean named(Throwable exception, String constraintName) {
        return constraintName.equals(nameOf(exception));
    }

    public static String nameOf(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
