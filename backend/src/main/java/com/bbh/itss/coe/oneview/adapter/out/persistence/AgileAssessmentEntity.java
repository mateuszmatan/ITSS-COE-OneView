package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.EAGER;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "AGL_ASSESSMENT")
@NoArgsConstructor(access = PROTECTED)
public class AgileAssessmentEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Long productId;
    private LocalDate assessedOn;
    private String assessor;
    private String notes;

    @ElementCollection(fetch = EAGER)
    @CollectionTable(name = "AGL_ASSESSMENT_LEVEL", joinColumns = @JoinColumn(name = "ASSESSMENT_ID"))
    @MapKeyEnumerated(STRING)
    @MapKeyColumn(name = "DIMENSION")
    @Column(name = "LEVEL_REACHED")
    private Map<AgileDimension, Integer> levels = new HashMap<>();

    static AgileAssessmentEntity of(AgileAssessment assessment) {
        AgileAssessmentEntity entity = new AgileAssessmentEntity();
        entity.productId = assessment.productId();
        entity.assessedOn = assessment.assessedOn();
        entity.assessor = assessment.assessor();
        entity.notes = assessment.notes();
        entity.levels.putAll(assessment.levels());
        return entity;
    }

    AgileAssessment toDomain() {
        return new AgileAssessment(id, productId, assessedOn, assessor, levels, notes);
    }
}
