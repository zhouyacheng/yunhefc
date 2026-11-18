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
    </div>
  </article>
</template>
