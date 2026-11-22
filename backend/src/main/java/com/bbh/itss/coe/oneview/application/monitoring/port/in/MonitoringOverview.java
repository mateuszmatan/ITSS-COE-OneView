package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import java.util.List;

public record MonitoringOverview(List<ProductHealth> products, String metricsError) {

    public MonitoringOverview {
        products = List.copyOf(products);
    }
}
