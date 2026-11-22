package com.bbh.itss.coe.oneview.application.settings.port.in;

import com.bbh.itss.coe.oneview.domain.settings.ServiceTemplate;
import com.bbh.itss.coe.oneview.domain.settings.StoredServiceTemplate;

public interface ManageServiceTemplateUseCase {

    StoredServiceTemplate current();

    StoredServiceTemplate update(Long expectedVersion, ServiceTemplate template);
}
