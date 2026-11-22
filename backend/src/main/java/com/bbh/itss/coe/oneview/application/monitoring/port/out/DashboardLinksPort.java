package com.bbh.itss.coe.oneview.application.monitoring.port.out;

import com.bbh.itss.coe.oneview.domain.monitoring.DashboardLink;
import com.bbh.itss.coe.oneview.domain.monitoring.MetricsTag;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;
import com.bbh.itss.coe.oneview.domain.settings.PlatformSettings;

import java.util.List;

public interface DashboardLinksPort {

    List<DashboardLink> dashboards(PlatformSettings platform, MetricsTag tag, PipelineType type, int rangeDays);
}
