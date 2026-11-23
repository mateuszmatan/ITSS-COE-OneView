package com.bbh.itss.coe.oneview.domain.qa;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.Objects;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.staleVersion;

/**
 * The quality targets of BBH, which every application of the QM Metric Tool has unless it sets its own. The version
 * is {@code null} while the targets have never been saved.
 */
public record QualityDefaults(QualityTargets targets, Long version, Instant updatedAt) {

    public static final QualityDefaults INITIAL = new QualityDefaults(QualityTargets.BBH, null, null);

    public QualityDefaults change(Long expectedVersion, QualityTargets changed) {
        if (!Objects.equals(expectedVersion, version)) {
            throw staleVersion();
        }
        ValidationProblems problems = new ValidationProblems();
        (changed == null ? QualityTargets.NONE : changed).check(problems, true);
        problems.throwIfAny();
        return new QualityDefaults(changed, version, updatedAt);
    }
}
