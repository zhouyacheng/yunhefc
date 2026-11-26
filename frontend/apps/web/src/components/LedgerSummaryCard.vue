<script setup lang="ts">
import { formatMoney } from '@yunhefc/shared';
import { DatePicker, Popup } from 'vant';
import { ref } from 'vue';

import { TIME_FILTER_MAX_DATE, TIME_FILTER_MIN_DATE } from '../features/common/time-filter';

const props = defineProps<{
  month: string;
  monthLabel: string;
  customActive: boolean;
  expense: number;
  income: number;
  balance: number;
  filtered: boolean;
}>();
const emit = defineEmits<{ prev: []; next: []; pick: [month: string] }>();

const pickerOpen = ref(false);
const pickerValue = ref<string[]>(props.month.split('-'));

function openPicker(): void {
  pickerValue.value = props.month.split('-');
  pickerOpen.value = true;
}

function confirm({ selectedValues }: { selectedValues: string[] }): void {
  pickerOpen.value = false;
  emit('pick', `${selectedValues[0] ?? ''}-${(selectedValues[1] ?? '').padStart(2, '0')}`);
}
</script>

<template>
  <section class="ledger-hero" data-testid="ledger-summary">
    <div class="ledger-hero__month">
      <button
        type="button"
        class="ledger-hero__arrow"
        aria-label="上一个月"
        :disabled="customActive"
        @click="emit('prev')"
      >
        ‹
      </button>
      <button
        type="button"
        class="ledger-hero__month-label"
        aria-label="选择月份"
        @click="openPicker"
      >
        {{ monthLabel }}
        <span class="ledger-hero__caret" aria-hidden="true"></span>
      </button>
      <button
        type="button"
        class="ledger-hero__arrow"
        aria-label="下一个月"
        :disabled="customActive"
        @click="emit('next')"
      >
        ›
      </button>
      <span v-if="filtered" class="ledger-hero__scope">筛选合计</span>
    </div>

    <div class="ledger-hero__figures">
      <div class="ledger-figure">
        <span class="ledger-figure__label">支出(元)</span>
        <b class="ledger-figure__value ledger-figure__value--expense">
          {{ formatMoney(expense) }}
        </b>
      </div>
      <div class="ledger-figure ledger-figure--end">
        <span class="ledger-figure__label">收入(元)</span>
        <b class="ledger-figure__value ledger-figure__value--income">
          {{ formatMoney(income) }}
        </b>
      </div>
    </div>

    <p class="ledger-hero__balance">
      <span>{{ customActive ? '区间' : monthLabel }}结余</span>
      <b :class="{ 'is-negative': balance < 0 }">
        <span v-if="balance < 0" class="ledger-hero__sign">−</span>{{ formatMoney(Math.abs(balance)) }}
      </b>
    </p>

    <Popup v-model:show="pickerOpen" position="bottom" round>
      <DatePicker
        v-model="pickerValue"
        title="选择月份"
        :columns-type="['year', 'month']"
        :min-date="TIME_FILTER_MIN_DATE"
        :max-date="TIME_FILTER_MAX_DATE"
        @confirm="confirm"
      />
    </Popup>
  </section>
</template>

<style scoped>
.ledger-hero {
  margin-bottom: 14px;
  padding: 20px 22px;
  background:
    radial-gradient(circle at 100% 0%, rgba(37, 99, 235, 0.08), transparent 42%), var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius);
  box-shadow: var(--yh-shadow-sm);
}

.ledger-hero__month {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 18px;
}

.ledger-hero__month-label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 6px;
  color: var(--yh-ink);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.ledger-hero__month-label:hover {
  background: var(--yh-slate-soft);
}

.ledger-hero__caret {
  width: 7px;
  height: 7px;
  border-right: 2px solid var(--yh-muted);
  border-bottom: 2px solid var(--yh-muted);
  transform: rotate(45deg) translateY(-2px);
}

.ledger-hero__arrow {
  width: 30px;
  height: 30px;
  color: var(--yh-slate-ink);
  font-size: 18px;
  line-height: 1;
  background: var(--yh-slate-soft);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.ledger-hero__arrow:disabled {
  cursor: default;
  opacity: 0.35;
}

.ledger-hero__scope {
  margin-left: 6px;
  padding: 2px 8px;
  color: var(--yh-blue-ink);
  font-size: 11px;
  background: var(--yh-blue-soft);
  border-radius: 999px;
}

.ledger-hero__figures {
  display: grid;
  gap: 16px;
  grid-template-columns: 1fr 1fr;
}

.ledger-figure {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ledger-figure + .ledger-figure {
  padding-left: 16px;
  border-left: 1px solid var(--yh-line);
}

.ledger-figure--end {
  text-align: right;
}

.ledger-figure__label {
  color: var(--yh-muted);
  font-size: 12px;
}

.ledger-figure__value {
  font-size: clamp(26px, 4vw, 34px);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.ledger-hero__balance {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin: 18px 0 0;
  padding-top: 12px;
  color: var(--yh-muted);
  font-size: 12px;
  border-top: 1px dashed var(--yh-line);
}

.ledger-hero__balance b {
  color: var(--yh-slate-ink);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.ledger-hero__balance b.is-negative {
  color: var(--yh-red);
}

.ledger-hero__sign {
  font-size: 0.7em;
}

@media (max-width: 760px) {
  .ledger-hero {
    padding: 16px;
  }

  .ledger-hero__month-label {
    font-size: 18px;
  }
}
</style>
