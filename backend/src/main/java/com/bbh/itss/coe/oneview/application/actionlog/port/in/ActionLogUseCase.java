package com.bbh.itss.coe.oneview.application.actionlog.port.in;

import com.bbh.itss.coe.oneview.domain.actionlog.ActionLog;

import java.util.List;

public interface ActionLogUseCase {

    /** The newest first. */
    List<ActionLog> list();

    void record(ActionRecord action);
}
