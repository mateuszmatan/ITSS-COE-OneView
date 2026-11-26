package com.bbh.itss.coe.oneview.application.bitbucket;

import com.bbh.itss.coe.oneview.application.ReadOnly;
import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.bitbucket.port.in.PullRequestDecorationsUseCase;
import com.bbh.itss.coe.oneview.application.bitbucket.port.out.PullRequestDecorationPort;
import com.bbh.itss.coe.oneview.application.pipeline.port.out.PipelineRepositoryPort;
import com.bbh.itss.coe.oneview.application.report.port.out.PipelineLookupPort;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationResult;
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;
import com.bbh.itss.coe.oneview.domain.shared.Timestamps;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.List;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Records the decorations of pull requests that the pipeline library reports after it has commented on a pull request
 * or opened one, for the pipeline it finds as the pipeline report does. It does not call Bitbucket.
 */
@UseCase
@RequiredArgsConstructor
public class PullRequestDecorationService implements PullRequestDecorationsUseCase {

    static final String RECORDED = "RECORDED";
    static final String SKIPPED = "SKIPPED";

    private final PipelineLookupPort pipelineLookup;
    private final PipelineRepositoryPort pipelines;
    private final PullRequestDecorationPort decorations;
    private final Clock clock;

    @Override
    public PrDecorationResult record(PrDecorationRequest request) {
        if (request == null || isBlank(trimToNull(request.repositoryUrl()))
                || isBlank(trimToNull(request.pullRequestId()))) {
            return new PrDecorationResult(null, 0, 0, SKIPPED, "Missing repositoryUrl or pullRequestId");
        }
        Long pipelineId = pipelineLookup.findPipelineOfRun(request.pipelineKey(), request.job()).orElse(null);
        PullRequestDecoration recorded = decorations.save(
                PullRequestDecoration.recorded(request, pipelineId, Timestamps.now(clock)));
        String decorated = "Recorded the decoration of pull request " + recorded.pullRequestId();
        return new PrDecorationResult(request.pullRequestId(), recorded.generalComments(), recorded.lineComments(),
                RECORDED, pipelineId == null
                ? decorated + " without a pipeline: OneView finds no pipeline by the pipeline key or the job"
                : decorated + " for pipeline " + pipelineId);
    }

    @Override
    @ReadOnly
    public List<PullRequestDecoration> recent(long pipelineId, int limit) {
        pipelines.load(pipelineId).orElseThrow(() -> notFound("Pipeline", pipelineId));
        return decorations.findRecentByPipelineId(pipelineId, Math.clamp(limit, 1, MOST_RECENT));
    }
}
