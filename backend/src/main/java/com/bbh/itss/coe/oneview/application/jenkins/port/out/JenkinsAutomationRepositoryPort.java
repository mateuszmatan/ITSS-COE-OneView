package com.bbh.itss.coe.oneview.application.jenkins.port.out;

import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;

import java.util.Optional;

public interface JenkinsAutomationRepositoryPort {

    Optional<JenkinsAutomation> load();

    JenkinsAutomation save(JenkinsAutomation automation);
}
