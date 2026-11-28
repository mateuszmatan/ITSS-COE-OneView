package com.bbh.itss.coe.oneview.domain.jenkins;

import com.bbh.itss.coe.oneview.domain.shared.InvalidRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bbh.itss.coe.oneview.domain.shared.UriEncoding.decode;
import static com.bbh.itss.coe.oneview.domain.shared.UriEncoding.pathSegment;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;

/**
 * Where a Jenkins job is: the address of its Jenkins, the names of its folders and its own name, and the number of
 * the build when the address named one. A Jenkins itself, without a job, has no names.
 */
public record JenkinsJobAddress(String jenkinsUrl, List<String> names, Integer buildNumber) {

    public static final String NOT_A_JOB = "must be the http or https address of a Jenkins job or of one of its"
            + " builds, such as https://jenkins.bbh.com/job/DevSecOps/job/payments/ or .../job/payments/42/";
    public static final String NOT_A_JENKINS = "must be the http or https address of Jenkins, such as"
            + " https://jenkins.bbh.com";

    public JenkinsJobAddress {
        names = List.copyOf(names);
    }

    /** Reads the address of a job or of one of its builds, as copied from the browser. */
    public static JenkinsJobAddress ofJob(String field, String url) {
        JenkinsJobAddress address = parse(field, url, NOT_A_JOB);
        if (address.names.isEmpty()) {
            throw InvalidRequestException.of(field, NOT_A_JOB);
        }
        return address;
    }

    /** Reads the address of a Jenkins, and the folder below it when the address or the path names one. */
    public static JenkinsJobAddress ofFolder(String field, String jenkinsUrl, String folderPath) {
        JenkinsJobAddress jenkins = parse(field, jenkinsUrl, NOT_A_JENKINS);
        List<String> names = new ArrayList<>(jenkins.names);
        if (!isBlank(folderPath)) {
            Arrays.stream(folderPath.split("/")).map(String::trim).filter(name -> !name.isEmpty()).forEach(names::add);
        }
        return new JenkinsJobAddress(jenkins.jenkinsUrl, names, null);
    }

    private static JenkinsJobAddress parse(String field, String url, String problem) {
        String trimmed = url == null ? "" : url.trim().replaceFirst("[?#].*$", "");
        if (!trimmed.matches("^https?://[^/\\s]+(/\\S*)?$")) {
            throw InvalidRequestException.of(field, problem);
        }
        int jobs = trimmed.indexOf("/job/");
        String base = stripEnd(jobs < 0 ? trimmed : trimmed.substring(0, jobs), "/");
        List<String> names = new ArrayList<>();
        Integer build = null;
        if (jobs >= 0) {
            String[] parts = trimmed.substring(jobs + 1).split("/");
            int index = 0;
            while (index + 1 < parts.length && parts[index].equals("job")) {
                names.add(decode(parts[index + 1]));
                index += 2;
            }
            if (index < parts.length && parts[index].matches("\\d{1,9}")) {
                build = Integer.valueOf(parts[index]);
            }
        }
        return new JenkinsJobAddress(base, names, build);
    }

    public JenkinsJobAddress child(String name) {
        List<String> all = new ArrayList<>(names);
        all.add(name);
        return new JenkinsJobAddress(jenkinsUrl, all, null);
    }

    /** The address of the job or folder, ending with a slash; of Jenkins itself without names. */
    public String url() {
        return jenkinsUrl + "/" + names.stream().map(name -> "job/" + pathSegment(name) + "/").collect(joining());
    }

    /** The names joined with slashes, as Jenkins shows the full name of a job. */
    public String path() {
        return String.join("/", names);
    }
}
