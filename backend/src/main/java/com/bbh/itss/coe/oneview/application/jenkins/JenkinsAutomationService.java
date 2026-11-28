package com.bbh.itss.coe.oneview.application.jenkins;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.WithoutTransaction;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.JenkinsAutomationUseCase;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.GenericUserSecretPort;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsAutomationRepositoryPort;
import com.bbh.itss.coe.oneview.application.jenkins.port.out.JenkinsPort;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomation;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsAutomationSettings;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.coe.oneview.application.jenkins.GenericUser.signIn;

@UseCase
@RequiredArgsConstructor
public class JenkinsAutomationService implements JenkinsAutomationUseCase {

    private final JenkinsAutomationRepositoryPort repository;
    private final GenericUserSecretPort secrets;
    private final JenkinsPort jenkins;

    @Override
    @ReadOnly
    public JenkinsAutomation current() {
        return repository.load().orElse(JenkinsAutomation.NOT_SET);
    }

    @Override
    public JenkinsAutomation update(Long expectedVersion, JenkinsAutomationSettings settings) {
        return repository.save(current().change(expectedVersion, settings));
    }

    @Override
    @WithoutTransaction
    public ConnectionCheck check(String jenkinsUrl) {
        try {
            String address = JenkinsJobAddress.ofFolder("jenkinsUrl", jenkinsUrl, null).jenkinsUrl();
            String name = jenkins.whoAmI(address, signIn(current().settings(), secrets));
            return new ConnectionCheck(true, "OIS gave the secret and " + address + " knows the generic user as "
                    + name + ".");
        } catch (RuntimeException e) {
            return new ConnectionCheck(false, e.getMessage());
        }
    }
}
