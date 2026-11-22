package com.bbh.itss.coe.oneview.application.discovery.port.in;

public record InspectRepositoryCommand(
        String repositoryUrl,
        String branch,
        String credentialsId
) {
}
