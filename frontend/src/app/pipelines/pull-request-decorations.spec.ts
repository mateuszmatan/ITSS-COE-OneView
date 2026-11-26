import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DecorationKind, PullRequestDecoration } from '../core/models';
import { buttonOf, text } from '@common/testing/dom';
import { decorationFinding, pullRequestDecoration } from '../testing/fixtures';
import {
  PullRequestDecorations,
  buildLabel,
  commentsAdded,
  decorationKindLabel,
  findingPlace,
  findingsPerTool,
  mergeBranches,
  pullRequestLabel,
} from './pull-request-decorations';

describe('pull request decoration texts', () => {
  it('names the pull request a decoration was posted on, and one of a kind the portal does not know as a pull request', () => {
    expect(decorationKindLabel('BRANCH')).toBe('Pull request of the analysed branch');
    expect(decorationKindLabel('STATIC')).toBe('Static security pull request');
    expect(decorationKindLabel('DAST')).toBe('DAST pull request');
    expect(decorationKindLabel(null)).toBe('Pull request');
    expect(decorationKindLabel('GOLDEN_FIX' as string as DecorationKind)).toBe('Pull request');
  });

  it('names a pull request by its title, or by its number when the library sent no title', () => {
    expect(pullRequestLabel({ title: ' Add the login page ', pullRequestId: '12' })).toBe(
      'Add the login page',
    );
    expect(pullRequestLabel({ title: null, pullRequestId: '12' })).toBe('#12');
    expect(pullRequestLabel({ title: '  ', pullRequestId: '12' })).toBe('#12');
  });

  it('says which branch the pull request merges into which, as far as it is known', () => {
    expect(mergeBranches({ sourceBranch: 'feature/login', targetBranch: 'main' })).toBe(
      'feature/login → main',
    );
    expect(mergeBranches({ sourceBranch: 'feature/login', targetBranch: null })).toBe(
      'from feature/login',
    );
    expect(mergeBranches({ sourceBranch: null, targetBranch: 'main' })).toBe('into main');
    expect(mergeBranches({ sourceBranch: null, targetBranch: null })).toBe('');
  });

  it('counts the comments under a line and on the pull request, and says when they were limited', () => {
    const comments = { lineComments: 3, generalComments: 2, limited: false, maxComments: 25 };

    expect(commentsAdded(comments)).toBe('3 under a line, 2 on the pull request');
    expect(commentsAdded({ ...comments, limited: true })).toBe(
      '3 under a line, 2 on the pull request, limited to 25',
    );
    expect(commentsAdded({ ...comments, limited: true, maxComments: null })).toBe(
      '3 under a line, 2 on the pull request, limited',
    );
    expect(commentsAdded({ ...comments, lineComments: 0, generalComments: 0 })).toBe(
      '0 under a line, 0 on the pull request',
    );
  });

  it('counts the findings per tool and says when the portal kept only the first ones', () => {
    const decoration = pullRequestDecoration();
    const one = [decorationFinding()];

    expect(findingsPerTool({ findings: [], findingsTotal: 0 })).toBe('None');
    expect(findingsPerTool({ findings: [], findingsTotal: 340 })).toBe('340 findings');
    expect(findingsPerTool(decoration)).toBe('3 findings: 2 AppScan SAST and 1 Nexus IQ');
    expect(findingsPerTool({ findings: one, findingsTotal: 1 })).toBe('1 finding: 1 AppScan SAST');
    expect(findingsPerTool({ findings: decoration.findings, findingsTotal: 340 })).toBe(
      '340 findings, the first 3: 2 AppScan SAST and 1 Nexus IQ',
    );
    expect(findingsPerTool({ findings: decoration.findings, findingsTotal: 0 })).toBe(
      '3 findings: 2 AppScan SAST and 1 Nexus IQ',
    );
    expect(
      findingsPerTool({ findings: [decorationFinding({ tool: null }), ...one], findingsTotal: 2 }),
    ).toBe('2 findings: 1 other and 1 AppScan SAST');
  });

  it('names the file and line of a finding as far as they are known', () => {
    expect(findingPlace({ file: 'src/Login.java', line: 42 })).toBe('src/Login.java:42');
    expect(findingPlace({ file: 'build.gradle', line: null })).toBe('build.gradle');
    expect(findingPlace({ file: null, line: 42 })).toBe('–');
  });

  it('names the build of the run, or a dash when Jenkins gave it no number', () => {
    expect(buildLabel(42)).toBe('#42');
    expect(buildLabel(0)).toBe('–');
    expect(buildLabel(null)).toBe('–');
  });
});

