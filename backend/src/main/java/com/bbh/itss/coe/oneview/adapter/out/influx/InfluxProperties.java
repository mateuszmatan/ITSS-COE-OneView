package com.bbh.itss.coe.oneview.adapter.out.influx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static com.bbh.itss.coe.oneview.adapter.out.influx.Flux.duration;

@ConfigurationProperties("oneview.influx")
public record InfluxProperties(
        String url,
        String org,
        String bucket,
        String token,
        @DefaultValue("365d") String lastRunLookback) {

    public InfluxProperties {
        duration(lastRunLookback);
    }
}
