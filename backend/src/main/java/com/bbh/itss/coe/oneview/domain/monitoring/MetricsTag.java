package com.bbh.itss.coe.oneview.domain.monitoring;

import com.bbh.itss.coe.oneview.domain.catalog.MetricsSettings;
import com.bbh.itss.coe.oneview.domain.catalog.Service;
import com.bbh.itss.coe.oneview.domain.pipeline.Pipeline;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;

public record MetricsTag(String project, String env) {

    public static MetricsTag of(Service service, Pipeline pipeline) {
        MetricsSettings metrics = service.settings().metrics();
        return of(metrics.influxProject(), metrics.influxEnv(), pipeline.type());
    }

    public static MetricsTag of(String influxProject, String influxEnv, PipelineType type) {
        return new MetricsTag(type.influxProjectTag(influxProject), influxEnv);
    }
}
