package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.application.agile.port.out.AgileRepositoryPort;
import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class AgilePersistenceAdapter implements AgileRepositoryPort {

    private final AgileApplicationJpaRepository applications;
    private final AgileAssessmentJpaRepository assessments;

    @Override
    public List<AgileProfile> findProfiles() {
        return applications.findAll().stream().map(AgileApplicationEntity::toDomain).toList();
    }

    @Override
    public Optional<AgileProfile> loadProfile(long productId) {
        return applications.findById(productId).map(AgileApplicationEntity::toDomain);
    }

    @Override
    public AgileProfile saveProfile(AgileProfile profile) {
        AgileApplicationEntity entity = applications.findById(profile.productId())
                .orElseGet(() -> new AgileApplicationEntity(profile.productId()));
        entity.requireVersion(profile.version());
        entity.apply(profile);
        return applications.saveAndFlush(entity).toDomain();
    }

    @Override
    public List<AgileAssessment> findAssessments() {
        return assessments.findAllByOrderByAssessedOnDescIdDesc().stream()
                .map(AgileAssessmentEntity::toDomain).toList();
    }

    @Override
    public List<AgileAssessment> findAssessments(long productId) {
        return assessments.findByProductIdOrderByAssessedOnDescIdDesc(productId).stream()
                .map(AgileAssessmentEntity::toDomain).toList();
    }

    @Override
    public Optional<AgileAssessment> loadAssessment(long id) {
        return assessments.findById(id).map(AgileAssessmentEntity::toDomain);
    }

    @Override
    public AgileAssessment saveAssessment(AgileAssessment assessment) {
        return assessments.saveAndFlush(AgileAssessmentEntity.of(assessment)).toDomain();
    }

    @Override
    public void deleteAssessment(long id) {
        assessments.findById(id).ifPresent(assessments::delete);
    }
}
