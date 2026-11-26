import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { DecorationFinding, DecorationKind, PullRequestDecoration } from '../core/models';
import { counted, listed } from '@common/shared/formatting';
import { uniqueId } from '@common/ui/form-field';

const KINDS: Record<DecorationKind, string> = {
  BRANCH: 'Pull request of the analysed branch',
  STATIC: 'Static security pull request',
  DAST: 'DAST pull request',
};

/** The pull request a decoration was posted on; a kind the portal does not know is just a pull request. */
export function decorationKindLabel(kind: DecorationKind | null): string {
  return (kind && KINDS[kind]) || 'Pull request';
}

/** The pull request by its title, or by its number when the library sent no title. */
export function pullRequestLabel(
  decoration: Pick<PullRequestDecoration, 'title' | 'pullRequestId'>,
): string {
  return decoration.title?.trim() || `#${decoration.pullRequestId}`;
}

/** The branch the pull request merges, and the branch it merges into. */
export function mergeBranches(
  decoration: Pick<PullRequestDecoration, 'sourceBranch' | 'targetBranch'>,
): string {
  const { sourceBranch: from, targetBranch: into } = decoration;
  if (from && into) {
    return `${from} → ${into}`;
  }
  return from ? `from ${from}` : into ? `into ${into}` : '';
}

/** The comments the library posted under a line of a changed file and on the pull request itself. */
export function commentsAdded(
  decoration: Pick<
    PullRequestDecoration,
    'lineComments' | 'generalComments' | 'limited' | 'maxComments'
  >,
): string {
  const added = `${decoration.lineComments} under a line, ${decoration.generalComments} on the pull request`;
  if (!decoration.limited) {
    return added;
  }
  return decoration.maxComments === null
    ? `${added}, limited`
    : `${added}, limited to ${decoration.maxComments}`;
}

/** How many findings the decoration was posted for, per tool; the portal keeps the first ones of a long list. */
export function findingsPerTool(
  decoration: Pick<PullRequestDecoration, 'findings' | 'findingsTotal'>,
): string {
  const kept = decoration.findings.length;
  const total = Math.max(decoration.findingsTotal, kept);
  if (total === 0) {
    return 'None';
  }
  if (kept === 0) {
    return counted(total, 'finding');
  }
  const perTool = new Map<string, number>();
  for (const finding of decoration.findings) {
    const tool = finding.tool ?? 'other';
    perTool.set(tool, (perTool.get(tool) ?? 0) + 1);
  }
  const tools = listed([...perTool].map(([tool, count]) => `${count} ${tool}`));
  return kept < total
    ? `${counted(total, 'finding')}, the first ${kept}: ${tools}`
    : `${counted(total, 'finding')}: ${tools}`;
}

/** The file and line of a finding, as the comment under that line names them. */
export function findingPlace(finding: Pick<DecorationFinding, 'file' | 'line'>): string {
  if (!finding.file) {
    return '–';
  }
  return finding.line ? `${finding.file}:${finding.line}` : finding.file;
}

/** The build of the run, or a dash when Jenkins gave it no number. */
export function buildLabel(build: number | null): string {
  return build && build > 0 ? `#${build}` : '–';
}

@Component({
  selector: 'ov-pull-request-decorations',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-scroll">
      <table class="decoration-table" aria-label="Pull request decorations, newest first">
        <thead>
          <tr>
            <th scope="col">When</th>
            <th scope="col">Build</th>
            <th scope="col">Kind</th>
            <th scope="col">Pull request</th>
            <th scope="col">Service</th>
            <th scope="col">Comments added</th>
            <th scope="col">Findings</th>
          </tr>
        </thead>
        <tbody>
          @for (decoration of decorations(); track decoration.id) {
            @let open = expanded().has(decoration.id);
            <tr class="decoration">
              <td class="when">{{ decoration.recordedAt | date: dateTime }}</td>
              <td class="build">{{ build(decoration.build) }}</td>
              <td class="kind">{{ kind(decoration.kind) }}</td>
              <td class="pull-request">
                @if (decoration.pullRequestUrl; as url) {
                  <a [href]="url" target="_blank" rel="noopener">{{ label(decoration) }}</a>
                } @else {
                  <span>{{ label(decoration) }}</span>
                }
                @if (branches(decoration); as merge) {
                  <span class="explain mono">{{ merge }}</span>
                }
                @if (decoration.created) {
                  <span class="explain">Opened by this decoration</span>
                }
              </td>
              <td class="service mono">{{ decoration.service ?? '–' }}</td>
              <td class="comments">{{ comments(decoration) }}</td>
              <td class="findings">
                {{ findings(decoration) }}
                @if (decoration.findings.length) {
                  <button
                    type="button"
                    class="text-link"
                    [attr.aria-expanded]="open"
                    [attr.aria-controls]="open ? findingsId(decoration) : null"
                    (click)="toggle(decoration.id)"
                  >
                    {{ open ? 'Hide the findings' : 'Show the findings' }}
                  </button>
                }
              </td>
            </tr>
            @if (open) {
              <tr class="finding-list" [id]="findingsId(decoration)">
                <td colspan="7">
                  <table
                    class="finding-table"
                    [attr.aria-label]="'Findings of ' + label(decoration)"
                  >
                    <thead>
                      <tr>
                        <th scope="col">Tool</th>
                        <th scope="col">Severity</th>
                        <th scope="col">File and line</th>
                        <th scope="col">Title</th>
                        <th scope="col">Recommended fix</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (finding of decoration.findings; track $index) {
                        <tr>
                          <td>{{ finding.tool ?? '–' }}</td>
                          <td>{{ finding.severity ?? '–' }}</td>
                          <td class="mono place">{{ place(finding) }}</td>
                          <td>{{ finding.title ?? '–' }}</td>
                          <td class="fix">{{ finding.remediation ?? '–' }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </td>
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .table-scroll {
      overflow-x: auto;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
    }

    th,
    td {
      padding: 5px 8px;
      border-bottom: 1px solid var(--ov-border);
      text-align: left;
      vertical-align: top;
    }

    th {
      color: var(--ov-muted);
      font-weight: 600;
      white-space: nowrap;
    }

    .when,
    .build {
      white-space: nowrap;
    }

    .pull-request,
    .place {
      overflow-wrap: anywhere;
    }

    .explain {
      display: block;
      color: var(--ov-muted);
      font-size: 11.5px;
    }

    .findings .text-link {
      display: block;
      margin-top: 2px;
    }

    .finding-list > td {
      padding: 4px 8px 10px 24px;
      background: var(--ov-surface);
    }

    .finding-table th,
    .finding-table td {
      background: var(--ov-card);
    }

    .fix {
      white-space: pre-line;
    }
  `,
})
export class PullRequestDecorations {
  readonly decorations = input.required<readonly PullRequestDecoration[]>();

  protected readonly dateTime = 'd MMM y, HH:mm';
  protected readonly kind = decorationKindLabel;
  protected readonly label = pullRequestLabel;
  protected readonly branches = mergeBranches;
  protected readonly comments = commentsAdded;
  protected readonly findings = findingsPerTool;
  protected readonly place = findingPlace;
  protected readonly build = buildLabel;
  protected readonly expanded = signal<ReadonlySet<number>>(new Set());

  private readonly idPrefix = uniqueId('ov-decoration-findings');

  protected findingsId(decoration: PullRequestDecoration): string {
    return `${this.idPrefix}-${decoration.id}`;
  }

  protected toggle(id: number): void {
    this.expanded.update((open) => {
      const next = new Set(open);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }
}
