import { QualityApplication, QualityTargets } from '../core/models';

export type TargetKey = keyof QualityTargets;

/** The most open critical defects a target may allow, as the portal accepts it. */
export const MAX_CRITICAL_DEFECTS = 999_999;

export interface TargetField {
  key: TargetKey;
  label: string;
  /** The label of the field that sets the target. */
  input: string;
  min: number;
  max: number;
  /** What the target asks for. */
  help: string;
  /** The target in words, for example "at least 80%". */
  text: (value: number) => string;
}

export const TARGET_FIELDS: readonly TargetField[] = [
  {
    key: 'coveragePercent',
    label: 'Code coverage',
    input: 'Code coverage (%)',
    min: 0,
    max: 100,
    help: 'The least share of the code, in percent, that the automated tests of an application run.',
    text: (value) => `at least ${value}%`,
  },
  {
    key: 'passRatePercent',
    label: 'Test pass rate',
    input: 'Test pass rate (%)',
    min: 0,
    max: 100,
    help: 'The least share of the automated tests, in percent, that pass in a run of the pipeline.',
    text: (value) => `at least ${value}%`,
  },
  {
    key: 'maxCriticalDefects',
    label: 'Open critical defects',
    input: 'Open critical defects (at most)',
    min: 0,
    max: MAX_CRITICAL_DEFECTS,
    help: 'The most critical defects an application may have open at a time; 0 allows none.',
    text: (value) => `at most ${value}`,
  },
];

/** One target in words, or that it is not set. */
export function targetText(field: TargetField, value: number | null | undefined): string {
  return value === null || value === undefined ? 'not set' : field.text(value);
}

/** Every target in one line: "Code coverage at least 80%, test pass rate at least 95%, ...". */
export function targetsSummary(targets: QualityTargets): string {
  const text = TARGET_FIELDS.map(
    (field) => `${field.label.toLowerCase()} ${targetText(field, targets[field.key])}`,
  ).join(', ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** The targets an application sets for itself, in words, or that it follows BBH. */
export function ownTargetsSummary(targets: QualityTargets): string {
  const own = TARGET_FIELDS.filter(
    (field) => targets[field.key] !== null && targets[field.key] !== undefined,
  );
  if (own.length === 0) {
    return 'The BBH targets';
  }
  const text = own
    .map((field) => `${field.label.toLowerCase()} ${field.text(targets[field.key]!)}`)
    .join(', ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Why an application is not in the QM Metric Tool, and how an administrator brings it in. */
export function notTakingPartText(application: QualityApplication): string {
  return application.version === null
    ? `${application.name} does not take part in the QM Metric Tool yet. An administrator sets it up in OneView Admin with its QA lead, where its results are and its own quality targets.`
    : `${application.name} no longer takes part in the QM Metric Tool. An administrator brings it back in OneView Admin by editing it and ticking Takes part.`;
}

/** Whether an application takes part in the QM Metric Tool, in words. */
export function participation(application: QualityApplication): string {
  if (application.enabled) {
    return 'Takes part';
  }
  return application.version === null ? 'Not set up' : 'Does not take part';
}
