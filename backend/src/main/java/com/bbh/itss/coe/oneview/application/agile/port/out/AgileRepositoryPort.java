package com.bbh.itss.coe.oneview.application.agile.port.out;

import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;

import java.util.List;
import java.util.Optional;

public interface AgileRepositoryPort {

    List<AgileProfile> findProfiles();

    Optional<AgileProfile> loadProfile(long productId);

    AgileProfile saveProfile(AgileProfile profile);

    /** The assessments of every application, the latest first. */
    List<AgileAssessment> findAssessments();

    /** The assessments of one application, the latest first. */
    List<AgileAssessment> findAssessments(long productId);

    Optional<AgileAssessment> loadAssessment(long id);

    AgileAssessment saveAssessment(AgileAssessment assessment);

    void deleteAssessment(long id);
}
