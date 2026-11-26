package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrFinding;
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;

import java.time.Instant;
import java.util.List;

/** A recorded decoration of a pull request with its findings. It has no pipeline key: OneView does not store it. */
public record PullRequestDecorationResponse(
        Long id,
        Long pipelineId,
        Instant recordedAt,
        String job,
        Long build,
        String branch,
        String commit,
        String service,
        DecorationKind kind,
        String repositoryUrl,
        String pullRequestId,
        String pullRequestUrl,
        String title,
        String sourceBranch,
        String targetBranch,
        boolean created,
        int lineComments,
        int generalComments,
        boolean limited,
        Integer maxComments,
        int findingsTotal,
        String summary,
        List<PrFinding> findings
) {

    static PullRequestDecorationResponse of(PullRequestDecoration decoration) {
        return new PullRequestDecorationResponse(
                decoration.id(),
                decoration.pipelineId(),
                decoration.recordedAt(),
                decoration.job(),
                decoration.build(),
                decoration.branch(),
                decoration.commit(),
                decoration.service(),
                decoration.kind(),
                decoration.repositoryUrl(),
                decoration.pullRequestId(),
                decoration.pullRequestUrl(),
                decoration.title(),
                decoration.sourceBranch(),
                decoration.targetBranch(),
                decoration.created(),
                decoration.lineComments(),
                decoration.generalComments(),
                decoration.limited(),
                decoration.maxComments(),
                decoration.findingsTotal(),
                decoration.summary(),
                decoration.findings()
        );
    }
}
