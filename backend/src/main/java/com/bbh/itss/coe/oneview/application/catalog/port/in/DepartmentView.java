package com.bbh.itss.coe.oneview.application.catalog.port.in;

import com.bbh.itss.coe.oneview.domain.catalog.CatalogUsage;

public record DepartmentView(long id, String name, long version, CatalogUsage usage) {
}
