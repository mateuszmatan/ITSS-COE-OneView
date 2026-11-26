package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.bitbucket.port.in.PullRequestDecorationsUseCase;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the pipeline library reports a decoration of a pull request after it has commented on the pull request or
 * opened it. The path keeps its name of the time OneView posted the comments itself; it now records the decoration and
 * posts nothing.
 */
@RestController
@RequestMapping("/api/dso/bitbucket/pull-requests")
@RequiredArgsConstructor
public class BitbucketPrController {

    private final PullRequestDecorationsUseCase decorations;

    @PostMapping("/decorate")
    public PrDecorationResult decorate(@Valid @RequestBody PrDecorationRequest request) {
        return decorations.record(request);
    }
}
