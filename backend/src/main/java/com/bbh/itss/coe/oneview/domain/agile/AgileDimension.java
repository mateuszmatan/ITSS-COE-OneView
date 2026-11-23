package com.bbh.itss.coe.oneview.domain.agile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The dimensions an agile maturity assessment rates, each from level 1 to 5. They are the starting set of the
 * ITSS COE; the BBH model is still to be agreed (TODO O.2).
 */
@Getter
@RequiredArgsConstructor
public enum AgileDimension {

    TEAM("Team and roles", "A stable, cross-functional team with a product owner and an agile lead who know their part."),
    PLANNING("Planning and delivery", "A refined backlog, short planning cycles and a steady flow of finished work."),
    ENGINEERING("Engineering practices", "Automated build, tests and deployment, and a definition of done the team keeps."),
    PRODUCT("Product ownership", "Work ordered by business value, with stakeholders reviewing what is delivered."),
    IMPROVEMENT("Continuous improvement", "Retrospectives that lead to changes, and metrics the team uses to improve.");

    private final String label;
    private final String description;
}
