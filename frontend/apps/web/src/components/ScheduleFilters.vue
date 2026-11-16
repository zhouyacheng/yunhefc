<script setup lang="ts">
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from '@yunhefc/shared';
import { Button, Calendar, DatePicker, Popup } from 'vant';
import { computed, ref } from 'vue';

import { currentShanghaiMonth } from '../features/schedule/query';
import type { ScheduleForm } from '../features/schedule/query';

const emit = defineEmits<{ search: []; reset: [] }>();
const form = defineModel<ScheduleForm>({ required: true });

const typeOptions = Object.entries(MATCH_TYPE_TEXT);
const statusOptions = Object.entries(MATCH_STATUS_TEXT);

const RANGE_MIN_DATE = new Date(2000, 0, 1);
const RANGE_MAX_DATE = new Date(2100, 11, 31);

const monthPickerOpen = ref(false);
const calendarOpen = ref(false);
const monthPickerValue = ref<string[]>(currentShanghaiMonth().split('-'));

const rangeText = computed(() =>
  form.value.startDate && form.value.endDate
    ? `${form.value.startDate} 至 ${form.value.endDate}`
    : '',
);

const calendarDefaultDate = computed<Date | [Date, Date] | undefined>(() => {
  const start = toDate(form.value.startDate);
  const end = toDate(form.value.endDate);
  return start && end ? [start, end] : undefined;
});

function toDate(value: string): Date | null {
  if (!value) {
    return null;
  }
  const [year = 0, month = 1, day = 1] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

// Calendar 返回本地日历日，按本地 getter 格式化，避免 toISOString 在 UTC+8 少一天。
function formatDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function openMonthPicker(): void {
  const [year = '', month = ''] = (form.value.month || currentShanghaiMonth()).split('-');
  monthPickerValue.value = [year, month.padStart(2, '0')];
  monthPickerOpen.value = true;
  calendarOpen.value = false;
}

function confirmMonth({ selectedValues }: { selectedValues: string[] }): void {
  form.value = {
    ...form.value,
    month: `${selectedValues[0] ?? ''}-${(selectedValues[1] ?? '').padStart(2, '0')}`,
    startDate: '',
    endDate: '',
  };
  monthPickerOpen.value = false;
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
  form.value = {
    ...form.value,
    month: '',
    startDate: formatDate(start),
    endDate: formatDate(end),
  };
  calendarOpen.value = false;
}

function clearMonth(): void {
  form.value = { ...form.value, month: '' };
}

function clearRange(): void {
  form.value = { ...form.value, startDate: '', endDate: '' };
}

function search(): void {
  monthPickerOpen.value = false;
  calendarOpen.value = false;
  emit('search');
}

function reset(): void {
  monthPickerOpen.value = false;
  calendarOpen.value = false;
  emit('reset');
}
</script>

<template>
  <form class="schedule-filters" @submit.prevent="search">
    <label class="schedule-filter">
      <span>类型</span>
      <select v-model="form.type" name="type" aria-label="比赛类型">
        <option value="">全部类型</option>
        <option v-for="[value, text] in typeOptions" :key="value" :value="value">
          {{ text }}
        </option>
      </select>
    </label>

    <label class="schedule-filter">
      <span>地点</span>
      <input
        v-model="form.venue"
        type="text"
        name="venue"
        aria-label="地点"
        placeholder="如：聚龙"
      />
    </label>

    <label class="schedule-filter">
      <span>状态</span>
      <select v-model="form.status" name="status" aria-label="比赛状态">
        <option value="">全部状态</option>
        <option v-for="[value, text] in statusOptions" :key="value" :value="value">
          {{ text }}
        </option>
      </select>
    </label>

    <div class="schedule-filter">
      <span>月份</span>
      <div class="schedule-range">
        <button
          type="button"
          class="schedule-range__trigger"
          aria-label="选择月份"
          @click="openMonthPicker"
        >
          {{ form.month || '全部月份' }}
        </button>
        <button
          v-if="form.month"
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

    <div class="schedule-filter-actions">
      <Button size="small" type="primary" native-type="submit">搜索</Button>
      <Button size="small" native-type="button" plain @click="reset">重置</Button>
    </div>
  </form>

  <Calendar
    v-model:show="calendarOpen"
    type="range"
    allow-same-day
    switch-mode="year-month"
    :min-date="RANGE_MIN_DATE"
    :max-date="RANGE_MAX_DATE"
    :default-date="calendarDefaultDate"
    title="选择时间范围"
    @confirm="confirmRange"
  />

  <Popup v-model:show="monthPickerOpen" position="bottom" round>
    <DatePicker
      v-model="monthPickerValue"
      title="选择月份"
      :columns-type="['year', 'month']"
      :min-date="RANGE_MIN_DATE"
      :max-date="RANGE_MAX_DATE"
      @confirm="confirmMonth"
    />
  </Popup>
</template>