describe('PullRequestDecorations', () => {
  let fixture: ComponentFixture<PullRequestDecorations>;

  beforeEach(() => {
    fixture = TestBed.createComponent(PullRequestDecorations);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () =>
    [...page().querySelectorAll('tr.decoration')].map((row) =>
      [...row.querySelectorAll('td')].map(text),
    );
  const pullRequests = () =>
    [...page().querySelectorAll('tr.decoration td.pull-request')].map((cell) =>
      [...cell.children].map(text),
    );
  const findingRows = () =>
    [...page().querySelectorAll('.finding-list')].map((list) =>
      [...list.querySelectorAll('tbody tr')].map((row) =>
        [...row.querySelectorAll('td')].map(text),
      ),
    );

  async function show(decorations: PullRequestDecoration[]) {
    fixture.componentRef.setInput('decorations', decorations);
    await fixture.whenStable();
  }

  it('shows one row per decoration in the order given, with the columns of the section', async () => {
    await show([
      pullRequestDecoration(),
      pullRequestDecoration({ id: 30, build: 41, service: null }),
    ]);

    expect([...page().querySelectorAll('.decoration-table > thead th')].map(text)).toEqual([
      'When',
      'Build',
      'Kind',
      'Pull request',
      'Service',
      'Comments added',
      'Findings',
    ]);
    expect(page().querySelector('.decoration-table')!.getAttribute('aria-label')).toBe(
      'Pull request decorations, newest first',
    );
    expect(rows().map((row) => row[0])).toEqual([
      expect.stringMatching(/^2[56] Nov 2026, \d\d:\d\d$/),
      expect.stringMatching(/^2[56] Nov 2026, \d\d:\d\d$/),
    ]);
    expect(rows().map((row) => [row[1], row[4]])).toEqual([
      ['#42', 'gui'],
      ['#41', '–'],
    ]);
  });

  it('links the pull request only when the portal has its address, and opens it in a new tab', async () => {
    await show([
      pullRequestDecoration(),
      pullRequestDecoration({ id: 30, pullRequestId: '9', title: null, pullRequestUrl: null }),
    ]);

    const links = [...page().querySelectorAll<HTMLAnchorElement>('.pull-request a')];
    expect(
      links.map((link) => [
        text(link),
        link.getAttribute('href'),
        link.getAttribute('target'),
        link.getAttribute('rel'),
      ]),
    ).toEqual([
      [
        'Add the login page',
        'https://bitbucket.bbh.com/projects/CERT/repos/gui/pull-requests/12',
        '_blank',
        'noopener',
      ],
    ]);
    expect(pullRequests()).toEqual([
      ['Add the login page', 'feature/login → main'],
      ['#9', 'feature/login → main'],
    ]);
    const label = page().querySelectorAll('tr.decoration td.pull-request')[1].firstElementChild!;
    expect([label.tagName, label.className]).toEqual(['SPAN', '']);
  });

  it('notes a pull request the decoration opened, also a DAST pull request that got no comment', async () => {
    await show([
      pullRequestDecoration(),
      pullRequestDecoration({
        id: 30,
        kind: 'DAST',
        pullRequestId: '13',
        pullRequestUrl: null,
        title: '[Security] DAST findings - gui',
        sourceBranch: 'security-dast-gui',
        created: true,
        lineComments: 0,
        generalComments: 0,
        findingsTotal: 1,
        findings: [
          decorationFinding({
            tool: 'AppScan DAST',
            file: null,
            line: null,
            title: 'Cross-Site Scripting',
            remediation: 'Encode the output.',
          }),
        ],
      }),
    ]);

    expect(pullRequests()).toEqual([
      ['Add the login page', 'feature/login → main'],
      ['[Security] DAST findings - gui', 'security-dast-gui → main', 'Opened by this decoration'],
    ]);
    const dast = rows()[1];
    expect([dast[2], ...dast.slice(4)]).toEqual([
      'DAST pull request',
      'gui',
      '0 under a line, 0 on the pull request',
      '1 finding: 1 AppScan DAST Show the findings',
    ]);
  });

  it('shows every text the library sent as text, never as HTML', async () => {
    await show([
      pullRequestDecoration({
        title: '<img src=x onerror=alert(1)>',
        service: '<b>gui</b>',
        sourceBranch: '<script>alert(1)</script>',
        targetBranch: 'main',
        findings: [
          decorationFinding({
            tool: '<i>AppScan</i>',
            severity: '<u>HIGH</u>',
            file: '<em>Login.java</em>',
            title: '<a href="https://example.com">SQL Injection</a>',
            remediation: '<iframe src="https://example.com"></iframe>',
          }),
        ],
        findingsTotal: 1,
      }),
    ]);
    buttonOf(page(), 'Show the findings').click();
    await fixture.whenStable();

    expect(pullRequests()).toEqual([
      ['<img src=x onerror=alert(1)>', '<script>alert(1)</script> → main'],
    ]);
    expect(rows()[0].slice(4)).toEqual([
      '<b>gui</b>',
      '3 under a line, 2 on the pull request',
      '1 finding: 1 <i>AppScan</i> Hide the findings',
    ]);
    expect(findingRows()).toEqual([
      [
        [
          '<i>AppScan</i>',
          '<u>HIGH</u>',
          '<em>Login.java</em>:42',
          '<a href="https://example.com">SQL Injection</a>',
          '<iframe src="https://example.com"></iframe>',
        ],
      ],
    ]);
    expect(page().querySelectorAll('img, b, script, i, u, em, iframe').length).toBe(0);
    expect(page().querySelectorAll('a').length).toBe(1);
  });

  it('keeps the line breaks of a recommended fix', async () => {
    const remediation = 'Use a prepared statement:\n\n    query(sql, id)';
    await show([pullRequestDecoration({ findings: [decorationFinding({ remediation })] })]);
    buttonOf(page(), 'Show the findings').click();
    await fixture.whenStable();

    const fix = page().querySelector('.finding-list .fix')!;
    expect(fix.textContent).toBe(remediation);
    expect(getComputedStyle(fix).getPropertyValue('white-space')).toBe('pre-line');
  });

  it('opens and closes the findings of each decoration on its own', async () => {
    await show([
      pullRequestDecoration(),
      pullRequestDecoration({ id: 30, findings: [decorationFinding()], findingsTotal: 1 }),
      pullRequestDecoration({ id: 29, findings: [], findingsTotal: 0 }),
    ]);

    const buttons = () => [...page().querySelectorAll<HTMLButtonElement>('.findings button')];
    expect(buttons().map((button) => [text(button), button.getAttribute('aria-expanded')])).toEqual(
      [
        ['Show the findings', 'false'],
        ['Show the findings', 'false'],
      ],
    );
    expect(buttons().map((button) => button.getAttribute('aria-controls'))).toEqual([null, null]);
    expect(page().querySelector('.finding-list')).toBeNull();

    buttons()[1].click();
    await fixture.whenStable();

    expect(buttons().map(text)).toEqual(['Show the findings', 'Hide the findings']);
    expect(findingRows()).toEqual([
      [
        [
          'AppScan SAST',
          'HIGH',
          'src/main/java/com/bbh/cert/Login.java:42',
          'SQL Injection',
          'Use a prepared statement.',
        ],
      ],
    ]);
    const second = page().querySelector('.finding-list')!;
    expect(second.previousElementSibling).toBe(page().querySelectorAll('tr.decoration')[1]);
    expect(buttons()[1].getAttribute('aria-controls')).toBe(second.id);
    expect(second.querySelector('.finding-table')!.getAttribute('aria-label')).toBe(
      'Findings of Add the login page',
    );

    buttons()[0].click();
    await fixture.whenStable();

    expect(buttons().map(text)).toEqual(['Hide the findings', 'Hide the findings']);
    expect(findingRows().map((list) => list.length)).toEqual([3, 1]);
    const ids = [...page().querySelectorAll('.finding-list')].map((list) => list.id);
    expect(new Set(ids).size).toBe(2);
    expect(buttons().map((button) => button.getAttribute('aria-controls'))).toEqual(ids);

    buttons()[1].click();
    await fixture.whenStable();

    expect(buttons().map((button) => button.getAttribute('aria-expanded'))).toEqual([
      'true',
      'false',
    ]);
    expect(findingRows().map((list) => list.length)).toEqual([3]);
  });
});
