import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { ProductsApi } from '../core/api';
import { Department, ProductSummary } from '../core/models';
import { ADMIN_PRODUCTS, adminProduct } from '../core/sections';
import { counted } from '@common/shared/formatting';
import { OvInput } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { byDepartment } from '@common/departments/departments';
import { pipelineTally, tally } from './pipeline-tally';
import { DepartmentsApi } from '@common/core/api';
import { OneViewContext } from '@common/context/oneview-context';

@Component({
  selector: 'ov-product-list',
  imports: [ReactiveFormsModule, RouterLink, OvInput, GRID, OvLoading, LoadError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly router = inject(Router);
  private readonly context = inject(OneViewContext);

  protected readonly newProduct = `${ADMIN_PRODUCTS.path}/new`;
  protected readonly adminProduct = adminProduct;
  protected readonly productId = (product: ProductSummary) => product.id;
  protected readonly clickable = () => 'clickable';
  protected readonly columns: GridColumn<ProductSummary>[] = [
    {
      key: 'product',
      header: 'Product',
      value: (product) => product.name,
      minWidth: 200,
      wrap: true,
    },
    {
      key: 'ownerTeam',
      header: 'Owner team',
      value: (product) => product.ownerTeam ?? '–',
      width: 180,
    },
    {
      key: 'services',
      header: 'Services',
      value: (product) => product.serviceCount,
      numeric: true,
      width: 100,
    },
    {
      key: 'pipelines',
      header: 'Pipelines',
      value: (product) => product.pipelineCount,
      width: 170,
    },
  ];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly query = toSignal(
    this.search.valueChanges.pipe(
      debounceTime(250),
      map((value) => value.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  protected readonly products = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly departments = rxResource({
    stream: () => this.departmentsApi.list<Department>(),
  });

  protected readonly level = this.context.level;
  protected readonly scopeName = computed(() => {
    const { departmentId, productId } = this.context.scope();
    if (productId !== null) {
      const products = this.products.hasValue() ? this.products.value() : [];
      return products.find((product) => product.id === productId)?.name ?? this.context.scopeName();
    }
    const departments = this.departments.hasValue() ? this.departments.value() : [];
    return (
      departments.find((department) => department.id === departmentId)?.name ??
      this.context.scopeName()
    );
  });

  /** The business services with their products, only the chosen business service or application if any. */
  protected readonly groups = computed(() => {
    if (!this.products.hasValue() || !this.departments.hasValue()) {
      return null;
    }
    const { departmentId } = this.context.scope();
    const products = this.products
      .value()
      .filter((product) => this.context.includes(product.departmentId, product.id));
    return byDepartment(this.departments.value(), products).filter((group) => {
      switch (this.level()) {
        case 'application':
          return group.products.length > 0;
        case 'department':
          return (
            group.department?.id === departmentId && (group.products.length > 0 || !this.query())
          );
        default:
          return group.products.length || !this.query();
      }
    });
  });
  protected readonly empty = computed(() =>
    (this.groups() ?? []).every((group) => group.products.length === 0),
  );
  protected readonly total = computed(() => {
    const groups = this.groups() ?? [];
    const placed = groups.filter((group) => group.department);
    const products = placed.reduce((sum, group) => sum + group.products.length, 0);
    const unassigned = groups.find((group) => !group.department)?.products.length ?? 0;
    const total = `${counted(products, 'product')} in ${counted(placed.length, 'business service')}`;
    return unassigned ? `${total}, ${unassigned} not in a business service` : total;
  });

  protected readonly tally = tally;
  protected readonly pipelineTally = pipelineTally;

  protected reload(): void {
    this.products.reload();
    this.departments.reload();
  }

  protected clearSearch(): void {
    this.search.setValue('');
  }

  protected open(product: ProductSummary): void {
    this.router.navigate(adminProduct(product.id));
  }
}
