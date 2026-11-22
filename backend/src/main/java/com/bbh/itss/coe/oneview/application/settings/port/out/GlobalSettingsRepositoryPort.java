package com.bbh.itss.coe.oneview.application.settings.port.out;

import com.bbh.itss.coe.oneview.domain.settings.GlobalSettings;

import java.util.Optional;

public interface GlobalSettingsRepositoryPort {

    Optional<GlobalSettings> load();

    GlobalSettings save(GlobalSettings settings);
}
