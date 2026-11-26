package com.bbh.itss.coe.oneview.application.bitbucket.port.out;

import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;

import java.util.List;

public interface PullRequestDecorationPort {

    PullRequestDecoration save(PullRequestDecoration decoration);

    /** The newest decorations recorded for the pipeline first, each with its findings in the order they were sent. */
    List<PullRequestDecoration> findRecentByPipelineId(long pipelineId, int limit);
}
