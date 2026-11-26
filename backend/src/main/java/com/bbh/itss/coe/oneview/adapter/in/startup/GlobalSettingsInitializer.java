package com.bbh.itss.coe.oneview.adapter.in.startup;

import com.bbh.itss.coe.oneview.application.settings.port.in.ManageGlobalSettingsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalSettingsInitializer implements ApplicationRunner {

    private final ManageGlobalSettingsUseCase settings;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking and initializing the Library defaults of OneView...");
        settings.ensureExists();
        log.info("Library defaults of OneView ready");
    }
}
