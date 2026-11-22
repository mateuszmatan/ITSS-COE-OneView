import { AdminArea, OneViewArea, OneViewSection, OneViewTab } from '@common/core/sections';

export const PIPELINES: OneViewSection = {
  path: '/pipelines',
  label: 'Pipelines',
  heading: 'DevSecOps Pipelines',
  description:
    "Every automated build, test and security pipeline of your business service's products. Open one to see its key, its Jenkinsfile and its latest runs.",
};

export const SELF_SERVICE: OneViewSection = {
  path: '/self-service',
  label: 'Self-service',
  heading: 'DevSecOps Self-service',
  description:
    'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.',
};

export const MONITORING: OneViewSection = {
  path: '/monitoring',
  label: 'Pipeline Monitoring',
  heading: 'DevSecOps Pipeline Monitoring',
  description:
    'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.',
};

export const SAMM_MATURITY: OneViewSection = {
  path: '/samm',
  label: 'OWASP SAMM',
  heading: 'DevSecOps OWASP SAMM',
  description:
    'Software Assurance Maturity Model (OWASP SAMM v2.0) across BBH applications and business services. Continuous security posture scoring derived from pipeline health and verification gates.',
};

export const ACTION_HISTORY: OneViewSection = {
  path: '/history',
  label: 'Action history',
  heading: 'DevSecOps Action History',
  description:
    'History and audit log of all operations in the portal: who, when, what was done and the result.',
};

export const ADMIN: OneViewSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'DevSecOps Admin',
  description:
    'Set up the portal for everyone: business services, products and their services, what a new service gets, and the settings every pipeline shares.',
};

export const SECTIONS: readonly OneViewSection[] = [
  PIPELINES,
  SELF_SERVICE,
  MONITORING,
  SAMM_MATURITY,
  ACTION_HISTORY,
  ADMIN,
];

export const QA_METRICS: OneViewSection = {
  path: '/qa',
  label: 'QA Metrics',
  heading: 'QA Metrics',
  description:
    'Quality assurance of every product: the results of its tests, its code quality and its defects, with their trends in Grafana from InfluxDB.',
};

export const AGILE_MATURITY: OneViewSection = {
  path: '/agile',
  label: 'Agile Maturity',
  heading: 'Agile Maturity',
  description:
    'How mature the agile way of working of every business service and its teams is, and how it changes over time.',
};

export const DEVSECOPS: OneViewArea = { key: 'devsecops', label: 'DevSecOps', sections: SECTIONS };
export const QA: OneViewArea = { key: 'qa', label: 'QA', sections: [QA_METRICS] };
export const AGILE: OneViewArea = { key: 'agile', label: 'Agile', sections: [AGILE_MATURITY] };

export const AREAS: readonly OneViewArea[] = [DEVSECOPS, QA, AGILE];

export const ADMIN_DEPARTMENTS: OneViewTab = {
  path: '/admin/departments',
  label: 'Business Services',
};
export const ADMIN_PRODUCTS: OneViewTab = { path: '/admin/products', label: 'Products' };
export const ADMIN_TEMPLATE: OneViewTab = { path: '/admin/template', label: 'Service template' };
export const ADMIN_SETTINGS: OneViewTab = { path: '/admin/settings', label: 'Library defaults' };

export const DEVSECOPS_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS, ADMIN_TEMPLATE, ADMIN_SETTINGS],
};

export function adminProduct(id: number | string, ...rest: string[]): (string | number)[] {
  return [ADMIN_PRODUCTS.path, id, ...rest];
}

export function pipelinePage(id: number | string): (string | number)[] {
  return [PIPELINES.path, id];
}

export function monitoringProduct(id: number | string): (string | number)[] {
  return [`${MONITORING.path}/products`, id];
}

export function monitoringPipeline(id: number | string): (string | number)[] {
  return [`${MONITORING.path}/pipelines`, id];
}

export function sammPage(): (string | number)[] {
  return [SAMM_MATURITY.path];
}

