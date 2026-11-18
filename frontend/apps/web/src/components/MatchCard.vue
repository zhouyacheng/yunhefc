<script setup lang="ts">
import type { MatchSummary } from '@yunhefc/shared';
import {
  formatDayStartTime,
  matchStatusText,
  matchTypeText,
  seasonMatchText,
} from '@yunhefc/shared';
import { Tag } from 'vant';
import { computed } from 'vue';

import {
  resultTone,
  scoreText,
  statusTone,
  typeTone,
  winnerSide,
} from '../features/matches/presentation';

const props = defineProps<{ match: MatchSummary }>();

const winner = computed(() => winnerSide(props.match));

/** 底行三栏报名人数：正式 / 外挂 / 候补，各显示「已确认 / 名额」。 */
const queueStats = computed(() => [
  {
    key: 'formal',
    label: '正式',
    count: props.match.confirmedCount,
    capacity: props.match.capacity,
  },
  {
    key: 'guest',
    label: '外挂',
    count: props.match.guestCount,
    capacity: props.match.guestCapacity,
  },
  {
    key: 'waitlist',
    label: '候补',
    count: props.match.waitlistCount,
    capacity: props.match.waitlistCapacity,
  },
]);
</script>

<template>
  <article class="match-card">
    <div class="match-card__top">
      <span class="match-card__date">{{ formatDayStartTime(match.startAt) }}</span>
      <Tag
        v-if="match.resultText"
        class="status-tag"
        :class="`status-tag--${resultTone(match.resultText)}`"
      >
        {{ match.resultText }}
      </Tag>
      <span class="match-card__ordinal">{{ seasonMatchText(match) }}</span>
    </div>

    <div class="match-card__teams">
      <span :class="{ 'team-name--winner': winner === 0 }">{{ match.homeTeamName }}</span>
      <strong>{{ scoreText(match) }}</strong>
      <span :class="{ 'team-name--winner': winner === 1 }">{{ match.awayTeamName }}</span>
    </div>

    <div class="match-card__foot">
      <Tag class="match-type-tag" :class="`match-type-tag--${typeTone(match.type)}`">
        {{ matchTypeText(match.type) }}
      </Tag>
      <span class="small">{{ match.venue }}</span>
      <Tag class="status-tag" :class="`status-tag--${statusTone(match.status)}`">
        {{ matchStatusText(match.status) }}
      </Tag>
      <div
        v-for="stat in queueStats"
        :key="stat.key"
        class="match-card__stat"
        :class="`match-card__stat--${stat.key}`"
      >
        <span>{{ stat.label }}</span>
        <b>{{ stat.count }} / {{ stat.capacity }}</b>
      </div>
    </div>
  </article>
</template>
