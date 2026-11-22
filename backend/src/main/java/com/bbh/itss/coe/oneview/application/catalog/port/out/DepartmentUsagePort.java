package com.bbh.itss.coe.oneview.application.catalog.port.out;

import com.bbh.itss.coe.oneview.domain.catalog.CatalogUsage;

import java.util.Map;

public interface DepartmentUsagePort {

    Map<Long, CatalogUsage> perDepartment();
}
