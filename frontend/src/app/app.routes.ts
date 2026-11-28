import { Routes } from '@angular/router';
import { unsavedChangesGuard } from '@common/core/unsaved-changes';
import { WITH_CONTEXT } from '@common/shell/app-shell';
import {
  ACTION_HISTORY,
  ADMIN,
  ADMIN_AGILE,
  ADMIN_DEPARTMENTS,
  ADMIN_JENKINS,
  ADMIN_PRODUCTS,
  ADMIN_QA,
  ADMIN_SETTINGS,
  ADMIN_TEMPLATE,
  AGILE_MATURITY,
  LANDING,
  ONEVIEW_ADMIN,
  MONITORING,
  PIPELINES,
  PIPELINE_WIZARD,
  QA_METRICS,
  SAMM_MATURITY,
  SELF_SERVICE,
} from './core/sections';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: LANDING.label,
    data: WITH_CONTEXT,
    loadComponent: () => import('./landing/landing-page').then((m) => m.LandingPage),
  },
  {
    path: 'pipelines',
    title: PIPELINES.heading,
    data: WITH_CONTEXT,
    loadComponent: () => import('./pipelines/pipeline-list').then((m) => m.PipelineList),
  },
  {
    path: 'pipelines/new',
    title: PIPELINE_WIZARD.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pipeline-wizard/pipeline-wizard').then((m) => m.PipelineWizard),
  },
  {
    path: 'pipelines/:id',
    title: 'Pipeline',
    loadComponent: () => import('./pipelines/pipeline-page').then((m) => m.PipelinePage),
  },
  {
    path: 'self-service',
    title: SELF_SERVICE.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./self-service/self-service').then((m) => m.SelfService),
  },
  {
    path: 'monitoring',
    title: MONITORING.heading,
    data: WITH_CONTEXT,
    loadComponent: () =>
      import('./monitoring/monitoring-overview').then((m) => m.MonitoringOverview),
  },
  {
    path: 'monitoring/products/:id',
    title: 'Product pipelines',
    loadComponent: () =>
      import('./monitoring/product-monitoring').then((m) => m.ProductMonitoringPage),
  },
  {
    path: 'monitoring/pipelines/:id',
    title: 'Pipeline details',
    loadComponent: () =>
      import('./monitoring/pipeline-monitoring').then((m) => m.PipelineMonitoringPage),
  },
  {
    path: 'samm',
    title: SAMM_MATURITY.heading,
    data: WITH_CONTEXT,
    loadComponent: () => import('./samm/samm-overview').then((m) => m.SammOverviewPage),
  },
  {
    path: 'qa',
    title: QA_METRICS.heading,
    data: WITH_CONTEXT,
    loadComponent: () => import('./qa/qa-overview').then((m) => m.QaOverviewPage),
  },
  {
    path: 'agile',
    title: AGILE_MATURITY.heading,
    data: WITH_CONTEXT,
    loadComponent: () => import('./agile/agile-overview').then((m) => m.AgileOverviewPage),
  },
  {
    path: 'history',
    title: ACTION_HISTORY.heading,
    data: WITH_CONTEXT,
    loadComponent: () => import('./history/action-history').then((m) => m.ActionHistoryPage),
  },
  {
    path: 'admin/products/new',
    title: 'Add product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin/products/:id',
    title: 'Product',
    loadComponent: () => import('./products/product-detail').then((m) => m.ProductDetail),
  },
  {
    path: 'admin/products/:id/edit',
    title: 'Edit product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin',
    loadComponent: () => import('@common/admin/admin-page').then((m) => m.AdminPage),
    data: { area: ONEVIEW_ADMIN },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${ADMIN_DEPARTMENTS.label} · ${ADMIN.heading}`,
        loadComponent: () =>
          import('./departments/pipeline-departments').then((m) => m.PipelineDepartments),
      },
      {
        path: 'products',
        title: `${ADMIN_PRODUCTS.label} · ${ADMIN.heading}`,
        data: WITH_CONTEXT,
        loadComponent: () => import('./products/product-list').then((m) => m.ProductList),
      },
      {
        path: 'template',
        title: `${ADMIN_TEMPLATE.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./settings/service-template-page').then((m) => m.ServiceTemplatePage),
      },
      {
        path: 'settings',
        title: `${ADMIN_SETTINGS.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
      },
      {
        path: 'jenkins',
        title: `${ADMIN_JENKINS.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./settings/jenkins-automation-page').then((m) => m.JenkinsAutomationPage),
      },
      {
        path: 'qa',
        title: `${ADMIN_QA.group} · ${ADMIN.heading}`,
        data: WITH_CONTEXT,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./qa/qa-admin').then((m) => m.QaAdmin),
      },
      {
        path: 'agile',
        title: `${ADMIN_AGILE.group} · ${ADMIN.heading}`,
        data: WITH_CONTEXT,
        loadComponent: () => import('./agile/agile-admin').then((m) => m.AgileAdmin),
      },
    ],
  },
  { path: 'products', redirectTo: 'admin/products' },
  { path: 'settings', redirectTo: 'admin/settings' },
  { path: '**', redirectTo: '' },
];
