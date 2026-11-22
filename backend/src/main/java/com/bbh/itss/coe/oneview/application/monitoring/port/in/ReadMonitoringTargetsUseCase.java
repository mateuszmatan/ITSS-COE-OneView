package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import com.bbh.itss.coe.oneview.domain.settings.PlatformSettings;

public interface ReadMonitoringTargetsUseCase {

    PlatformSettings platform();

    MonitoringTargets everything();

    MonitoringTargets ofDepartment(long departmentId);

    MonitoringTargets ofProduct(long productId);

    MonitoringTargets ofPipeline(long pipelineId);
}
