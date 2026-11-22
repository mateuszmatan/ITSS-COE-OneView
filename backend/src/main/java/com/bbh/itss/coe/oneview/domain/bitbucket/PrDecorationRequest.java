package com.bbh.itss.coe.oneview.domain.bitbucket;

import java.util.List;

public record PrDecorationRequest(
        String repositoryUrl,
        String pullRequestId,
        String credentialsId,
        String sourceBranch,
        String targetBranch,
        List<PrFinding> findings,
        String summary
) {
}
