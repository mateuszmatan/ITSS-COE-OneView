package com.bbh.itss.coe.oneview.domain.catalog;

public record CatalogUsage(long productCount, long serviceCount, long pipelineCount, long activePipelineCount) {

    public static final CatalogUsage UNUSED = new CatalogUsage(0, 0, 0, 0);
}
