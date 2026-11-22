package com.bbh.itss.coe.oneview.domain.catalog;

import static java.util.Locale.ROOT;

public enum DatabaseTarget {
    TEST, RD, QC;

    public String configKey() {
        return name().toLowerCase(ROOT);
    }
}
