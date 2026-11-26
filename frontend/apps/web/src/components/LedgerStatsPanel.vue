<script setup lang="ts">
import { formatMoney } from '@yunhefc/shared';
import { Button, Calendar, Empty, Loading } from 'vant';
import { onMounted, toRef, watch } from 'vue';

import { TIME_FILTER_MAX_DATE, TIME_FILTER_MIN_DATE } from '../features/common/time-filter';
import { ledgerPeriodLabel } from '../features/ledger/presentation';
import { STATS_DIRECTIONS, STATS_PERIODS, useLedgerStats } from '../features/ledger/useLedgerStats';

const props = defineProps<{ active: boolean; pageMonth: string }>();

const {
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
  statsDirectionLabel,
  enter,
  retry,
  selectPeriod,
  shiftStats,
  onPeriodLabelClick,
  confirmStatsRange,
  setDirection,
} = useLedgerStats(toRef(props, 'pageMonth'));

function trendTitle(index: number): string {
  const bar = bars.value[index];
  return bar ? `${bar.label} ${formatMoney(bar.value)}` : '';
}

watch(
  () => props.active,
  (active) => {
    if (active) {
      enter();
    }
  },
);

// 页面月份变化时，统计的周/月/年口径同步到该月。
watch(
  () => props.pageMonth,
  () => {
    if (props.active) {
      enter();
    }
  },
);

onMounted(() => {
  if (props.active) {
    enter();
  }
});
</script>

<template>
  <div>
    <div class="stats-periods" role="tablist" aria-label="统计周期">
      <button
        v-for="item in STATS_PERIODS"
        :key="item.value"
        type="button"
        role="tab"
        class="stats-periods__item"
        :class="{ 'is-active': statsPeriod === item.value }"
        :aria-selected="statsPeriod === item.value"
        @click="selectPeriod(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-if="statsNeedsRange" class="state">
      <Empty description="请选择自定义统计时间" />
      <Button size="small" type="primary" @click="statsCalendarOpen = true">选择时间</Button>
    </div>

    <div v-else-if="statsLoading && !stats" class="state">
      <Loading size="24">统计加载中…</Loading>
    </div>

    <div v-else-if="statsError && !stats" class="state">
      <p class="muted">{{ statsError }}</p>
      <Button size="small" type="primary" @click="retry">重试</Button>
    </div>

    <template v-else-if="stats">
      <section class="stats-card card" data-testid="ledger-stats">
        <div class="stats-nav">
          <button
            type="button"
            class="stats-nav__arrow"
            aria-label="上一周期"
            :disabled="statsPeriod === 'custom'"
            @click="shiftStats(-1)"
          >
            ◀
          </button>
          <button
            type="button"
            class="stats-nav__label"
            aria-label="统计周期"
            @click="onPeriodLabelClick"
          >
            {{ ledgerPeriodLabel(stats) }}
          </button>
          <button
            type="button"
            class="stats-nav__arrow"
            aria-label="下一周期"
            :disabled="statsPeriod === 'custom'"
            @click="shiftStats(1)"
          >
            ▶
          </button>
          <div class="stats-direction" role="group" aria-label="收支方向">
            <button
              v-for="item in STATS_DIRECTIONS"
              :key="item.value"
              type="button"
              class="stats-direction__item"
              :class="{ 'is-active': statsDirection === item.value }"
              :aria-pressed="statsDirection === item.value"
              @click="setDirection(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <p class="stats-total">{{ statsTotalText }}</p>
        <p class="stats-line">{{ statsCountLine }}</p>
        <p class="stats-line">{{ statsBalanceLine }}</p>
      </section>

      <section class="stats-trend card" data-testid="ledger-trend">
        <header class="stats-trend__head">
          <h2>收支趋势</h2>
          <span>{{ statsDirectionLabel }}</span>
        </header>
        <div v-if="!trendEmpty" class="trend">
          <div
            v-for="(bar, index) in bars"
            :key="bar.start"
            class="trend__col"
            :title="trendTitle(index)"
          >
            <div class="trend__track">
              <i v-if="bar.value > 0" class="trend__bar" :style="{ height: `${bar.percent}%` }"></i>
            </div>
            <span v-if="bar.labelVisible" class="trend__label">{{ bar.label }}</span>
          </div>
        </div>
        <Empty v-else description="该周期暂无流水" />
      </section>
    </template>

    <Calendar
      v-model:show="statsCalendarOpen"
      type="range"
      allow-same-day
      switch-mode="year-month"
      :min-date="TIME_FILTER_MIN_DATE"
      :max-date="TIME_FILTER_MAX_DATE"
      :default-date="statsCalendarDefault"
      title="选择统计时间"
      @confirm="confirmStatsRange"
    />
  </div>
</template>

<style scoped>
.stats-periods {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.stats-periods__item {
  padding: 7px 16px;
  color: var(--yh-slate-ink);
  font-size: 13px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 999px;
  cursor: pointer;
}

.stats-periods__item.is-active {
  color: var(--yh-blue-ink);
  background: var(--yh-blue-soft);
  border-color: transparent;
}

.stats-card {
  margin-bottom: 14px;
  padding: 18px 22px 20px;
}

.stats-nav {
  display: flex;
  gap: 8px;
  align-items: center;
}

.stats-nav__arrow {
  padding: 4px;
  color: var(--yh-muted);
  font-size: 11px;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.stats-nav__arrow:disabled {
  cursor: default;
  opacity: 0.3;
}

.stats-nav__label {
  color: var(--yh-ink);
  font-size: 15px;
  font-weight: 600;
  background: transparent;
  border: 0;
  cursor: default;
}

.stats-direction {
  display: flex;
  gap: 2px;
  margin-left: auto;
  padding: 3px;
  background: var(--yh-slate-soft);
  border-radius: 999px;
}

.stats-direction__item {
  padding: 5px 16px;
  color: var(--yh-muted);
  font-size: 13px;
  background: transparent;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
}

.stats-direction__item.is-active {
  color: var(--yh-blue-ink);
  font-weight: 600;
  background: var(--yh-surface);
  box-shadow: var(--yh-shadow-sm);
}

.stats-total {
  margin: 18px 0 14px;
  color: var(--yh-blue-ink);
  font-size: clamp(30px, 5vw, 40px);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.stats-line {
  margin: 0;
  color: var(--yh-muted);
  font-size: 12px;
  line-height: 1.7;
}

.stats-line + .stats-line {
  margin-top: 2px;
}

.stats-trend {
  padding: 18px 22px 16px;
}

.stats-trend__head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.stats-trend__head h2 {
  margin: 0;
  font-size: 15px;
}

.stats-trend__head span {
  color: var(--yh-muted);
  font-size: 12px;
}

.trend {
  display: flex;
  gap: 3px;
  align-items: flex-end;
  height: 150px;
  padding-top: 8px;
}

.trend__col {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  justify-content: flex-end;
  min-width: 0;
  height: 100%;
}

.trend__track {
  display: flex;
  align-items: flex-end;
  height: 110px;
  background: linear-gradient(to top, var(--yh-surface-soft), transparent);
  border-radius: 4px;
}

.trend__bar {
  display: block;
  width: 100%;
  min-height: 2px;
  background: linear-gradient(to top, var(--yh-blue), #60a5fa);
  border-radius: 4px 4px 0 0;
}

.trend__label {
  overflow: hidden;
  color: var(--yh-muted);
  font-size: 10px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .stats-card,
  .stats-trend {
    padding: 16px;
  }
}
</style>
