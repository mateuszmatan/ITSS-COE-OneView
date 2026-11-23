package com.bbh.itss.coe.oneview.application.monitoring.port.in;

public interface MonitorPipelinesUseCase {

    MonitoringStatus status();

    MonitoringOverview overview();

    DepartmentPipelines department(long departmentId);

    /** The pipelines of every business service, as {@link #department(long)} lists the ones of one. */
    DepartmentPipelines everyPipeline();

    ProductMonitoring product(long productId);

    PipelineMonitoring pipeline(long pipelineId, String range);

    PortfolioActivity activity(String range);

    /** The activity of one business service, or of one application (product), when given; else of BBH. */
    PortfolioActivity activity(String range, Long departmentId, Long productId);
}
