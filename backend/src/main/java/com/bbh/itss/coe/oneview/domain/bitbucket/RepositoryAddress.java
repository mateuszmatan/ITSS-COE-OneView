package com.bbh.itss.coe.oneview.domain.bitbucket;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * A Bitbucket repository as its address names it: the page of the repository or its http clone address on
 * Bitbucket Server, or a repository on Bitbucket Cloud. The base address is that of the Bitbucket Server, null for
 * Bitbucket Cloud.
 */
public record RepositoryAddress(String baseUrl, String project, String slug) {

    private static final Pattern SERVER_PAGE =
            Pattern.compile("^(https?://.+?)/(projects|users)/([^/]+)/repos/([^/?#]+).*$");
    private static final Pattern SERVER_CLONE =
            Pattern.compile("^(https?://)(?:[^@/]+@)?(.+?)/scm/([^/]+)/([^/?#]+?)(?:\\.git)?/?$");
    private static final Pattern CLOUD = Pattern.compile(
            "^https?://(?:[^@/]+@)?(?:api\\.)?bitbucket\\.org/(?:2\\.0/repositories/)?([^/]+)/([^/?#]+?)(?:\\.git)?(?:/.*)?$");

    public static Optional<RepositoryAddress> parse(String url) {
        String trimmed = trimToEmpty(url);
        Matcher cloud = CLOUD.matcher(trimmed);
        if (cloud.matches()) {
            return Optional.of(new RepositoryAddress(null, cloud.group(1), cloud.group(2)));
        }
        Matcher page = SERVER_PAGE.matcher(trimmed);
        if (page.matches()) {
            String owner = page.group(2).equals("users") ? "~" + page.group(3) : page.group(3);
            return Optional.of(new RepositoryAddress(page.group(1), owner, page.group(4)));
        }
        Matcher clone = SERVER_CLONE.matcher(trimmed);
        if (clone.matches()) {
            return Optional.of(new RepositoryAddress(clone.group(1) + clone.group(2), clone.group(3),
                    clone.group(4)));
        }
        return Optional.empty();
    }

    public boolean cloud() {
        return baseUrl == null;
    }

    /** The http address Git clones the repository from. */
    public String cloneUrl() {
        return cloud() ? "https://bitbucket.org/" + project + "/" + slug + ".git"
                : baseUrl + "/scm/" + project.toLowerCase(Locale.ROOT) + "/" + slug + ".git";
    }

    /** The address Git clones from for any repository address: as given when it is not a Bitbucket address. */
    public static String cloneUrlOf(String url) {
        return parse(url).map(RepositoryAddress::cloneUrl).orElse(trimToEmpty(url));
    }
}
