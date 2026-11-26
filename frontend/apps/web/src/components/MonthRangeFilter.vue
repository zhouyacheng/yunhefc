<script setup lang="ts">
import { Calendar, DatePicker, Popup } from 'vant';
import { computed, ref } from 'vue';

import {
  TIME_FILTER_MAX_DATE,
  TIME_FILTER_MIN_DATE,
  currentShanghaiMonth,
  formatLocalDate,
  toLocalDate,
} from '../features/common/time-filter';
import type { TimeForm } from '../features/common/time-filter';

/** 时间筛选控件：月份与自定义日期范围互斥；变更后由父组件决定是否立即查询。 */
const props = withDefaults(defineProps<{ showMonth?: boolean }>(), { showMonth: true });
const emit = defineEmits<{ change: [] }>();
const time = defineModel<TimeForm>({ required: true });

const monthPickerOpen = ref(false);
const calendarOpen = ref(false);
const monthPickerValue = ref<string[]>(currentShanghaiMonth().split('-'));

const rangeText = computed(() =>
  time.value.startDate && time.value.endDate
    ? `${time.value.startDate} 至 ${time.value.endDate}`
    : '',
);

const calendarDefaultDate = computed<Date | [Date, Date] | undefined>(() => {
  const start = toLocalDate(time.value.startDate);
  const end = toLocalDate(time.value.endDate);
  return start && end ? [start, end] : undefined;
});

function openMonthPicker(): void {
  const [year = '', month = ''] = (time.value.month || currentShanghaiMonth()).split('-');
  monthPickerValue.value = [year, month.padStart(2, '0')];
  monthPickerOpen.value = true;
  calendarOpen.value = false;
}

function confirmMonth({ selectedValues }: { selectedValues: string[] }): void {
  time.value = {
    month: `${selectedValues[0] ?? ''}-${(selectedValues[1] ?? '').padStart(2, '0')}`,
    startDate: '',
    endDate: '',
  };
  monthPickerOpen.value = false;
  emit('change');
}

function clearMonth(): void {
  time.value = { ...time.value, month: '' };
  emit('change');
}

function openCalendar(): void {
  calendarOpen.value = true;
  monthPickerOpen.value = false;
}

function confirmRange(dates: Date[]): void {
  const [start, end] = dates;
  if (!start || !end) {
    return;
  }
  time.value = {
    month: '',
    startDate: formatLocalDate(start),
    endDate: formatLocalDate(end),
  };
  calendarOpen.value = false;
  emit('change');
}

function clearRange(): void {
  time.value = { ...time.value, startDate: '', endDate: '' };
  emit('change');
}
</script>

<template>
  <div v-if="props.showMonth" class="schedule-filter">
    <span>月份</span>
    <div class="schedule-range">
      <button
        type="button"
        class="schedule-range__trigger"
        aria-label="选择月份"
        @click="openMonthPicker"
      >
        {{ time.month || '全部月份' }}
      </button>
      <button
        v-if="time.month"
        type="button"
        class="schedule-range__clear"
        aria-label="清除月份"
        @click="clearMonth"
      >
        清除
      </button>
    </div>
  </div>

  <div class="schedule-filter">
    <span>自定义时间</span>
    <div class="schedule-range">
      <button
        type="button"
        class="schedule-range__trigger"
        aria-label="选择自定义时间"
        @click="openCalendar"
      >
        {{ rangeText || '选择日期范围' }}
      </button>
      <button
        v-if="rangeText"
        type="button"
        class="schedule-range__clear"
        aria-label="清除自定义时间"
        @click="clearRange"
      >
        清除
      </button>
    </div>
  </div>

  <Calendar
    v-model:show="calendarOpen"
    type="range"
    allow-same-day
    switch-mode="year-month"
    :min-date="TIME_FILTER_MIN_DATE"
    :max-date="TIME_FILTER_MAX_DATE"
    :default-date="calendarDefaultDate"
    title="选择时间范围"
    @confirm="confirmRange"
  />

  <Popup v-model:show="monthPickerOpen" position="bottom" round>
    <DatePicker
      v-model="monthPickerValue"
      title="选择月份"
      :columns-type="['year', 'month']"
      :min-date="TIME_FILTER_MIN_DATE"
      :max-date="TIME_FILTER_MAX_DATE"
      @confirm="confirmMonth"
    />
  </Popup>
</template>
