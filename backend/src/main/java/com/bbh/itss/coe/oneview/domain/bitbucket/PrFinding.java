package com.bbh.itss.coe.oneview.domain.bitbucket;

public record PrFinding(
        String tool,
        String severity,
        String file,
        Integer line,
        String title,
        String description,
        String remediation
) {
}
