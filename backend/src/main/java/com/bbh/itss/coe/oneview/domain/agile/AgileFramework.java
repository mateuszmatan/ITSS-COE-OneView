package com.bbh.itss.coe.oneview.domain.agile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The agile way of working a team follows. */
@Getter
@RequiredArgsConstructor
public enum AgileFramework {

    SCRUM("Scrum"),
    KANBAN("Kanban"),
    SAFE("SAFe"),
    OTHER("Other");

    private final String label;
}
