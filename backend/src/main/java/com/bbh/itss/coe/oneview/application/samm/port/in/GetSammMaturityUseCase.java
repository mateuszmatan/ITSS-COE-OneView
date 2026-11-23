package com.bbh.itss.coe.oneview.application.samm.port.in;

import com.bbh.itss.coe.oneview.domain.samm.SammApplicationScore;
import com.bbh.itss.coe.oneview.domain.samm.SammDepartmentScore;
import com.bbh.itss.coe.oneview.domain.samm.SammOrganizationOverview;

import java.util.List;
import java.util.Optional;

public interface GetSammMaturityUseCase {

    SammOrganizationOverview overview();

    /** The overview of one business service, or of one application (product), when given; else of BBH. */
    SammOrganizationOverview overview(Long departmentId, Long productId);

    List<SammDepartmentScore> departmentScores();

    Optional<SammDepartmentScore> departmentScore(Long departmentId);

    Optional<SammApplicationScore> applicationScore(Long pipelineId);
}
