package com.bbh.itss.coe.oneview.application.qa.port.in;

import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;

/** An application (a product of the catalog) with its profile in the QM Metric Tool and the targets it has. */
public record QualityApplicationView(long productId, String code, String name, Long departmentId,
                                     String departmentName, QualityProfile profile, QualityTargets effectiveTargets) {
}
