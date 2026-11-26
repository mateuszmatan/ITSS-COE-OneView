package com.bbh.itss.coe.oneview.domain.bitbucket;

import java.util.List;

/**
 * The decoration of a pull request as the pipeline library reports it after it has commented on the pull request or
 * opened it. Every field after {@code summary} is optional, so the request of an older client stays valid.
 * {@code credentialsId} is accepted and ignored: OneView does not post to Bitbucket. {@code kind} is text, so that a
 * kind OneView does not know is recorded as no kind instead of being refused.
 */
public record PrDecorationRequest(
        String repositoryUrl,
        String pullRequestId,
        String credentialsId,
        String sourceBranch,
        String targetBranch,
        List<PrFinding> findings,
        String summary,
        String pipelineKey,
        String job,
        Long build,
        String branch,
        String commit,
        String service,
        String kind,
        String pullRequestUrl,
        String title,
        Boolean created,
        Integer lineComments,
        Integer generalComments,
        Boolean limited,
        Integer maxComments,
        Integer findingsTotal
) {
}
