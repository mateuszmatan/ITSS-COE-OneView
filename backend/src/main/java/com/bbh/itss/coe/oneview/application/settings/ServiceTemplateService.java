package com.bbh.itss.coe.oneview.application.settings;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.settings.port.in.ManageServiceTemplateUseCase;
import com.bbh.itss.coe.oneview.application.settings.port.out.ServiceTemplateRepositoryPort;
import com.bbh.itss.coe.oneview.domain.settings.ServiceTemplate;
import com.bbh.itss.coe.oneview.domain.settings.StoredServiceTemplate;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ServiceTemplateService implements ManageServiceTemplateUseCase {

    private final ServiceTemplateRepositoryPort repository;

    @Override
    @ReadOnly
    public StoredServiceTemplate current() {
        return stored();
    }

    @Override
    public StoredServiceTemplate update(Long expectedVersion, ServiceTemplate template) {
        return repository.save(stored().change(expectedVersion, template));
    }

    private StoredServiceTemplate stored() {
        return repository.load().orElseGet(StoredServiceTemplate::unsaved);
    }
}
