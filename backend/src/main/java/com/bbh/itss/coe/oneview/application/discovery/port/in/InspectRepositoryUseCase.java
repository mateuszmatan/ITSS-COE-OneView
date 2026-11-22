package com.bbh.itss.coe.oneview.application.discovery.port.in;

import com.bbh.itss.coe.oneview.domain.discovery.DiscoveredStack;

public interface InspectRepositoryUseCase {

    DiscoveredStack inspect(InspectRepositoryCommand command);
}
