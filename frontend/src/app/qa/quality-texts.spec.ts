import { notInQuality, qualityApplication } from '../testing/fixtures';
import {
  TARGET_FIELDS,
  ownTargetsSummary,
  participation,
  targetText,
  targetsSummary,
} from './quality-texts';

describe('quality texts', () => {
  it('names every target and the range the portal accepts for it', () => {
    expect(TARGET_FIELDS.map((field) => [field.key, field.label, field.min, field.max])).toEqual([
      ['coveragePercent', 'Code coverage', 0, 100],
      ['passRatePercent', 'Test pass rate', 0, 100],
      ['maxCriticalDefects', 'Open critical defects', 0, 999_999],
    ]);
  });

  it('writes the targets in words', () => {
    const [coverage, , defects] = TARGET_FIELDS;

    expect(targetText(coverage, 80)).toBe('at least 80%');
    expect(targetText(defects, 0)).toBe('at most 0');
    expect(targetText(coverage, null)).toBe('not set');
    expect(
      targetsSummary({ coveragePercent: 80, passRatePercent: 95, maxCriticalDefects: 0 }),
    ).toBe(
      'Code coverage at least 80%, test pass rate at least 95%, open critical defects at most 0',
    );
  });

  it('names only the targets an application sets for itself', () => {
    expect(
      ownTargetsSummary({ coveragePercent: 85, passRatePercent: null, maxCriticalDefects: 2 }),
    ).toBe('Code coverage at least 85%, open critical defects at most 2');
    expect(
      ownTargetsSummary({ coveragePercent: null, passRatePercent: null, maxCriticalDefects: null }),
    ).toBe('The BBH targets');
  });

  it('tells an application that takes part from one that was never set up or left', () => {
    expect(participation(qualityApplication())).toBe('Takes part');
    expect(participation(notInQuality())).toBe('Not set up');
    expect(participation(notInQuality({ version: 3 }))).toBe('Does not take part');
  });
});
