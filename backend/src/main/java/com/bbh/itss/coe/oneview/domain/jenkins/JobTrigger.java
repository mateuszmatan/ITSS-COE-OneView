package com.bbh.itss.coe.oneview.domain.jenkins;

/** What starts a Jenkins job by itself, with its schedule or interval where it has one. */
public record JobTrigger(Kind kind, String name, String schedule) {

    public enum Kind { SCM_POLLING, TIMER, BRANCH_SCAN, OTHER }
}
