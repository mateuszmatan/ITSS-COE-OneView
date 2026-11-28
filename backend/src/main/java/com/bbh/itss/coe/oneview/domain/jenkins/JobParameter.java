package com.bbh.itss.coe.oneview.domain.jenkins;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/** A build parameter of a Jenkins job. A password parameter never carries its default value. */
public record JobParameter(Type type, String name, String defaultValue, String description, List<String> choices) {

    public enum Type { STRING, TEXT, BOOLEAN, CHOICE, PASSWORD }

    public JobParameter {
        choices = List.copyOf(emptyIfNull(choices));
        defaultValue = type == Type.PASSWORD ? null : defaultValue;
    }
}
