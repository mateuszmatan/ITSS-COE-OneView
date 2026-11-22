package com.bbh.itss.coe.oneview.application.catalog.port.in;

import java.time.Instant;

public record ProductSummaryView(long id, String code, String name, String description, String ownerTeam,
                                 Long departmentId, String departmentName, long serviceCount, long pipelineCount,
                                 long activePipelineCount, Instant updatedAt) {
}
