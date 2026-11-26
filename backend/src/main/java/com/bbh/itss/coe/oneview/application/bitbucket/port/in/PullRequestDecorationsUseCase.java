package com.bbh.itss.coe.oneview.application.bitbucket.port.in;

import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationResult;
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;

import java.util.List;

/**
 * The decorations of pull requests that the pipeline library reports: OneView records them for the pipeline that ran
 * them and shows them. The library posts the comments itself; OneView never posts to Bitbucket.
 */
public interface PullRequestDecorationsUseCase {

    /** The number of decorations {@link #recent} lists when it is not given one. */
    int RECENT = 20;

    /** The most decorations {@link #recent} lists. */
    int MOST_RECENT = 100;

    /**
     * Records the decoration for its pipeline, or without one when OneView finds none. A request without the
     * repository or the pull request records nothing and is answered as skipped.
     */
    PrDecorationResult record(PrDecorationRequest request);

    /** The newest decorations of a pipeline first, at most {@link #MOST_RECENT}; fails for an unknown pipeline. */
    List<PullRequestDecoration> recent(long pipelineId, int limit);
}
