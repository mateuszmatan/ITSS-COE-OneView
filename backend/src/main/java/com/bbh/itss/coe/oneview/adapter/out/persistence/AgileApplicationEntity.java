package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.agile.AgileFramework;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "AGL_APPLICATION")
@NoArgsConstructor(access = PROTECTED)
public class AgileApplicationEntity extends ProductOwnedEntity {

    private boolean enabled;
    private String teamName;
    @Enumerated(STRING)
    private AgileFramework framework;
    private String coach;

    AgileApplicationEntity(long productId) {
        super(productId);
    }

    AgileProfile toDomain() {
        return new AgileProfile(getId(), enabled, teamName, framework, coach, version(), updatedAt());
    }

    void apply(AgileProfile profile) {
        enabled = profile.enabled();
        teamName = profile.teamName();
        framework = profile.framework();
        coach = profile.coach();
    }
}
