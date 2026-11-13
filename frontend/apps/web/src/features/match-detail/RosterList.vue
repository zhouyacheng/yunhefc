<script setup lang="ts">
import type { RosterEntry, RosterSections } from './roster';
import { ATTENDANCE_SOURCE_TEXT, ATTENDANCE_STATUS_TEXT, positionText } from '@yunhefc/shared';
import { Button, Loading } from 'vant';
import { computed } from 'vue';

const props = defineProps<{
  sections: RosterSections;
  loading: boolean;
  error: string;
  attendanceError: string;
  adminUrl: string | null;
}>();

const emit = defineEmits<{
  retry: [];
  adminClick: [];
}>();

interface RosterGroupView {
  key: string;
  title: string;
  entries: RosterEntry[];
  empty: string;
  testid: string;
}

const groups = computed<RosterGroupView[]>(() => [
  {
    key: 'formal',
    title: '正式球员',
    entries: props.sections.formalConfirmed,
    empty: '暂无正式球员报名',
    testid: 'roster-formal',
  },
  {
    key: 'waitlist',
    title: '候补（正式）',
    entries: props.sections.formalWaitlist,
    empty: '暂无候补',
    testid: 'roster-waitlist',
  },
  {
    key: 'guest',
    title: '外挂球员',
    entries: props.sections.guestConfirmed,
    empty: '暂无外挂球员报名',
    testid: 'roster-guest',
  },
]);

function jerseyText(entry: RosterEntry): string {
  const jersey = entry.row.player.jersey;
  return jersey === null || jersey === undefined ? '—' : `#${jersey}`;
}

function attendanceText(entry: RosterEntry): string {
  if (!entry.attendance) {
    return '未记录';
  }
  const status = ATTENDANCE_STATUS_TEXT[entry.attendance.status] ?? entry.attendance.status;
  const source = ATTENDANCE_SOURCE_TEXT[entry.attendance.source] ?? entry.attendance.source;
  return `${status} · ${source}`;
}
</script>

<template>
  <section class="card roster-card">
    <div class="card-head">
      <h2>名单与考勤</h2>
      <span class="muted small">{{ sections.total }} 人</span>
    </div>

    <div class="roster-toolbar">
      <span class="muted small">按报名时间排序</span>
      <a
        v-if="adminUrl && !loading && !error"
        class="ghost-link"
        data-testid="roster-admin-link"
        :href="adminUrl"
        @mousedown="emit('adminClick')"
        @click="emit('adminClick')"
      >
        去 B03 管理报名
      </a>
    </div>

    <div v-if="loading" class="state state--inline">
      <Loading size="20">名单加载中…</Loading>
    </div>

    <div v-else-if="error" class="state state--inline">
      <p class="muted">{{ error }}</p>
      <Button size="small" @click="emit('retry')">重试</Button>
    </div>

    <template v-else>
      <p v-if="attendanceError" class="roster-warning">{{ attendanceError }}</p>

      <div
        v-for="group in groups"
        :key="group.key"
        class="roster-section"
        :class="`roster-section--${group.key}`"
        :data-testid="group.testid"
      >
        <div class="roster-section__head">
          <strong>{{ group.title }}</strong>
          <span class="muted small">{{ group.entries.length }} 人</span>
        </div>

        <div
          v-for="entry in group.entries"
          :key="entry.row.player.id"
          class="roster-row"
          data-testid="roster-row"
          :data-player-id="entry.row.player.id"
        >
          <div class="roster-row__main">
            <strong>
              {{ entry.row.player.name }}
              <small class="muted">{{ jerseyText(entry) }}</small>
            </strong>
            <span class="muted small">{{ positionText(entry.row.player.position) }}</span>
          </div>
          <span v-if="entry.waitlistOrder" class="roster-order">
            候补第 {{ entry.waitlistOrder }} 顺位
          </span>
          <span class="roster-attendance" data-testid="roster-attendance">
            {{ attendanceText(entry) }}
          </span>
        </div>

        <p v-if="!group.entries.length" class="muted small">{{ group.empty }}</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.roster-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.roster-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.roster-warning {
  margin: 0;
  padding: 8px 10px;
  color: #92400e;
  font-size: 13px;
  background: #fef3c7;
  border-radius: 10px;
}

.roster-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--yh-line);
}

.roster-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.roster-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  background: #f8fafc;
  border-radius: 10px;
}

.roster-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.roster-row__main strong {
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.roster-order {
  color: #b45309;
  font-size: 12px;
  white-space: nowrap;
}

.roster-attendance {
  color: var(--yh-muted);
  font-size: 12px;
  text-align: right;
}
</style>
