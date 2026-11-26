import { describeError } from '@yunhefc/api-client';
import type { LedgerStatistics } from '@yunhefc/shared';
import { formatAmount } from '@yunhefc/shared';
import { computed, ref, type Ref } from 'vue';

import { api } from '../../api/client';
import { formatLocalDate, toLocalDate } from '../common/time-filter';
import {
  ledgerTrendBars,
  statsBalanceText,
  statsCountText,
  statsDirectionLabel,
  statsTotal,
  type LedgerStatsDirection,
} from './presentation';
import { shiftStatsAnchor, toStatisticsRequest, type LedgerStatsPeriod } from './query';

export const STATS_PERIODS: ReadonlyArray<{ value: LedgerStatsPeriod; label: string }> = [
  { value: 'week', label: '按周' },
  { value: 'month', label: '按月' },
  { value: 'year', label: '按年' },
  { value: 'custom', label: '自定义时间' },
];

export const STATS_DIRECTIONS: ReadonlyArray<{ value: LedgerStatsDirection; label: string }> = [
  { value: 'expense', label: '支出' },
  { value: 'income', label: '收入' },
];

/**
 * C13 统计视图状态：周期/自定义区间/方向与 LED-14 读取。
 * 页面月份用于对齐默认口径；自定义周期不受页面月份影响。
 */
export function useLedgerStats(pageMonth: Ref<string>) {
  const statsPeriod = ref<LedgerStatsPeriod>('month');
  // 以月中为锚点：周口径落在本月内，月/年口径不受日影响。
  const statsAnchor = ref(`${pageMonth.value}-15`);
  const statsFrom = ref('');
  const statsTo = ref('');
  const statsDirection = ref<LedgerStatsDirection>('expense');
  const stats = ref<LedgerStatistics | null>(null);
  const statsLoading = ref(false);
  const statsError = ref('');
  const statsCalendarOpen = ref(false);
  let statsSeq = 0;

  const bars = computed(() =>
    stats.value
      ? ledgerTrendBars(stats.value.trend, stats.value.trendGranularity, statsDirection.value)
      : [],
  );
  const trendEmpty = computed(() => bars.value.every((bar) => bar.value === 0));
  /** 自定义周期尚未选区间时展示引导，而不是旧周期数据。 */
  const statsNeedsRange = computed(
    () => statsPeriod.value === 'custom' && (!statsFrom.value || !statsTo.value),
  );
  const statsTotalText = computed(() =>
    stats.value ? `¥${formatAmount(statsTotal(stats.value, statsDirection.value))}` : '—',
  );
  const statsCountLine = computed(() =>
    stats.value ? statsCountText(stats.value, statsDirection.value) : '',
  );
  const statsBalanceLine = computed(() => (stats.value ? statsBalanceText(stats.value) : ''));
  const statsCalendarDefault = computed<[Date, Date] | undefined>(() => {
    const start = toLocalDate(statsFrom.value);
    const end = toLocalDate(statsTo.value);
    return start && end ? [start, end] : undefined;
  });

  async function loadStats(): Promise<void> {
    if (statsNeedsRange.value) {
      return;
    }
    const seq = ++statsSeq;
    statsLoading.value = true;
    statsError.value = '';
    try {
      const view = await api.get<LedgerStatistics>('/ledger/statistics', {
        query: toStatisticsRequest({
          period: statsPeriod.value,
          anchor: statsAnchor.value,
          from: statsFrom.value,
          to: statsTo.value,
        }),
      });
      if (seq !== statsSeq) {
        return;
      }
      stats.value = view;
    } catch (caught) {
      if (seq !== statsSeq) {
        return;
      }
      stats.value = null;
      statsError.value = describeError(caught, '统计加载失败，请稍后重试');
    } finally {
      if (seq === statsSeq) {
        statsLoading.value = false;
      }
    }
  }

  /** 进入统计时按页面月份对齐周/月/年口径，再取数。 */
  function enter(): void {
    if (statsNeedsRange.value) {
      return;
    }
    if (statsPeriod.value !== 'custom') {
      statsAnchor.value = `${pageMonth.value}-15`;
    }
    void loadStats();
  }

  function retry(): void {
    void loadStats();
  }

  function openStatsCalendar(): void {
    statsCalendarOpen.value = true;
  }

  function selectPeriod(period: LedgerStatsPeriod): void {
    if (period === statsPeriod.value) {
      if (period === 'custom') {
        openStatsCalendar();
      }
      return;
    }
    statsPeriod.value = period;
    if (period === 'custom') {
      if (statsFrom.value && statsTo.value) {
        void loadStats();
      } else {
        openStatsCalendar();
      }
      return;
    }
    void loadStats();
  }

  function shiftStats(delta: number): void {
    if (statsPeriod.value === 'custom') {
      return;
    }
    statsAnchor.value = shiftStatsAnchor(statsAnchor.value, statsPeriod.value, delta);
    void loadStats();
  }

  function onPeriodLabelClick(): void {
    if (statsPeriod.value === 'custom') {
      openStatsCalendar();
    }
  }

  function confirmStatsRange(dates: Date[]): void {
    const [start, end] = dates;
    if (!start || !end) {
      return;
    }
    statsFrom.value = formatLocalDate(start);
    statsTo.value = formatLocalDate(end);
    statsCalendarOpen.value = false;
    void loadStats();
  }

  function setDirection(direction: LedgerStatsDirection): void {
    statsDirection.value = direction;
  }

  return {
    statsPeriod,
    statsDirection,
    stats,
    statsLoading,
    statsError,
    statsCalendarOpen,
    statsCalendarDefault,
    bars,
    trendEmpty,
    statsNeedsRange,
    statsTotalText,
    statsCountLine,
    statsBalanceLine,
    statsDirectionLabel: computed(() => statsDirectionLabel(statsDirection.value)),
    enter,
    retry,
    selectPeriod,
    shiftStats,
    onPeriodLabelClick,
    confirmStatsRange,
    setDirection,
  };
}
