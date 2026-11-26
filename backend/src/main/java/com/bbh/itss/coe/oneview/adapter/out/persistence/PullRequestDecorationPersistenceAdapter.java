package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.application.bitbucket.port.out.PullRequestDecorationPort;
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class PullRequestDecorationPersistenceAdapter implements PullRequestDecorationPort {

    private final PullRequestDecorationJpaRepository repository;

    @Override
    public PullRequestDecoration save(PullRequestDecoration decoration) {
        return repository.saveAndFlush(PullRequestDecorationEntity.of(decoration)).toDomain();
    }

    @Override
    public List<PullRequestDecoration> findRecentByPipelineId(long pipelineId, int limit) {
        return repository.findByPipelineIdOrderByRecordedAtDescIdDesc(pipelineId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(PullRequestDecorationEntity::toDomain)
                .toList();
    }
}
