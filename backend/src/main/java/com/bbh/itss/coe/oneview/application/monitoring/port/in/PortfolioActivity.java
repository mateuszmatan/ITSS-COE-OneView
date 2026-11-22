package com.bbh.itss.coe.oneview.application.monitoring.port.in;

import com.bbh.itss.coe.oneview.domain.monitoring.DoraSummary;

public record PortfolioActivity(int pipelines, DoraSummary dora, String metricsError) {
}
