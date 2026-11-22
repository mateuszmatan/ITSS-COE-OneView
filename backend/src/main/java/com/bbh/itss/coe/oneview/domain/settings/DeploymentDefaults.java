package com.bbh.itss.coe.oneview.domain.settings;

import com.bbh.itss.coe.oneview.domain.catalog.DeployTarget;
import com.bbh.itss.coe.oneview.domain.catalog.Region;
import com.bbh.itss.coe.oneview.domain.shared.ConfigTree;
import lombok.Builder;

import static com.bbh.itss.coe.oneview.domain.catalog.DeployTarget.VM;
import static com.bbh.itss.coe.oneview.domain.catalog.Region.RD;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Builder
public record DeploymentDefaults(String urbanCodeSiteName, String urbanCodeDeployProcess, String rdHost, String qcHost,
                                 String sshUser, String deployScript, String versionFile) {

    public DeploymentDefaults {
        urbanCodeSiteName = trimToNull(urbanCodeSiteName);
        urbanCodeDeployProcess = trimToNull(urbanCodeDeployProcess);
        rdHost = trimToNull(rdHost);
        qcHost = trimToNull(qcHost);
        sshUser = trimToNull(sshUser);
        deployScript = trimToNull(deployScript);
        versionFile = trimToNull(versionFile);
    }

    public void fillIn(ConfigTree config, DeployTarget target) {
        if (target != VM) {
            return;
        }
        config.fillIn("deploy.vm.dod", "siteName", urbanCodeSiteName)
                .fillIn("deploy.vm.dod", "deployProcess", urbanCodeDeployProcess);
        for (Region region : Region.values()) {
            String path = "deploy.vm." + region.configKey() + ".";
            config.setIfAbsent(path + "host", region == RD ? rdHost : qcHost)
                    .setIfAbsent(path + "user", sshUser)
                    .setIfAbsent(path + "deployScript", deployScript)
                    .setIfAbsent(path + "versionFile", versionFile);
        }
    }
}
