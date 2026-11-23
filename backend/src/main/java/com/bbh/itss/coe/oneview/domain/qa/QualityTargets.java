package com.bbh.itss.coe.oneview.domain.qa;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import static org.apache.commons.lang3.ObjectUtils.getIfNull;

/**
 * The quality targets of the QM Metric Tool: the least code coverage and test pass rate in percent, and the most
 * open critical defects. On an application a target left out ({@code null}) is the target of BBH.
 */
public record QualityTargets(Integer coveragePercent, Integer passRatePercent, Integer maxCriticalDefects) {

    public static final int MAX_DEFECTS = 999_999;

    /** The targets of BBH while no administrator has changed them. */
    public static final QualityTargets BBH = new QualityTargets(80, 95, 0);

    public static final QualityTargets NONE = new QualityTargets(null, null, null);

    /** Each target of this one, and the one of {@code fallback} where this one has none. */
    public QualityTargets over(QualityTargets fallback) {
        return new QualityTargets(getIfNull(coveragePercent, fallback.coveragePercent()),
                getIfNull(passRatePercent, fallback.passRatePercent()),
                getIfNull(maxCriticalDefects, fallback.maxCriticalDefects()));
    }

    public boolean empty() {
        return coveragePercent == null && passRatePercent == null && maxCriticalDefects == null;
    }

    void check(ValidationProblems problems, boolean required) {
        percent(problems, "coveragePercent", coveragePercent, required);
        percent(problems, "passRatePercent", passRatePercent, required);
        if (maxCriticalDefects == null) {
            if (required) {
                problems.add("maxCriticalDefects", "set the most open critical defects allowed");
            }
        } else if (maxCriticalDefects < 0 || maxCriticalDefects > MAX_DEFECTS) {
            problems.add("maxCriticalDefects", "must be between 0 and " + MAX_DEFECTS);
        }
    }

    private static void percent(ValidationProblems problems, String field, Integer value, boolean required) {
        if (value == null) {
            if (required) {
                problems.add(field, "set a target between 0 and 100");
            }
        } else if (value < 0 || value > 100) {
            problems.add(field, "must be between 0 and 100");
        }
    }
}
