package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsAutomationRepositoryPort;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.bbh.itss.coe.oneview.adapter.out.persistence.SingletonEntity.ID;

@Component
@RequiredArgsConstructor
class JenkinsAutomationPersistenceAdapter implements JenkinsAutomationRepositoryPort {

    private final JenkinsAutomationJpaRepository repository;

    @Override
    public Optional<JenkinsAutomation> load() {
        return repository.findById(ID).map(JenkinsAutomationEntity::toDomain);
    }

    @Override
    public JenkinsAutomation save(JenkinsAutomation automation) {
        JenkinsAutomationEntity entity = repository.findById(ID).orElseGet(JenkinsAutomationEntity::new);
        if (!entity.isNew()) {
            entity.requireVersion(automation.version());
        }
        entity.apply(automation.settings());
        return repository.saveAndFlush(entity).toDomain();
    }
}
