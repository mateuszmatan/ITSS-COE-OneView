import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CONTEXT_APPLICATION_KEY,
  CONTEXT_DEPARTMENT_KEY,
  OneViewContext,
  inScope,
  scopeNameIn,
  scopeParams,
} from './oneview-context';

const DEPARTMENTS = [
  { id: 3, name: 'Custody', version: 0, productCount: 2 },
  { id: 5, name: 'Fund Services', version: 1, productCount: 1 },
];
const PRODUCTS = [
  {
    id: 12,
    code: 'PAY',
    name: 'payments',
    departmentId: 3,
    departmentName: 'Custody',
    serviceCount: 1,
  },
  { id: 11, code: 'LEDGER', name: 'Ledger', departmentId: 3, departmentName: 'Custody' },
  { id: 20, code: 'FUND', name: 'Fund Admin', departmentId: 5, departmentName: 'Fund Services' },
  { id: 30, code: 'OLD', name: 'Archive', departmentId: null, departmentName: null },
];

describe('OneViewContext', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const load = (departments: unknown[] = DEPARTMENTS, products: unknown[] = PRODUCTS) => {
    const http = TestBed.inject(HttpTestingController);
    const context = TestBed.inject(OneViewContext);
    context.refresh();
    expect(context.loading()).toBe(true);
    http.expectOne('/api/departments').flush(departments);
    http.expectOne('/api/products').flush(products);
    return context;
  };

  it('starts with the whole of BBH and remembers the business service and application in the browser', () => {
    const context = TestBed.inject(OneViewContext);
    expect(context.scope()).toEqual({ departmentId: null, productId: null });
    expect(context.level()).toBe('bbh');
    expect(context.scopeName()).toBe('BBH');

    context.choose(3, 11);
    expect(context.level()).toBe('application');
    expect(localStorage.getItem(CONTEXT_DEPARTMENT_KEY)).toBe('3');
    expect(localStorage.getItem(CONTEXT_APPLICATION_KEY)).toBe('11');

    context.choose(5);
    expect(context.level()).toBe('department');
    expect(localStorage.getItem(CONTEXT_DEPARTMENT_KEY)).toBe('5');
    expect(localStorage.getItem(CONTEXT_APPLICATION_KEY)).toBeNull();

    context.choose(null);
    expect(localStorage.getItem(CONTEXT_DEPARTMENT_KEY)).toBeNull();
  });

  it('reads the business service chosen before there were applications to choose', () => {
    localStorage.setItem(CONTEXT_DEPARTMENT_KEY, '5');
    localStorage.setItem(CONTEXT_APPLICATION_KEY, 'x1');

    expect(TestBed.inject(OneViewContext).scope()).toEqual({ departmentId: 5, productId: null });
  });

  it('lists the business services and their applications by name and names what is chosen', () => {
    localStorage.setItem(CONTEXT_DEPARTMENT_KEY, '3');
    const context = load();

    expect(context.loading()).toBe(false);
    expect(context.departments()).toEqual([
      { id: 3, name: 'Custody' },
      { id: 5, name: 'Fund Services' },
    ]);
    expect(context.applications().map((application) => application.name)).toEqual([
      'Archive',
      'Fund Admin',
      'Ledger',
      'payments',
    ]);
    expect(context.applicationsInScope().map((application) => application.code)).toEqual([
      'LEDGER',
      'PAY',
    ]);
    expect(context.scopeName()).toBe('Custody');

    context.chooseApplication(20);
    expect(context.scope()).toEqual({ departmentId: 5, productId: 20 });
    expect(context.scopeName()).toBe('Fund Admin');
    expect(context.application()?.departmentName).toBe('Fund Services');

    context.chooseApplication(null);
    expect(context.scope()).toEqual({ departmentId: 5, productId: null });
  });

  it.each([
    ['an application that was deleted', '3', '99', { departmentId: 3, productId: null }],
    ['an application that moved', '5', '11', { departmentId: 3, productId: 11 }],
    ['a business service that was deleted', '7', null, { departmentId: null, productId: null }],
    [
      'an application of a deleted business service',
      '7',
      '99',
      { departmentId: null, productId: null },
    ],
  ])('drops or follows %s once the lists are read', (_, department, application, expected) => {
    localStorage.setItem(CONTEXT_DEPARTMENT_KEY, department);
    if (application) {
      localStorage.setItem(CONTEXT_APPLICATION_KEY, application);
    }

    expect(load().scope()).toEqual(expected);
  });

  it('keeps what is chosen and says why when the lists cannot be read', () => {
    localStorage.setItem(CONTEXT_DEPARTMENT_KEY, '3');
    const http = TestBed.inject(HttpTestingController);
    const context = TestBed.inject(OneViewContext);

    context.refresh();
    http.expectOne('/api/departments').flush('down', { status: 503, statusText: 'Unavailable' });

    expect(context.loadError()).toBeTruthy();
    expect(context.loaded()).toBe(false);
    expect(context.scope()).toEqual({ departmentId: 3, productId: null });
    expect(context.scopeName()).toBe('the chosen business service');
  });

  it('tells whether something of a business service and an application is in what is chosen', () => {
    const context = TestBed.inject(OneViewContext);
    expect(context.includes(3, 11)).toBe(true);
    expect(context.includes(null)).toBe(true);

    context.choose(3);
    expect(context.includes(3, 11)).toBe(true);
    expect(context.includes(5, 20)).toBe(false);
    expect(context.includes(null)).toBe(false);

    context.choose(3, 11);
    expect(context.includes(3, 11)).toBe(true);
    expect(context.includes(3, 12)).toBe(false);
    expect(context.includes(3)).toBe(false);
    expect(inScope({ departmentId: null, productId: 11 }, 5, 11)).toBe(true);
  });

  it('narrows the API to what is chosen', () => {
    expect(scopeParams()).toEqual({});
    expect(scopeParams({ departmentId: null, productId: null })).toEqual({});
    expect(scopeParams({ departmentId: 3, productId: null })).toEqual({ departmentId: 3 });
    expect(scopeParams({ departmentId: 3, productId: 11 })).toEqual({
      departmentId: 3,
      productId: 11,
    });
  });

  it('names what is chosen from the applications a page has read', () => {
    const applications = [
      { productId: 11, name: 'Ledger', departmentId: 3, departmentName: 'Custody' },
      { productId: 30, name: 'Archive', departmentId: null, departmentName: null },
    ];
    const scope = (departmentId: number | null, productId: number | null = null) => ({
      departmentId,
      productId,
    });

    expect(scopeNameIn(scope(null), applications, 'fallback')).toBe('BBH');
    expect(scopeNameIn(scope(3), applications, 'fallback')).toBe('Custody');
    expect(scopeNameIn(scope(3, 11), applications, 'fallback')).toBe('Ledger');
    expect(scopeNameIn(scope(5), applications, 'Fund Services')).toBe('Fund Services');
    expect(scopeNameIn(scope(3, 12), applications, 'the chosen application')).toBe(
      'the chosen application',
    );
  });

  it('works without the storage of the browser', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const context = TestBed.inject(OneViewContext);
    expect(context.departmentId()).toBeNull();

    context.choose(3, 11);
    expect(context.scope()).toEqual({ departmentId: 3, productId: 11 });
  });
});
