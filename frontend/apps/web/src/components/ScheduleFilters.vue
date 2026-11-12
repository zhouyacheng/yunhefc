<script setup lang="ts">
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from '@yunhefc/shared';
import { Button } from 'vant';

import type { ScheduleForm } from '../features/schedule/query';

defineProps<{ yearOptions: string[] }>();
const emit = defineEmits<{ search: []; reset: [] }>();
const form = defineModel<ScheduleForm>({ required: true });

const typeOptions = Object.entries(MATCH_TYPE_TEXT);
const statusOptions = Object.entries(MATCH_STATUS_TEXT);

function onYearChange(event: Event): void {
  form.value = { ...form.value, season: (event.target as HTMLSelectElement).value, month: '' };
}

function onMonthChange(event: Event): void {
  form.value = { ...form.value, month: (event.target as HTMLInputElement).value, season: '' };
}
</script>

<template>
  <form class="schedule-filters" @submit.prevent="emit('search')">
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

    <label class="schedule-filter">
      <span>年份</span>
      <select
        :value="form.season"
        name="season"
        aria-label="年份"
        :disabled="Boolean(form.month)"
        @change="onYearChange"
      >
        <option value="">全部年份</option>
        <option v-for="year in yearOptions" :key="year" :value="year">{{ year }} 年</option>
      </select>
    </label>

    <label class="schedule-filter">
      <span>月份</span>
      <input
        :value="form.month"
        type="month"
        name="month"
        aria-label="月份"
        :disabled="Boolean(form.season)"
        @change="onMonthChange"
      />
    </label>

    <div class="schedule-filter-actions">
      <Button size="small" type="primary" native-type="submit">搜索</Button>
      <Button size="small" native-type="button" plain @click="emit('reset')">重置</Button>
    </div>
  </form>
</template>
