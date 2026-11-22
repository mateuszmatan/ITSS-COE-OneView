package com.bbh.itss.coe.oneview.application.monitoring.port.in;

public interface MonitorPipelinesUseCase {

    MonitoringStatus status();

    MonitoringOverview overview();

    DepartmentPipelines department(long departmentId);

    ProductMonitoring product(long productId);

    PipelineMonitoring pipeline(long pipelineId, String range);

    PortfolioActivity activity(String range);
}
