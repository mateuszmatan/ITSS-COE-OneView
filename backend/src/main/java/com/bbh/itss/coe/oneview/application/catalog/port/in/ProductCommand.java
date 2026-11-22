package com.bbh.itss.coe.oneview.application.catalog.port.in;

import com.bbh.itss.coe.oneview.domain.catalog.ProductDetails;
import com.bbh.itss.coe.oneview.domain.catalog.ServiceDraft;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;

import java.util.List;

import static com.bbh.itss.coe.oneview.domain.shared.Text.clean;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/**
 * A product to store. The pipeline type and the agent labels apply to the pipelines this call creates; without labels
 * they keep the service template's.
 */
public record ProductCommand(Long version, ProductDetails details, List<ServiceDraft> services,
                             PipelineType pipelineType, List<String> agentLabels) {

    public ProductCommand {
        services = List.copyOf(emptyIfNull(services));
        agentLabels = clean(agentLabels);
    }
}
