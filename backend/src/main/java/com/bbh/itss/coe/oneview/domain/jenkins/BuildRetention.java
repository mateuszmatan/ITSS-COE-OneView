package com.bbh.itss.coe.oneview.domain.jenkins;

/** How long a Jenkins job keeps its builds: for so many days, so many builds, or both; null and -1 mean no limit. */
public record BuildRetention(Integer daysToKeep, Integer buildsToKeep) {

    public BuildRetention {
        daysToKeep = limit(daysToKeep);
        buildsToKeep = limit(buildsToKeep);
    }

    public boolean limited() {
        return daysToKeep != null || buildsToKeep != null;
    }

    private static Integer limit(Integer value) {
        return value == null || value < 1 ? null : value;
    }
}
