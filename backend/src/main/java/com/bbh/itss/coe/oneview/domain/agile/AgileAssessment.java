package com.bbh.itss.coe.oneview.domain.agile;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static java.math.RoundingMode.HALF_UP;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * One agile maturity assessment of an application: the day it was made, who made it, the level reached in every
 * {@link AgileDimension} (1 to 5) and notes. Its overall level is the mean of the levels, to one decimal.
 */
public record AgileAssessment(Long id, long productId, LocalDate assessedOn, String assessor,
                              Map<AgileDimension, Integer> levels, String notes) {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int MAX_ASSESSOR = 200;
    public static final int MAX_NOTES = 4000;

    public AgileAssessment {
        assessor = trimToNull(assessor);
        notes = trimToNull(notes);
        levels = levels == null || levels.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new EnumMap<>(levels));
    }

    /** A new assessment, checked: every dimension rated, the day not in the future. */
    public static AgileAssessment record(long productId, LocalDate assessedOn, String assessor,
                                         Map<AgileDimension, Integer> levels, String notes, LocalDate today) {
        AgileAssessment assessment = new AgileAssessment(null, productId, assessedOn, assessor, levels, notes);
        ValidationProblems problems = new ValidationProblems();
        if (assessedOn == null) {
            problems.add("assessedOn", "set the day of the assessment");
        } else if (assessedOn.isAfter(today)) {
            problems.add("assessedOn", "must not be in the future");
        }
        if (assessment.assessor() == null) {
            problems.add("assessor", "name who made the assessment");
        }
        problems.fits("assessor", assessment.assessor(), MAX_ASSESSOR).fits("notes", assessment.notes(), MAX_NOTES);
        ValidationProblems levelProblems = problems.at("levels");
        for (AgileDimension dimension : AgileDimension.values()) {
            Integer level = assessment.levels().get(dimension);
            if (level == null) {
                levelProblems.add(dimension.name(), "rate " + dimension.label() + " from " + MIN_LEVEL + " to " + MAX_LEVEL);
            } else if (level < MIN_LEVEL || level > MAX_LEVEL) {
                levelProblems.add(dimension.name(), "must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
            }
        }
        problems.throwIfAny();
        return assessment;
    }

    /** The mean of the levels of the dimensions, to one decimal; {@code null} without levels. */
    public BigDecimal overallLevel() {
        if (levels.isEmpty()) {
            return null;
        }
        int sum = levels.values().stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(levels.size()), 1, HALF_UP);
    }
}
