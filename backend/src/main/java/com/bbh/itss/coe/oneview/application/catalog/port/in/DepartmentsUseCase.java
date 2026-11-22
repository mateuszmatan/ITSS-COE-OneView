package com.bbh.itss.coe.oneview.application.catalog.port.in;

import java.util.List;

public interface DepartmentsUseCase {

    List<DepartmentView> list();

    DepartmentView get(long id);

    DepartmentView create(String name);

    DepartmentView rename(long id, Long version, String name);

    void delete(long id);
}
