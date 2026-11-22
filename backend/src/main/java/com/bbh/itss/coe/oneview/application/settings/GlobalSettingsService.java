package com.bbh.itss.coe.oneview.application.settings;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.settings.port.in.ManageGlobalSettingsUseCase;
import com.bbh.itss.coe.oneview.application.settings.port.out.GlobalSettingsRepositoryPort;
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettings;
import com.bbh.itss.coe.oneview.domain.settings.GlobalSettingsValues;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.coe.oneview.domain.settings.GlobalSettings.bbhDefaults;

@UseCase
@RequiredArgsConstructor
public class GlobalSettingsService implements ManageGlobalSettingsUseCase {

    private final GlobalSettingsRepositoryPort repository;

    @Override
    public GlobalSettings ensureExists() {
        return repository.load().orElseGet(() -> repository.save(bbhDefaults()));
    }

    @Override
    @ReadOnly
    public GlobalSettings current() {
        return load();
    }

    @Override
    public GlobalSettings update(Long expectedVersion, GlobalSettingsValues values) {
        return repository.save(load().change(expectedVersion, values));
    }

    private GlobalSettings load() {
        return repository.load().orElseThrow(() -> new IllegalArgumentException(
                "The global settings are missing; the portal creates them at start-up"));
    }
}
