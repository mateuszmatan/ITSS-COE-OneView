package com.bbh.itss.coe.oneview.application.catalog.port.out;

import java.util.Map;

public interface PipelineCountsPort {

    Map<Long, Long> pipelinesPerProduct();

    Map<Long, Long> activePipelinesPerProduct();
}
