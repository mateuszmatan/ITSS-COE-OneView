package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.application.actionlog.port.out.ActionLogRepositoryPort;
import com.bbh.itss.coe.oneview.domain.actionlog.ActionLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class ActionLogPersistenceAdapter implements ActionLogRepositoryPort {

    private final ActionLogJpaRepository repository;

    @Override
    public List<ActionLog> findAll() {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ActionLogEntity::toDomain)
                .toList();
    }

    @Override
    public void save(ActionLog log) {
        repository.save(ActionLogEntity.of(log));
    }
}
