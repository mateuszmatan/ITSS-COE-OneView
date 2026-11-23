package com.bbh.itss.coe.oneview.application.qa.port.out;

import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;

import java.util.List;
import java.util.Optional;

public interface QualityRepositoryPort {

    Optional<QualityDefaults> loadDefaults();

    QualityDefaults saveDefaults(QualityDefaults defaults);

    List<QualityProfile> findProfiles();

    Optional<QualityProfile> loadProfile(long productId);

    QualityProfile saveProfile(QualityProfile profile);
}
