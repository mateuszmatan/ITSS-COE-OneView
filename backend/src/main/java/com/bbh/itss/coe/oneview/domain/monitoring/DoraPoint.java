package com.bbh.itss.coe.oneview.domain.monitoring;

import java.time.Instant;

public record DoraPoint(Instant time, boolean deployment, boolean changeFailure, long leadTimeSeconds,
                        long durationSeconds) {
}
