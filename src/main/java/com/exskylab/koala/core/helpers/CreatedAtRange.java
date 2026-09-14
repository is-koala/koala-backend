package com.exskylab.koala.core.helpers;

import java.time.Instant;

public record CreatedAtRange(Instant from, Instant to) {

    public static final Instant BEGINNING_OF_TIME = Instant.EPOCH;
    public static final Instant END_OF_TIME = Instant.parse("9999-12-31T23:59:59Z");
    public static final CreatedAtRange ANY = new CreatedAtRange(BEGINNING_OF_TIME, END_OF_TIME);

    public static CreatedAtRange of(Instant from, Instant to) {
        return new CreatedAtRange(from == null ? BEGINNING_OF_TIME : from, to == null ? END_OF_TIME : to);
    }
}
