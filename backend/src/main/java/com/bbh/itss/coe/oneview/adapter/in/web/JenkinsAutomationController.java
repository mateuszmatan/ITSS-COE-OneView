package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.JenkinsAutomationUseCase;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.JenkinsAutomationUseCase.ConnectionCheck;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.coe.oneview.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.coe.oneview.adapter.in.web.InputFormats.URL_MESSAGE;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings.OIS_HOST_PATTERN;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings.OIS_HOST_RULE;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.SCHEDULE_PATTERN;
import static com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec.SCHEDULE_RULE;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/** The settings with which OneView acts in Jenkins and Bitbucket as a generic user. The OIS API key is never sent back. */
@RestController
@RequestMapping("/api/jenkins-automation")
@RequiredArgsConstructor
public class JenkinsAutomationController {

    private final JenkinsAutomationUseCase automation;
    private final ActionRecorder actions;

    @GetMapping
    public SettingsDto get() {
        return SettingsDto.from(automation.current());
    }

    @PutMapping
    public SettingsDto update(@Valid @RequestBody SettingsDto request) {
        return actions.record(ActionRecord.of("UPDATE_JENKINS_AUTOMATION", "SETTINGS", "Jenkins automation", null),
                () -> SettingsDto.from(automation.update(request.version(), request.toSettings())),
                "Updated the Jenkins automation");
    }

    /** Fetches the secret from OIS and signs in to Jenkins with it; changes nothing. */
    @PostMapping("/check")
    public ConnectionCheck check(@Valid @RequestBody CheckRequest request) {
        return automation.check(request.jenkinsUrl());
    }

    public record CheckRequest(@NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE)
                               String jenkinsUrl) {
    }

    @JsonIgnoreProperties(value = {"updatedAt", "oisApiKeySet", "ready", "missing"}, allowGetters = true)
    public record SettingsDto(
            Long version,
            Instant updatedAt,
            @Size(max = 200) String genericUser,
            @Size(max = 255) @Pattern(regexp = "^$|" + OIS_HOST_PATTERN, message = OIS_HOST_RULE) String oisHost,
            @JsonProperty(access = WRITE_ONLY) @Size(max = 500) String oisApiKey,
            Boolean oisApiKeySet,
            @Size(max = 100) @Pattern(regexp = "^$|" + SCHEDULE_PATTERN, message = SCHEDULE_RULE) String pollSchedule,
            @Size(max = 1000) @Pattern(regexp = "^((https?|ssh)://[^\\s$`\"\\\\]+)?$",
                    message = "must be the http, https or ssh address of the Git repository of the library")
            String libraryRepositoryUrl,
            @Size(max = 200) String libraryVersion,
            @Size(max = 200) String libraryCredentialsId,
            Boolean ready,
            List<String> missing) {

        static SettingsDto from(JenkinsAutomation stored) {
            JenkinsAutomationSettings settings = stored.settings();
            return new SettingsDto(stored.version(), stored.updatedAt(), settings.genericUser(), settings.oisHost(),
                    null, settings.oisApiKey() != null, settings.pollSchedule(), settings.libraryRepositoryUrl(),
                    settings.libraryVersion(), settings.libraryCredentialsId(), settings.ready(), settings.missing());
        }

        JenkinsAutomationSettings toSettings() {
            return new JenkinsAutomationSettings(genericUser, oisHost, oisApiKey, pollSchedule, libraryRepositoryUrl,
                    libraryVersion, libraryCredentialsId);
        }
    }
}
