package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "QA_SETTINGS")
@NoArgsConstructor(access = PROTECTED)
public class QualitySettingsEntity extends SingletonEntity {

    private Integer coverageTarget;
    private Integer passRateTarget;
    private Integer maxCriticalDefects;

    static QualitySettingsEntity create() {
        return new QualitySettingsEntity();
    }

    QualityDefaults toDomain() {
        return new QualityDefaults(new QualityTargets(coverageTarget, passRateTarget, maxCriticalDefects), version(),
                updatedAt());
    }

    void apply(QualityTargets targets) {
        coverageTarget = targets.coveragePercent();
        passRateTarget = targets.passRatePercent();
        maxCriticalDefects = targets.maxCriticalDefects();
    }
}
