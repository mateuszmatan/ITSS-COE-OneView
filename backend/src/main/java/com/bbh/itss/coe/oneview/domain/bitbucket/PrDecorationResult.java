package com.bbh.itss.coe.oneview.domain.bitbucket;

public record PrDecorationResult(
        String pullRequestId,
        int commentsAdded,
        int lineCommentsAdded,
        String status,
        String message
) {
}
