package com.bbh.itss.coe.oneview.application.bitbucket.port.in;

import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationRequest;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrDecorationResult;

public interface DecoratePullRequestUseCase {

    PrDecorationResult decorate(PrDecorationRequest request);
}
