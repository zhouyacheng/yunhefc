<script setup lang="ts">
import type { MatchSummary } from '@yunhefc/shared';
import { formatDate, formatDateTime, matchStatusText, matchTypeText } from '@yunhefc/shared';
import { Tag } from 'vant';
import { computed } from 'vue';

import { resultTone, scoreText, statusTone, winnerSide } from '../features/matches/presentation';

const props = defineProps<{ match: MatchSummary }>();

const winner = computed(() => winnerSide(props.match));
</script>

<template>
  <article class="match-card">
    <div class="match-card__top">
      <span class="match-card__date">{{ formatDate(match.startAt) }}</span>
      <Tag
        v-if="match.resultText"
        class="status-tag"
        :class="`status-tag--${resultTone(match.resultText)}`"
      >
        {{ match.resultText }}
      </Tag>
      <span class="muted small">第 {{ match.seasonMatchNo }} 场</span>
    </div>

    <div class="match-card__teams">
      <span :class="{ 'team-name--winner': winner === 0 }">{{ match.homeTeamName }}</span>
      <strong>{{ scoreText(match) }}</strong>
      <span :class="{ 'team-name--winner': winner === 1 }">{{ match.awayTeamName }}</span>
    </div>

    <div class="match-card__meta muted small">
      <span>{{ matchTypeText(match.type) }}</span>
      <span>{{ formatDateTime(match.startAt) }}</span>
      <span>{{ match.venue }}</span>
    </div>

    <div class="match-card__foot">
      <Tag class="status-tag" :class="`status-tag--${statusTone(match.status)}`">
        {{ matchStatusText(match.status) }}
      </Tag>
      <span class="small">
        正式 {{ match.confirmedCount }} · 外挂 {{ match.guestCount }} · 候补
        {{ match.waitlistCount }}
      </span>
    </div>
  </article>
</template>
