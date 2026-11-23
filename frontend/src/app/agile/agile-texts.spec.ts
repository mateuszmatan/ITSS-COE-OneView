import { agileApplication, agileModel, notInAgile } from '../testing/fixtures';
import {
  agileParticipation,
  averageLevel,
  averageOf,
  dayText,
  frameworkLabel,
  latestAssessment,
  levelShare,
  levelText,
  today,
} from './agile-texts';

describe('agile texts', () => {
  it('writes levels to one decimal and their mean', () => {
    expect(levelText(3.2)).toBe('3.2');
    expect(levelText(4)).toBe('4.0');
    expect(levelText(null)).toBe('–');
    expect(averageLevel([3.2, 2.4, null, undefined])).toBe(2.8);
    expect(averageLevel([1, 2, 2])).toBe(1.7);
    expect(averageLevel([])).toBeNull();
  });

  it('averages one dimension over the latest assessment of every application', () => {
    const applications = [
      agileApplication(),
      agileApplication({ productId: 3, assessments: [] }),
      agileApplication({
        productId: 4,
        assessments: [agileApplication().assessments[1]],
      }),
    ];

    expect(latestAssessment(applications[0])?.id).toBe(11);
    expect(latestAssessment(applications[1])).toBeNull();
    expect(averageOf(applications, 'TEAM')).toBe(3.5);
    expect(averageOf(applications, 'PRODUCT')).toBe(2);
  });

  it('names the way of working, whether a team takes part and the day of an assessment', () => {
    const { frameworks } = agileModel();

    expect(frameworkLabel(frameworks, 'SAFE')).toBe('SAFe');
    expect(frameworkLabel(frameworks, null)).toBe('Not set');
    expect(agileParticipation(agileApplication())).toBe('Takes part');
    expect(agileParticipation(notInAgile())).toBe('Not set up');
    expect(agileParticipation(notInAgile({ version: 2 }))).toBe('Does not take part');
    expect(dayText('2026-11-02')).toBe('2 Nov 2026');
    expect(dayText(null)).toBe('–');
  });

  it('draws a level as a share of the highest level and knows today in the browser', () => {
    expect(levelShare(4, 5)).toBe(80);
    expect(levelShare(7, 5)).toBe(100);
    expect(levelShare(null, 5)).toBe(0);
    expect(today(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
