package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.application.qa.port.out.QualityRepositoryPort;
import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.bbh.itss.coe.oneview.adapter.out.persistence.SingletonEntity.ID;

@Component
@RequiredArgsConstructor
class QualityPersistenceAdapter implements QualityRepositoryPort {

    private final QualitySettingsJpaRepository settings;
    private final QualityApplicationJpaRepository applications;

    @Override
    public Optional<QualityDefaults> loadDefaults() {
        return settings.findById(ID).map(QualitySettingsEntity::toDomain);
    }

    @Override
    public QualityDefaults saveDefaults(QualityDefaults defaults) {
        QualitySettingsEntity entity = settings.findById(ID).orElseGet(QualitySettingsEntity::create);
        entity.requireVersion(defaults.version());
        entity.apply(defaults.targets());
        return settings.saveAndFlush(entity).toDomain();
    }

    @Override
    public List<QualityProfile> findProfiles() {
        return applications.findAll().stream().map(QualityApplicationEntity::toDomain).toList();
    }

    @Override
    public Optional<QualityProfile> loadProfile(long productId) {
        return applications.findById(productId).map(QualityApplicationEntity::toDomain);
    }

    @Override
    public QualityProfile saveProfile(QualityProfile profile) {
        QualityApplicationEntity entity = applications.findById(profile.productId())
                .orElseGet(() -> new QualityApplicationEntity(profile.productId()));
        entity.requireVersion(profile.version());
        entity.apply(profile);
        return applications.saveAndFlush(entity).toDomain();
    }
}
