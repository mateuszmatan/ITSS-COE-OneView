package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.pipeline.PipelineType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * What a Jenkinsfile says about its pipeline, as far as its text shows it: the shared libraries it loads, the entry
 * point of the DevSecOps library it calls, whether it carries a pipeline key, its Jenkins agents, the services it
 * names, its own triggers and the parameters it declares. The key itself is never kept.
 */
public record JenkinsfileFacts(List<String> libraries, String entryPoint, PipelineType pipelineType,
                               boolean hasPipelineKey, List<String> agentLabels, List<String> serviceNames,
                               List<JobTrigger> triggers, List<String> parameters) {

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|(?m)//[^\\n]*$", DOTALL);
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern LIBRARY_ANNOTATION = Pattern.compile("@Library\\(\\s*(\\[[^\\]]*]|['\"][^'\"]+['\"])");
    private static final Pattern LIBRARY_STEP =
            Pattern.compile("(?m)^\\s*library\\s*\\(?\\s*(?:identifier\\s*:\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern KEY = Pattern.compile("\\bpipelineKeys?\\s*:");
    private static final Pattern AGENT_NAMES = Pattern.compile("\\bagentNames\\s*:\\s*\\[([^\\]]*)]");
    private static final Pattern AGENT_LABEL =
            Pattern.compile("\\b(?:label|node)\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PROJECT_NAMES = Pattern.compile("\\bprojectNames\\s*:\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern POLL = Pattern.compile("\\bpollSCM\\s*\\(?\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern CRON = Pattern.compile("\\bcron\\s*\\(?\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern PARAMETER = Pattern.compile(
            "\\b(?:string|booleanParam|choice|text|password)\\s*\\(\\s*name\\s*:\\s*['\"]([^'\"]+)['\"]");

    public JenkinsfileFacts {
        libraries = List.copyOf(libraries);
        agentLabels = List.copyOf(agentLabels);
        serviceNames = List.copyOf(serviceNames);
        triggers = List.copyOf(triggers);
        parameters = List.copyOf(parameters);
    }

    /** Reads a Jenkinsfile; null for a missing or empty one. */
    public static JenkinsfileFacts read(String jenkinsfile) {
        if (isBlank(jenkinsfile)) {
            return null;
        }
        String text = COMMENT.matcher(jenkinsfile).replaceAll("");
        Set<String> libraries = new LinkedHashSet<>();
        Matcher annotation = LIBRARY_ANNOTATION.matcher(text);
        while (annotation.find()) {
            libraries.addAll(all(QUOTED, annotation.group(1)));
        }
        libraries.addAll(all(LIBRARY_STEP, text));
        PipelineType type = Arrays.stream(PipelineType.values())
                .filter(candidate -> Pattern.compile("\\b" + candidate.entryPoint() + "\\s*[({]").matcher(text).find())
                .findFirst().orElse(null);
        List<String> agents = all(QUOTED, first(AGENT_NAMES, text));
        List<JobTrigger> triggers = new ArrayList<>();
        all(POLL, text).forEach(spec -> triggers.add(new JobTrigger(JobTrigger.Kind.SCM_POLLING, "pollSCM", spec)));
        all(CRON, text).forEach(spec -> triggers.add(new JobTrigger(JobTrigger.Kind.TIMER, "cron", spec)));
        return new JenkinsfileFacts(List.copyOf(libraries), type == null ? null : type.entryPoint(), type,
                KEY.matcher(text).find(), agents.isEmpty() ? all(AGENT_LABEL, text) : agents,
                Arrays.stream(first(PROJECT_NAMES, text).split(",")).map(String::trim)
                        .filter(name -> !name.isEmpty()).toList(),
                triggers, all(PARAMETER, text));
    }

    /** The name of a library without the version after its @. */
    public static String libraryName(String library) {
        int version = library.indexOf('@');
        return version < 0 ? library : library.substring(0, version);
    }

    public boolean loads(String library) {
        return libraries.stream().anyMatch(loaded -> libraryName(loaded).equals(library));
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<String> all(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1).trim());
        }
        return List.copyOf(found);
    }
}
