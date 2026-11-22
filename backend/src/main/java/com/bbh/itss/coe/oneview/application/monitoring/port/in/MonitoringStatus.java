package com.bbh.itss.coe.oneview.application.monitoring.port.in;

public record MonitoringStatus(boolean metricsConfigured, boolean metricsReachable, String metricsError) {
}
