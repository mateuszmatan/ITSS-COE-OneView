package com.bbh.itss.coe.oneview.application.agile.port.in;

import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Agile Assessment &amp; Adoption: how every application takes part and the maturity assessments of its team. */
public interface AgileAssessmentUseCase {

    /** Every application of the catalog, by business service and name, whether it takes part or not. */
    List<AgileApplicationView> applications();

    AgileApplicationView application(long productId);

    AgileApplicationView changeProfile(long productId, Long version, AgileProfile changed);

    AgileAssessment recordAssessment(long productId, LocalDate assessedOn, String assessor,
                                     Map<AgileDimension, Integer> levels, String notes);

    AgileAssessment assessment(long assessmentId);

    void deleteAssessment(long assessmentId);
}
