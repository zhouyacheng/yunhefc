<script setup lang="ts">
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from '@yunhefc/shared';
import { Button } from 'vant';
import { computed } from 'vue';

import type { TimeForm } from '../features/common/time-filter';
import type { ScheduleForm } from '../features/schedule/query';
import MonthRangeFilter from './MonthRangeFilter.vue';

const emit = defineEmits<{ search: []; reset: [] }>();
const form = defineModel<ScheduleForm>({ required: true });

const typeOptions = Object.entries(MATCH_TYPE_TEXT);
const statusOptions = Object.entries(MATCH_STATUS_TEXT);

/** 时间筛选以子组件所需形状读写表单草稿。 */
const time = computed<TimeForm>({
  get: () => ({
    month: form.value.month,
    startDate: form.value.startDate,
    endDate: form.value.endDate,
  }),
  set: (value) => {
    form.value = { ...form.value, ...value };
  },
});

/** 回车立即生效；其余条件变更由页面监听草稿自动触发。 */
function search(): void {
  emit('search');
}

function reset(): void {
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
        @keydown.enter.prevent="search"
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

    <MonthRangeFilter v-model="time" />

    <div class="schedule-filter-actions">
      <Button size="small" native-type="button" plain @click="reset">重置</Button>
    </div>
  </form>
</template>
