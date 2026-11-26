package com.bbh.itss.coe.oneview.adapter.out.persistence;

import com.bbh.itss.coe.oneview.domain.bitbucket.DecorationKind;
import com.bbh.itss.coe.oneview.domain.bitbucket.PrFinding;
import com.bbh.itss.coe.oneview.domain.bitbucket.PullRequestDecoration;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

/** A decoration of a pull request with its findings, which the database deletes with it. */
@Entity
@Table(name = "DSO_PR_DECORATION")
@NoArgsConstructor(access = PROTECTED)
public class PullRequestDecorationEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Long pipelineId;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(name = "JOB_NAME")
    private String job;

    @Column(name = "BUILD_NUMBER")
    private Long build;

    private String branch;

    @Column(name = "COMMIT_HASH")
    private String commit;

    @Column(name = "SERVICE_NAME")
    private String service;

    @Enumerated(STRING)
    private DecorationKind kind;

    private String repositoryUrl;

    @Column(nullable = false)
    private String pullRequestId;

    private String pullRequestUrl;
    private String title;
    private String sourceBranch;
    private String targetBranch;

    @Column(name = "PULL_REQUEST_CREATED")
    private boolean created;

    private int lineComments;
    private int generalComments;

    @Column(name = "COMMENTS_LIMITED")
    private boolean limited;

    private Integer maxComments;
    private int findingsTotal;

    @Lob
    private String summary;

    @ElementCollection
    @CollectionTable(name = "DSO_PR_DECORATION_FINDING", joinColumns = @JoinColumn(name = "DECORATION_ID"))
    @OrderColumn(name = "POSITION")
    private List<FindingEmbeddable> findings = new ArrayList<>();

    static PullRequestDecorationEntity of(PullRequestDecoration decoration) {
        PullRequestDecorationEntity entity = new PullRequestDecorationEntity();
        entity.pipelineId = decoration.pipelineId();
        entity.recordedAt = decoration.recordedAt();
        entity.job = decoration.job();
        entity.build = decoration.build();
        entity.branch = decoration.branch();
        entity.commit = decoration.commit();
        entity.service = decoration.service();
        entity.kind = decoration.kind();
        entity.repositoryUrl = decoration.repositoryUrl();
        entity.pullRequestId = decoration.pullRequestId();
        entity.pullRequestUrl = decoration.pullRequestUrl();
        entity.title = decoration.title();
        entity.sourceBranch = decoration.sourceBranch();
        entity.targetBranch = decoration.targetBranch();
        entity.created = decoration.created();
        entity.lineComments = decoration.lineComments();
        entity.generalComments = decoration.generalComments();
        entity.limited = decoration.limited();
        entity.maxComments = decoration.maxComments();
        entity.findingsTotal = decoration.findingsTotal();
        entity.summary = decoration.summary();
        decoration.findings().forEach(finding -> entity.findings.add(FindingEmbeddable.of(finding)));
        return entity;
    }

    PullRequestDecoration toDomain() {
        return new PullRequestDecoration(id, pipelineId, recordedAt, job, build, branch, commit, service, kind,
                repositoryUrl, pullRequestId, pullRequestUrl, title, sourceBranch, targetBranch, created, lineComments,
                generalComments, limited, maxComments, findingsTotal, summary,
                findings.stream().map(FindingEmbeddable::toDomain).toList());
    }

    /** A finding of the decoration. A finding without any text reads back as an empty embeddable, which is null. */
    @Embeddable
    public record FindingEmbeddable(
            String tool,
            String severity,
            @Column(name = "FILE_PATH") String file,
            @Column(name = "LINE_NUMBER") Integer line,
            String title,
            String description,
            String remediation) {

        static FindingEmbeddable of(PrFinding finding) {
            return new FindingEmbeddable(finding.tool(), finding.severity(), finding.file(), finding.line(),
                    finding.title(), finding.description(), finding.remediation());
        }

        static PrFinding toDomain(FindingEmbeddable finding) {
            return finding == null ? new PrFinding(null, null, null, null, null, null, null)
                    : new PrFinding(finding.tool, finding.severity, finding.file, finding.line, finding.title,
                    finding.description, finding.remediation);
        }
    }
}
