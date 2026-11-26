package com.bbh.itss.coe.oneview.domain.bitbucket;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.bbh.itss.coe.oneview.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.coe.oneview.domain.shared.Text.bytes;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * A decoration of a pull request that OneView recorded: the comments the pipeline library posted on the pull request
 * and the findings it posted them for. {@code pipelineId} is the pipeline that ran it, or null when OneView finds none.
 * It never holds the pipeline key of the run.
 */
public record PullRequestDecoration(
        Long id,
        Long pipelineId,
        Instant recordedAt,
        String job,
        Long build,
        String branch,
        String commit,
        String service,
        DecorationKind kind,
        String repositoryUrl,
        String pullRequestId,
        String pullRequestUrl,
        String title,
        String sourceBranch,
        String targetBranch,
        boolean created,
        int lineComments,
        int generalComments,
        boolean limited,
        Integer maxComments,
        int findingsTotal,
        String summary,
        List<PrFinding> findings
) {

    /** The findings OneView keeps of one decoration, the first ones sent; {@code findingsTotal} still counts all. */
    public static final int MAX_FINDINGS = 500;

    /* The sizes of the columns in bytes, as Oracle counts them: a longer text is cut to its column, never refused. */
    static final int JOB_BYTES = 1000;
    static final int BRANCH_BYTES = 500;
    static final int COMMIT_BYTES = 100;
    static final int SERVICE_BYTES = 200;
    static final int REPOSITORY_URL_BYTES = 1000;
    static final int PULL_REQUEST_ID_BYTES = 100;
    static final int PULL_REQUEST_URL_BYTES = 2000;
    static final int TITLE_BYTES = 2000;
    static final int TOOL_BYTES = 100;
    static final int SEVERITY_BYTES = 50;
    static final int FILE_BYTES = 1000;
    static final int TEXT_BYTES = 4000;

    private static final Pattern WEB_ADDRESS = Pattern.compile("(?i)https?://[^\\s/?#]+\\S*");

    public PullRequestDecoration {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * The decoration a request reports, as OneView records it: texts trimmed and cut to the sizes of their columns,
     * the first {@link #MAX_FINDINGS} findings, a pull request address only when it is an http or https address that
     * fits its column, no user name or password in an address, and counts that are never negative.
     */
    public static PullRequestDecoration recorded(PrDecorationRequest request, Long pipelineId, Instant recordedAt) {
        List<PrFinding> sent = emptyIfNull(request.findings()).stream().filter(Objects::nonNull).toList();
        return new PullRequestDecoration(
                null,
                pipelineId,
                recordedAt,
                cut(request.job(), JOB_BYTES),
                request.build(),
                cut(request.branch(), BRANCH_BYTES),
                cut(request.commit(), COMMIT_BYTES),
                cut(request.service(), SERVICE_BYTES),
                DecorationKind.of(request.kind()),
                cut(withoutUserInfo(request.repositoryUrl()), REPOSITORY_URL_BYTES),
                cut(request.pullRequestId(), PULL_REQUEST_ID_BYTES),
                webAddress(request.pullRequestUrl()),
                cut(request.title(), TITLE_BYTES),
                cut(request.sourceBranch(), BRANCH_BYTES),
                cut(request.targetBranch(), BRANCH_BYTES),
                Boolean.TRUE.equals(request.created()),
                count(request.lineComments()),
                count(request.generalComments()),
                Boolean.TRUE.equals(request.limited()),
                request.maxComments() == null || request.maxComments() < 0 ? null : request.maxComments(),
                Math.max(count(request.findingsTotal()), sent.size()),
                trimToNull(request.summary()),
                sent.stream().limit(MAX_FINDINGS).map(PullRequestDecoration::recorded).toList()
        );
    }

    private static PrFinding recorded(PrFinding finding) {
        return new PrFinding(
                cut(finding.tool(), TOOL_BYTES),
                cut(finding.severity(), SEVERITY_BYTES),
                cut(finding.file(), FILE_BYTES),
                finding.line() != null && finding.line() > 0 ? finding.line() : null,
                cut(finding.title(), TITLE_BYTES),
                cut(finding.description(), TEXT_BYTES),
                cut(finding.remediation(), TEXT_BYTES)
        );
    }

    /** The address without a user name or password before its host, as the pipeline library sends it. */
    static String withoutUserInfo(String address) {
        String trimmed = trimToNull(address);
        if (trimmed == null) {
            return null;
        }
        int scheme = trimmed.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int end = start;
        while (end < trimmed.length() && "/?#".indexOf(trimmed.charAt(end)) < 0) {
            end++;
        }
        int at = trimmed.lastIndexOf('@', end - 1);
        return at < start ? trimmed : trimmed.substring(0, start) + trimmed.substring(at + 1);
    }

    /** The address of the pull request, or null when it is no http or https address or does not fit its column. */
    static String webAddress(String address) {
        String plain = withoutUserInfo(address);
        boolean web = plain != null && WEB_ADDRESS.matcher(plain).matches() && bytes(plain) <= PULL_REQUEST_URL_BYTES;
        return web ? plain : null;
    }

    private static String cut(String text, int maxBytes) {
        return abbreviateBytes(trimToNull(text), maxBytes);
    }

    private static int count(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
