import { formatDate } from '@angular/common';
import {
  AgileApplication,
  AgileAssessment,
  AgileChoice,
  AgileDimension,
  AgileFramework,
} from '../core/models';

/** The highest level of an assessment, for the pages that do not read the model. */
export const AGILE_MAX_LEVEL = 5;

/** The latest assessment of an application, if it was ever assessed. */
export function latestAssessment(application: AgileApplication): AgileAssessment | null {
  return application.assessments[0] ?? null;
}

/** A level to one decimal, for example 3.2, or a dash without one. */
export function levelText(level: number | null | undefined): string {
  return typeof level === 'number' ? level.toFixed(1) : '–';
}

/** The mean of the levels there are, to one decimal; null without any. */
export function averageLevel(levels: readonly (number | null | undefined)[]): number | null {
  const known = levels.filter((level): level is number => typeof level === 'number');
  if (known.length === 0) {
    return null;
  }
  return Math.round((known.reduce((sum, level) => sum + level, 0) / known.length) * 10) / 10;
}

/** The mean level of one dimension over the latest assessment of every application given. */
export function averageOf(
  applications: readonly AgileApplication[],
  dimension: AgileDimension,
): number | null {
  return averageLevel(
    applications.map((application) => latestAssessment(application)?.levels[dimension]),
  );
}

/** The label of a way of working, or that none is set. */
export function frameworkLabel(
  frameworks: readonly AgileChoice<AgileFramework>[],
  code: AgileFramework | null,
): string {
  if (code === null) {
    return 'Not set';
  }
  return frameworks.find((framework) => framework.code === code)?.label ?? code;
}

/** Whether an application takes part in Agile Assessment & Adoption, in words. */
export function agileParticipation(application: AgileApplication): string {
  if (application.enabled) {
    return 'Takes part';
  }
  return application.version === null ? 'Not set up' : 'Does not take part';
}

/** What brings the team of an application that does not take part into Agile Assessment & Adoption. */
export function firstStepToAssess(application: AgileApplication): string {
  return application.version === null
    ? `Set up the team of ${application.name} first`
    : `Edit the team of ${application.name} and tick Takes part first`;
}

/** Why the team of an application is not in Agile Assessment & Adoption, and how an administrator brings it in. */
export function notTakingPartText(application: AgileApplication): string {
  return application.version === null
    ? `The team of ${application.name} does not take part in Agile Assessment & Adoption yet. An administrator sets it up in OneView Admin with the team, its way of working and its agile coach.`
    : `The team of ${application.name} no longer takes part in Agile Assessment & Adoption. An administrator brings it back in OneView Admin by editing its team and ticking Takes part.`;
}

/** A day of an assessment, for example 2 Nov 2026. */
export function dayText(day: string | null | undefined): string {
  return day ? formatDate(day, 'd MMM y', 'en-US') : '–';
}

/** The share of the highest level a level reaches, in percent, for a bar. */
export function levelShare(level: number | null | undefined, maxLevel: number): number {
  return typeof level === 'number' && maxLevel > 0
    ? Math.max(0, Math.min(100, (level / maxLevel) * 100))
    : 0;
}

/** Today in the time zone of the browser, as an assessment records it: 2026-11-23. */
export function today(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
