package com.bbh.itss.coe.oneview.domain.catalog;

import static java.util.Locale.ROOT;

public enum TestStage {
    SMOKE, REGRESSION, PERFORMANCE;

    public String configKey() {
        return name().toLowerCase(ROOT);
    }
}
