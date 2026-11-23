import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OneViewContext } from '@common/context/oneview-context';
import { of } from 'rxjs';
import { fieldOf, helpOf, unexplained } from '@common/testing/dom';
import { ActionHistoryApi } from '../core/api';
import { ActionLog } from '../core/models';
import { ActionHistoryPage, actionInScope } from './action-history';

const SAMPLE_LOGS: ActionLog[] = [
  {
    id: 1,
    createdAt: '2026-10-16T08:00:00Z',
    username: 'mateusz.matan',
    userDisplayName: 'Mateusz Matan',
    userEmail: 'mateusz.matan@bbh.com',
    departmentName: 'Corporate Technology',
    actionType: 'CREATE_PRODUCT',
    resourceType: 'PRODUCT',
    resourceName: 'CertScanner',
    details: 'Created product CERT with 2 service(s)',
    status: 'SUCCESS',
  },
  {
    id: 2,
    createdAt: '2026-10-16T08:30:00Z',
    username: 'mateusz.matan',
    userDisplayName: 'Mateusz Matan',
    userEmail: 'mateusz.matan@bbh.com',
    departmentName: 'Corporate Technology',
    actionType: 'REVOKE_KEY',
    resourceType: 'PIPELINE_KEY',
    resourceName: 'backend-api · Full',
    details: 'Invalidated the active key: Security breach',
    status: 'SUCCESS',
  },
];

describe('ActionHistoryPage', () => {
  let fixture: ComponentFixture<ActionHistoryPage>;
  let component: ActionHistoryPage;
  let api: { list: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { list: vi.fn().mockReturnValue(of(SAMPLE_LOGS)) };

    await TestBed.configureTestingModule({
      imports: [ActionHistoryPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActionHistoryApi, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActionHistoryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the page and list of logs', () => {
    expect(component).toBeTruthy();
    expect(component['columns'].length).toBe(7);
  });

  it('explains each filter with a help text', async () => {
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(unexplained(page)).toEqual([]);
    expect(helpOf(fieldOf(page, 'Status')!)).toBe(
      'Shows only the actions that succeeded, or only those that failed. A failed action gives its reason under Details.',
    );
  });

  it('filters rows by query', () => {
    component['filters'].controls.search.setValue('CertScanner');
    fixture.detectChanges();
    expect(component['filteredRows']().length).toBe(1);
    expect(component['filteredRows']()[0].resourceName).toBe('CertScanner');
  });

  it('shows actions, resources and statuses in words rather than codes', async () => {
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const cells = (column: string) =>
      [...page.querySelectorAll(`[col-id="${column}"].ag-cell`)].map((cell) =>
        cell.textContent!.trim(),
      );

    expect(cells('action')).toEqual(['Create product', 'Invalidate key']);
    expect(cells('resource')).toEqual(['Product CertScanner', 'Pipeline key backend-api · Full']);
    expect(cells('status')).toEqual(['Success', 'Success']);
  });

  it('finds a row by the words of its action', () => {
    component['filters'].controls.search.setValue('invalidate key');
    fixture.detectChanges();
    expect(component['filteredRows']().map((row) => row.id)).toEqual([2]);
  });

  it('filters rows by action type', () => {
    component['filters'].controls.actionType.setValue('REVOKE_KEY');
    fixture.detectChanges();
    expect(component['filteredRows']().length).toBe(1);
    expect(component['filteredRows']()[0].actionType).toBe('REVOKE_KEY');
  });

  it('shows only the actions on the chosen business service or application', async () => {
    const logs: ActionLog[] = [
      { ...SAMPLE_LOGS[0], id: 3, departmentId: 3, productId: 11 },
      { ...SAMPLE_LOGS[0], id: 4, departmentId: 5, departmentName: 'Fund Services', productId: 20 },
      {
        ...SAMPLE_LOGS[0],
        id: 5,
        departmentId: 3,
        productId: null,
        actionType: 'RENAME_DEPARTMENT',
      },
    ];
    api.list.mockReturnValue(of(logs));
    component['logsResource'].reload();
    await fixture.whenStable();
    const ids = () => component['all']().map((row) => row.id);
    const context = TestBed.inject(OneViewContext);

    expect(ids()).toEqual([3, 4, 5]);

    context.choose(3);
    expect(ids()).toEqual([3, 5]);
    expect(component['listTitle']()).toBe('Actions on the chosen business service');

    context.choose(3, 11);
    expect(ids()).toEqual([3]);

    context.choose(null);
  });

  it('matches older actions, kept without the ID of their business service, by its name', () => {
    const old = { ...SAMPLE_LOGS[0], departmentId: undefined, productId: undefined };

    expect(actionInScope(old, 3, null, 'Corporate Technology')).toBe(true);
    expect(actionInScope(old, 3, null, 'Custody')).toBe(false);
    expect(actionInScope(old, 3, null, null)).toBe(false);
    expect(actionInScope(old, 3, 11, 'Corporate Technology')).toBe(false);
    expect(actionInScope(old, null, null, null)).toBe(true);
  });
});
