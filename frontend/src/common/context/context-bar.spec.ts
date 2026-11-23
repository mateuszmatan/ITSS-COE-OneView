import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { choose, optionsOf, selectOf, text } from '../testing/dom';
import { ContextBar } from './context-bar';
import { CONTEXT_APPLICATION_KEY, CONTEXT_DEPARTMENT_KEY, OneViewContext } from './oneview-context';

const DEPARTMENTS = [
  { id: 3, name: 'Custody' },
  { id: 5, name: 'Fund Services' },
];
const PRODUCTS = [
  { id: 11, code: 'LEDGER', name: 'Ledger', departmentId: 3, departmentName: 'Custody' },
  { id: 12, code: 'PAY', name: 'Payments', departmentId: 3, departmentName: 'Custody' },
  { id: 20, code: 'FUND', name: 'Fund Admin', departmentId: 5, departmentName: 'Fund Services' },
  { id: 30, code: 'OLD', name: 'Archive', departmentId: null, departmentName: null },
];

describe('ContextBar', () => {
  let fixture: ComponentFixture<ContextBar>;
  let http: HttpTestingController;

  async function create(): Promise<void> {
    TestBed.configureTestingModule({
      imports: [ContextBar],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ContextBar);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(DEPARTMENTS);
    http.expectOne('/api/products').flush(PRODUCTS);
    await fixture.whenStable();
  }

  afterEach(() => {
    localStorage.removeItem(CONTEXT_DEPARTMENT_KEY);
    localStorage.removeItem(CONTEXT_APPLICATION_KEY);
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const department = () => selectOf(page(), 'Business service');
  const application = () => selectOf(page(), 'Application');
  const groups = () =>
    [...application().querySelectorAll('optgroup')].map((group) => group.getAttribute('label'));
  const context = () => TestBed.inject(OneViewContext);

  it('offers every business service and every application by business service, all of BBH first', async () => {
    await create();

    expect(optionsOf(department())).toEqual(['All business services', 'Custody', 'Fund Services']);
    expect(optionsOf(application())).toEqual([
      'All applications',
      'Ledger',
      'Payments',
      'Fund Admin',
      'Archive',
    ]);
    expect(groups()).toEqual(['Custody', 'Fund Services', 'Not in a business service']);
    expect(text(page().querySelector('.context-summary'))).toBe(
      'Showing all of BBH: 4 applications in 2 business services',
    );
  });

  it('narrows the applications to the chosen business service and sums it up', async () => {
    await create();

    choose(department(), 'Custody');
    await fixture.whenStable();

    expect(context().scope()).toEqual({ departmentId: 3, productId: null });
    expect(optionsOf(application())).toEqual(['All applications of Custody', 'Ledger', 'Payments']);
    expect(groups()).toEqual([]);
    expect(text(page().querySelector('.context-summary'))).toBe('Showing Custody: 2 applications');

    choose(application(), 'Payments');
    await fixture.whenStable();

    expect(context().scope()).toEqual({ departmentId: 3, productId: 12 });
    expect(text(page().querySelector('.context-summary'))).toBe(
      'Showing Payments (PAY) of Custody',
    );

    choose(department(), 'All business services');
    await fixture.whenStable();

    expect(context().scope()).toEqual({ departmentId: null, productId: null });
    expect(application().value).toContain('null');
  });

  it('takes the business service of an application chosen among all of them', async () => {
    await create();

    choose(application(), 'Fund Admin');
    await fixture.whenStable();

    expect(context().scope()).toEqual({ departmentId: 5, productId: 20 });
    expect(department().selectedOptions[0].textContent?.trim()).toBe('Fund Services');
    expect(application().selectedOptions[0].textContent?.trim()).toBe('Fund Admin');
  });

  it('shows what this browser remembers, and follows a choice made elsewhere', async () => {
    localStorage.setItem(CONTEXT_DEPARTMENT_KEY, '3');
    localStorage.setItem(CONTEXT_APPLICATION_KEY, '11');
    await create();

    expect(department().selectedOptions[0].textContent?.trim()).toBe('Custody');
    expect(application().selectedOptions[0].textContent?.trim()).toBe('Ledger');

    context().choose(5);
    await fixture.whenStable();

    expect(department().selectedOptions[0].textContent?.trim()).toBe('Fund Services');
    expect(application().selectedOptions[0].textContent?.trim()).toBe(
      'All applications of Fund Services',
    );
  });

  it('says when the business services cannot be read and reads them again', async () => {
    TestBed.configureTestingModule({
      imports: [ContextBar],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ContextBar);
    fixture.detectChanges();
    http
      .expectOne('/api/departments')
      .flush({ detail: 'Database down' }, { status: 500, statusText: 'Error' });
    expect(http.expectOne('/api/products').cancelled).toBe(true);
    await fixture.whenStable();

    expect(text(page().querySelector('.context-error'))).toBe(
      'The business services could not be read (Database down).',
    );

    page().querySelector<HTMLButtonElement>('.context-summary button')!.click();
    http.expectOne('/api/departments').flush(DEPARTMENTS);
    http.expectOne('/api/products').flush(PRODUCTS);
    await fixture.whenStable();

    expect(page().querySelector('.context-error')).toBeNull();
    expect(optionsOf(department())).toHaveLength(3);
  });
});
