package com.bbh.itss.coe.oneview.application.pipeline.port.out;

import com.bbh.itss.coe.oneview.domain.monitoring.MetricsTag;
import com.bbh.itss.coe.oneview.domain.pipeline.IssuedKey;
import com.bbh.itss.coe.oneview.domain.pipeline.Pipeline;
import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PipelineRepositoryPort {

    Optional<Pipeline> load(long id);

    Optional<Pipeline> loadForUpdate(long id);

    Optional<IssuedKey> findKey(String keyValue);

    List<Pipeline> findByProductId(long productId);

    List<Pipeline> findByDepartmentId(long departmentId);

    List<Pipeline> findAll();

    Set<MetricsTag> sharedMetricsTags();

    boolean existsForService(long serviceId, PipelineType type);

    Pipeline save(Pipeline pipeline);

    void delete(long id);

    boolean recordKeyUse(long keyId, Instant usedAt);
}
