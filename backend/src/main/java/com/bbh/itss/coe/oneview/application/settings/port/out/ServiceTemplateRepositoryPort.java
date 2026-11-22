package com.bbh.itss.coe.oneview.application.settings.port.out;

import com.bbh.itss.coe.oneview.domain.settings.StoredServiceTemplate;

import java.util.Optional;

public interface ServiceTemplateRepositoryPort {

    Optional<StoredServiceTemplate> load();

    StoredServiceTemplate save(StoredServiceTemplate template);
}
