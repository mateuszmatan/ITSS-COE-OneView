package com.bbh.itss.coe.oneview.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgileAssessmentJpaRepository extends JpaRepository<AgileAssessmentEntity, Long> {

    List<AgileAssessmentEntity> findAllByOrderByAssessedOnDescIdDesc();

    List<AgileAssessmentEntity> findByProductIdOrderByAssessedOnDescIdDesc(Long productId);
}
