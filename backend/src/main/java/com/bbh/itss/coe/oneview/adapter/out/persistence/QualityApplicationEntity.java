package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "QA_APPLICATION")
@NoArgsConstructor(access = PROTECTED)
public class QualityApplicationEntity extends ProductOwnedEntity {

    private boolean enabled;
    private String qaLead;
    private String sonarProjectKey;
    private String defectProjectKey;
    private String dashboardUrl;
    private Integer coverageTarget;
    private Integer passRateTarget;
    private Integer maxCriticalDefects;

    QualityApplicationEntity(long productId) {
        super(productId);
    }

    QualityProfile toDomain() {
        return new QualityProfile(getId(), enabled, qaLead, sonarProjectKey, defectProjectKey, dashboardUrl,
                new QualityTargets(coverageTarget, passRateTarget, maxCriticalDefects), version(), updatedAt());
    }

    void apply(QualityProfile profile) {
        enabled = profile.enabled();
        qaLead = profile.qaLead();
        sonarProjectKey = profile.sonarProjectKey();
        defectProjectKey = profile.defectProjectKey();
        dashboardUrl = profile.dashboardUrl();
        coverageTarget = profile.targets().coveragePercent();
        passRateTarget = profile.targets().passRatePercent();
        maxCriticalDefects = profile.targets().maxCriticalDefects();
    }
}
