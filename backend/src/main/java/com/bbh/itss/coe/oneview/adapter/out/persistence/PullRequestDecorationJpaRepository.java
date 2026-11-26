package com.bbh.itss.coe.oneview.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PullRequestDecorationJpaRepository extends JpaRepository<PullRequestDecorationEntity, Long> {

    List<PullRequestDecorationEntity> findByPipelineIdOrderByRecordedAtDescIdDesc(Long pipelineId, Pageable page);
}
