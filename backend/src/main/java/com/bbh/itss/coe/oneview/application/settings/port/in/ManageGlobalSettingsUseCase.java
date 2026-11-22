package com.bbh.itss.coe.oneview.application.settings.port.in;

import com.bbh.itss.coe.oneview.domain.settings.GlobalSettings;
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettingsValues;

public interface ManageGlobalSettingsUseCase {

    GlobalSettings ensureExists();

    GlobalSettings current();

    GlobalSettings update(Long expectedVersion, GlobalSettingsValues values);
}
