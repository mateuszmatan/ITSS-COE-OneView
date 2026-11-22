package com.bbh.itss.coe.oneview.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record InspectRepoRequest(
        @NotBlank String repositoryUrl,
        String branch,
        String credentialsId
) {
}
