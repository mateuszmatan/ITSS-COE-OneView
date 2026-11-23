package com.bbh.itss.coe.oneview.application.qa.port.in;

import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;

import java.util.List;

/** The QM Metric Tool: the quality targets of BBH and how every application takes part. */
public interface QualityManagementUseCase {

    QualityDefaults defaults();

    QualityDefaults changeDefaults(Long version, QualityTargets targets);

    /** Every application of the catalog, by business service and name, whether it takes part or not. */
    List<QualityApplicationView> applications();

    QualityApplicationView application(long productId);

    QualityApplicationView change(long productId, Long version, QualityProfile changed);
}
