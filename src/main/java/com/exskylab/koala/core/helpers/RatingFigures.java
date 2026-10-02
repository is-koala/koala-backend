package com.exskylab.koala.core.helpers;

import java.math.BigDecimal;

public record RatingFigures(BigDecimal averageRating, int reviewCount) {

    public static final RatingFigures UNRATED = new RatingFigures(BigDecimal.ZERO, 0);
}
