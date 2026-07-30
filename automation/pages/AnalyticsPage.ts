import { Locator, Page } from '@playwright/test';
import { BasePage } from './BasePage';
import { Routes } from '../constants/routes';

/** Maps to frontend/analytics.html - read-only per-link breakdown (frontend/js/analytics.js). */
export class AnalyticsPage extends BasePage {
  readonly shortCodeLabel: Locator;
  readonly totalClicks: Locator;
  readonly uniqueVisitors: Locator;
  readonly dailyClicksChart: Locator;
  readonly browsersChart: Locator;
  readonly osChart: Locator;
  readonly deviceChart: Locator;
  readonly countryChart: Locator;
  readonly referrerChart: Locator;
  readonly backToLinksLink: Locator;

  constructor(page: Page) {
    super(page);
    this.shortCodeLabel = page.locator('#shortCodeLabel');
    this.totalClicks = page.locator('#totalClicks');
    this.uniqueVisitors = page.locator('#uniqueVisitors');
    this.dailyClicksChart = page.locator('#dailyClicksChart');
    this.browsersChart = page.locator('#browsersChart');
    this.osChart = page.locator('#osChart');
    this.deviceChart = page.locator('#deviceChart');
    this.countryChart = page.locator('#countryChart');
    this.referrerChart = page.locator('#referrerChart');
    this.backToLinksLink = page.getByRole('link', { name: /Back to links/ });
  }

  async open(shortCode: string): Promise<void> {
    await this.goto(Routes.analytics(shortCode));
  }

  /** Each bar-chart container renders one .bar-row per entry, or a "No data yet" placeholder. */
  barRowsIn(chart: Locator): Locator {
    return chart.locator('.bar-row');
  }

  async hasNoData(chart: Locator): Promise<boolean> {
    return chart.getByText('No data yet').isVisible();
  }
}
