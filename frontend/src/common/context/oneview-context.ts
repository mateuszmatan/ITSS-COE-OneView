import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';

/** The business service chosen in this browser; Self-service also preselects it for a new product. */
export const CONTEXT_DEPARTMENT_KEY = 'oneview.department';
/** The application chosen in this browser. */
export const CONTEXT_APPLICATION_KEY = 'oneview.application';

/** What OneView shows: the whole of BBH, one business service or one application of it. */
export interface ContextScope {
  departmentId: number | null;
  productId: number | null;
}

export type ContextLevel = 'bbh' | 'department' | 'application';

export interface ContextDepartment {
  id: number;
  name: string;
}

/** An application is a product of the catalog. */
export interface ContextApplication {
  id: number;
  code: string;
  name: string;
  departmentId: number | null;
  departmentName: string | null;
}

interface ContextLists {
  departments: ContextDepartment[];
  applications: ContextApplication[];
}

/** The query parameters that narrow an answer of the API to a scope. */
export function scopeParams(scope?: ContextScope | null): Record<string, number> {
  const params: Record<string, number> = {};
  if (scope?.departmentId != null) {
    params['departmentId'] = scope.departmentId;
  }
  if (scope?.productId != null) {
    params['productId'] = scope.productId;
  }
  return params;
}

/** Whether something of a business service and an application is in a scope. */
export function inScope(
  scope: ContextScope,
  departmentId: number | null | undefined,
  productId?: number | null,
): boolean {
  if (scope.productId !== null) {
    return productId === scope.productId;
  }
  return scope.departmentId === null || departmentId === scope.departmentId;
}

/** An application as a page of an area reads it, with its business service. */
export interface NamedApplication {
  productId: number;
  name: string;
  departmentId: number | null;
  departmentName: string | null;
}

/**
 * The name of what a scope shows, read from the applications a page has already read: an application, a business
 * service or BBH. The fallback names a choice none of them belongs to.
 */
export function scopeNameIn(
  scope: ContextScope,
  applications: readonly NamedApplication[],
  fallback: string,
): string {
  if (scope.productId !== null) {
    return (
      applications.find((application) => application.productId === scope.productId)?.name ??
      fallback
    );
  }
  if (scope.departmentId !== null) {
    return (
      applications.find((application) => application.departmentId === scope.departmentId)
        ?.departmentName ?? fallback
    );
  }
  return 'BBH';
}

function stored(key: string): number | null {
  try {
    const value = localStorage.getItem(key);
    return value && /^\d+$/.test(value) ? Number(value) : null;
  } catch {
    return null;
  }
}

function store(key: string, id: number | null): void {
  try {
    if (id === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, String(id));
    }
  } catch {
    return;
  }
}

const byName = (a: { name: string }, b: { name: string }) =>
  a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });

/**
 * The business service and the application a person looks at. Every page of OneView that sums up applications
 * follows it, and this browser remembers it. Nothing chosen is the whole of BBH; a business service alone sums up
 * all of its applications.
 */
@Injectable({ providedIn: 'root' })
export class OneViewContext {
  private readonly http = inject(HttpClient);
  private readonly chosen = signal<ContextScope>({
    departmentId: stored(CONTEXT_DEPARTMENT_KEY),
    productId: stored(CONTEXT_APPLICATION_KEY),
  });
  private readonly lists = signal<ContextLists | null>(null);

  readonly scope = this.chosen.asReadonly();
  readonly departmentId = computed(() => this.chosen().departmentId);
  readonly productId = computed(() => this.chosen().productId);
  readonly level = computed<ContextLevel>(() =>
    this.productId() !== null ? 'application' : this.departmentId() !== null ? 'department' : 'bbh',
  );

  readonly loading = signal(false);
  readonly loadError = signal<unknown>(null);
  readonly loaded = computed(() => this.lists() !== null);
  readonly departments = computed(() => this.lists()?.departments ?? []);
  readonly applications = computed(() => this.lists()?.applications ?? []);

  readonly department = computed(
    () => this.departments().find((department) => department.id === this.departmentId()) ?? null,
  );
  readonly application = computed(
    () => this.applications().find((application) => application.id === this.productId()) ?? null,
  );
  /** The applications of the chosen business service, or every application. */
  readonly applicationsInScope = computed(() => {
    const departmentId = this.departmentId();
    return departmentId === null
      ? this.applications()
      : this.applications().filter((application) => application.departmentId === departmentId);
  });
  /** What is shown, by name: an application, a business service or BBH. */
  readonly scopeName = computed(() => {
    switch (this.level()) {
      case 'application':
        return this.application()?.name ?? 'the chosen application';
      case 'department':
        return this.department()?.name ?? 'the chosen business service';
      default:
        return 'BBH';
    }
  });

  /** Chooses a business service, and one of its applications or all of them; nothing is the whole of BBH. */
  choose(departmentId: number | null, productId: number | null = null): void {
    const current = this.chosen();
    if (current.departmentId === departmentId && current.productId === productId) {
      return;
    }
    this.chosen.set({ departmentId, productId });
    store(CONTEXT_DEPARTMENT_KEY, departmentId);
    store(CONTEXT_APPLICATION_KEY, productId);
  }

  /** Chooses one application, with its business service, or all applications of the business service. */
  chooseApplication(productId: number | null): void {
    if (productId === null) {
      this.choose(this.departmentId(), null);
      return;
    }
    const application = this.applications().find((candidate) => candidate.id === productId);
    this.choose(application ? application.departmentId : this.departmentId(), productId);
  }

  /** Whether something of a business service and an application is in what is chosen. */
  includes(departmentId: number | null | undefined, productId?: number | null): boolean {
    return inScope(this.chosen(), departmentId, productId);
  }

  /**
   * Reads the business services and applications to choose from. A choice that no longer exists is dropped, and
   * an application that moved takes its new business service with it.
   */
  refresh(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    forkJoin({
      departments: this.http.get<ContextDepartment[]>('/api/departments'),
      applications: this.http.get<ContextApplication[]>('/api/products'),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ departments, applications }) => {
          this.lists.set({
            departments: departments.map(({ id, name }) => ({ id, name })),
            applications: applications
              .map(({ id, code, name, departmentId, departmentName }) => ({
                id,
                code,
                name,
                departmentId: departmentId ?? null,
                departmentName: departmentName ?? null,
              }))
              .sort(byName),
          });
          this.loadError.set(null);
          this.reconcile();
        },
        error: (error: unknown) => this.loadError.set(error),
      });
  }

  /** Reads what there is to choose from unless it is read or being read, for the names a page shows. */
  ensureLoaded(): void {
    if (!this.loaded() && !this.loading()) {
      this.refresh();
    }
  }

  private reconcile(): void {
    const { departmentId, productId } = this.chosen();
    const application = this.application();
    if (productId !== null && !application) {
      this.choose(this.department() ? departmentId : null, null);
    } else if (application && application.departmentId !== departmentId) {
      this.choose(application.departmentId, application.id);
    } else if (departmentId !== null && !this.department()) {
      this.choose(null, null);
    }
  }
}
