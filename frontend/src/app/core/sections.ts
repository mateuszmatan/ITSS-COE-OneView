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
  heading: 'OneView Action History',
  description:
    'History and audit log of all operations in OneView, in every area: who, when, what was done and the result.',
};

export const ADMIN: OneViewSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'OneView Admin',
  description:
    'Set up OneView for everyone: business services and their applications, what a new DevSecOps service gets and the settings every pipeline shares, the quality targets of the QM Metric Tool and the agile teams and their assessments.',
};

/** The sections of the DevSecOps area. */
export const SECTIONS: readonly OneViewSection[] = [
  PIPELINES,
  SELF_SERVICE,
  MONITORING,
  SAMM_MATURITY,
];

/** The pages of every area, below the areas in the side menu. */
export const TOOLS: readonly OneViewSection[] = [ACTION_HISTORY, ADMIN];

export const QA_METRICS: OneViewSection = {
  path: '/qa',
  label: 'QM Metric Tool',
  heading: 'QM Metric Tool',
  description:
    'Quality assurance of every application: the quality targets it works to, who leads its quality, and where its test results, code quality and defects are, with their trends in Grafana from InfluxDB.',
};

export const AGILE_MATURITY: OneViewSection = {
  path: '/agile',
  label: 'Agile Assessment & Adoption',
  heading: 'Agile Assessment & Adoption',
  description:
    'How mature the agile way of working of every application team and business service is, dimension by dimension, and how it changes from one assessment to the next.',
};

export const LANDING: OneViewSection = {
  path: '/',
  label: 'Main Landing Page',
  heading: 'ITSS COE OneView',
  description:
    'One view of the ITSS COE tools at BBH. Choose where you want to go: DevSecOps, the QM Metric Tool or Agile Assessment & Adoption.',
};

export const DEVSECOPS: OneViewArea = {
  key: 'devsecops',
  label: 'DevSecOps',
  path: MONITORING.path,
  description:
    'The automated build, test and security pipelines of every product: set them up in Self-service, follow their runs in Pipeline Monitoring and see the OWASP SAMM maturity of every business service.',
  sections: SECTIONS,
};
export const QA: OneViewArea = {
  key: 'qa',
  label: QA_METRICS.label,
  path: QA_METRICS.path,
  description: QA_METRICS.description,
  sections: [QA_METRICS],
};
export const AGILE: OneViewArea = {
  key: 'agile',
  label: AGILE_MATURITY.label,
  path: AGILE_MATURITY.path,
  description: AGILE_MATURITY.description,
  sections: [AGILE_MATURITY],
};

export const AREAS: readonly OneViewArea[] = [DEVSECOPS, QA, AGILE];

export const ADMIN_DEPARTMENTS: OneViewTab = {
  path: '/admin/departments',
  label: 'Business Services',
  group: 'Catalog',
};
export const ADMIN_PRODUCTS: OneViewTab = {
  path: '/admin/products',
  label: 'Products',
  group: 'Catalog',
};
export const ADMIN_TEMPLATE: OneViewTab = {
  path: '/admin/template',
  label: 'Service template',
  group: DEVSECOPS.label,
};
export const ADMIN_SETTINGS: OneViewTab = {
  path: '/admin/settings',
  label: 'Library defaults',
  group: DEVSECOPS.label,
};
export const ADMIN_QA: OneViewTab = {
  path: '/admin/qa',
  label: 'Applications and targets',
  group: QA.label,
};
export const ADMIN_AGILE: OneViewTab = {
  path: '/admin/agile',
  label: 'Teams and assessments',
  group: AGILE.label,
};

export const ONEVIEW_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS, ADMIN_TEMPLATE, ADMIN_SETTINGS, ADMIN_QA, ADMIN_AGILE],
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
