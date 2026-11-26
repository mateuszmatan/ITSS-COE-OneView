package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.bitbucket.port.in.PullRequestDecorationsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The decorations of pull requests that the pipeline library reported for a pipeline, the newest first. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PullRequestDecorationController {

    private final PullRequestDecorationsUseCase decorations;

    @GetMapping("/pipelines/{id}/pull-request-decorations")
    public List<PullRequestDecorationResponse> recent(
            @PathVariable long id,
            @RequestParam(defaultValue = "" + PullRequestDecorationsUseCase.RECENT) int limit
    ) {
        return decorations.recent(id, limit).stream().map(PullRequestDecorationResponse::of).toList();
    }
}
