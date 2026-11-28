package com.bbh.itss.coe.oneview.domain.jenkins;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The kinds of Jenkins job OneView reads; it creates the first two. */
@Getter
@RequiredArgsConstructor
public enum JobKind {
    PIPELINE("Pipeline"),
    MULTIBRANCH("Multibranch Pipeline"),
    OTHER("Other job");

    private final String label;
}
