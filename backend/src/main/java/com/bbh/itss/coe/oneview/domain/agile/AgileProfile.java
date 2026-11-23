package com.bbh.itss.coe.oneview.domain.agile;

import com.bbh.itss.coe.oneview.domain.shared.ValidationProblems;

import java.time.Instant;
import java.util.Objects;

import static com.bbh.itss.coe.oneview.domain.shared.Failures.staleVersion;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * How an application (a product of the catalog) takes part in Agile Assessment &amp; Adoption: whether it does, the
 * team that builds it, the way of working of that team and its agile coach. The version is {@code null} while the
 * profile has never been saved.
 */
public record AgileProfile(long productId, boolean enabled, String teamName, AgileFramework framework, String coach,
                           Long version, Instant updatedAt) {

    public static final int MAX_NAME = 200;

    public AgileProfile {
        teamName = trimToNull(teamName);
        coach = trimToNull(coach);
    }

    /** The profile of an application that has never been set up for Agile Assessment &amp; Adoption. */
    public static AgileProfile none(long productId) {
        return new AgileProfile(productId, false, null, null, null, null, null);
    }

    public AgileProfile change(Long expectedVersion, AgileProfile changed) {
        if (!Objects.equals(expectedVersion, version)) {
            throw staleVersion();
        }
        AgileProfile next = new AgileProfile(productId, changed.enabled(), changed.teamName(), changed.framework(),
                changed.coach(), version, updatedAt);
        new ValidationProblems()
                .fits("teamName", next.teamName(), MAX_NAME)
                .fits("coach", next.coach(), MAX_NAME)
                .throwIfAny();
        return next;
    }
}
