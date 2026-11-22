package com.bbh.itss.coe.oneview.application.actionlog.port.out;

import com.bbh.itss.coe.oneview.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogRepositoryPort {

    /** The newest first. */
    List<ActionLog> findAll();

    void save(ActionLog log);
}
