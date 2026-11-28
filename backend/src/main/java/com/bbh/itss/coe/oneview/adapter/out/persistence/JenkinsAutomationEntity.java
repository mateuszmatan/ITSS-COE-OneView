package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PACKAGE;

@Entity
@Table(name = "DSO_JENKINS_AUTOMATION")
@NoArgsConstructor(access = PACKAGE)
public class JenkinsAutomationEntity extends SingletonEntity {

    private String genericUser;
    private String oisHost;
    private String oisApiKey;
    private String pollSchedule;
    private String libraryRepositoryUrl;
    private String libraryVersion;
    private String libraryCredentialsId;

    JenkinsAutomation toDomain() {
        return new JenkinsAutomation(new JenkinsAutomationSettings(genericUser, oisHost, oisApiKey, pollSchedule,
                libraryRepositoryUrl, libraryVersion, libraryCredentialsId), version(), updatedAt());
    }

    void apply(JenkinsAutomationSettings settings) {
        genericUser = settings.genericUser();
        oisHost = settings.oisHost();
        oisApiKey = settings.oisApiKey();
        pollSchedule = settings.pollSchedule();
        libraryRepositoryUrl = settings.libraryRepositoryUrl();
        libraryVersion = settings.libraryVersion();
        libraryCredentialsId = settings.libraryCredentialsId();
    }
}
