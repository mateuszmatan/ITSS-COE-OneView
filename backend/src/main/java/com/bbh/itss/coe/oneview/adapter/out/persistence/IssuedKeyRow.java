package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.adapter.RecordMapper;
import com.bbh.itss.coe.oneview.domain.pipeline.IssuedKey;
import com.bbh.itss.coe.oneview.domain.pipeline.KeyStatus;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineKey;

import java.time.Instant;

record IssuedKeyRow(Long pipelineId, Long id, String value, KeyStatus status, Instant issuedAt, Instant revokedAt,
                    String revokeReason, Instant lastUsedAt) {

    IssuedKey toDomain() {
        return new IssuedKey(pipelineId, RecordMapper.map(PipelineKey.class, this));
    }
}
