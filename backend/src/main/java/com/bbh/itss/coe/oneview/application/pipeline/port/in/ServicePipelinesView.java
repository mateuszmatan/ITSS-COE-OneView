package com.bbh.itss.coe.oneview.application.pipeline.port.in;

import com.bbh.itss.coe.oneview.domain.catalog.Service;

import java.util.List;

public record ServicePipelinesView(Service service, List<PipelineView> pipelines) {

    public ServicePipelinesView {
        pipelines = List.copyOf(pipelines);
    }
}
