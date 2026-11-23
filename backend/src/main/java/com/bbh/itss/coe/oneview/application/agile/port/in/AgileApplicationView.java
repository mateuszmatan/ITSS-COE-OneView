package com.bbh.itss.coe.oneview.application.agile.port.in;

import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;

import java.util.List;

/**
 * An application (a product of the catalog) with its profile in Agile Assessment &amp; Adoption and its assessments,
 * the latest first.
 */
public record AgileApplicationView(long productId, String code, String name, Long departmentId, String departmentName,
                                   AgileProfile profile, List<AgileAssessment> assessments) {
}
