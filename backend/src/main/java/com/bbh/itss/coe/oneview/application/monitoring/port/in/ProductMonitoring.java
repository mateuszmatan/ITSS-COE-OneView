package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import com.bbh.itss.coe.oneview.domain.catalog.Product;
import com.bbh.itss.coe.oneview.domain.monitoring.RunResult;

import java.util.List;

public record ProductMonitoring(Product product, RunResult overall, List<PipelineHealth> pipelines,
                                String metricsError) {

    public ProductMonitoring {
        pipelines = List.copyOf(pipelines);
    }
}
