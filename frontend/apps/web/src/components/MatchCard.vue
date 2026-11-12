<script setup lang="ts">
import type { MatchSummary } from '@yunhefc/shared';
import { formatDate, formatDateTime, matchStatusText, matchTypeText } from '@yunhefc/shared';
import { Tag } from 'vant';

import { matchTagType, scoreText } from '../features/matches/presentation';

defineProps<{ match: MatchSummary }>();
</script>

<template>
  <article class="match-card">
    <div class="match-card__top">
      <span class="match-card__date">{{ formatDate(match.startAt) }}</span>
      <Tag :type="matchTagType(match.status)" plain>{{ matchStatusText(match.status) }}</Tag>
      <span class="muted small">第 {{ match.seasonMatchNo }} 场</span>
    </div>

    <div class="match-card__teams">
      <span>{{ match.homeTeamName }}</span>
      <strong>{{ scoreText(match) }}</strong>
      <span>{{ match.awayTeamName }}</span>
    </div>

    <div class="match-card__meta muted small">
      <span>{{ matchTypeText(match.type) }}</span>
      <span>{{ formatDateTime(match.startAt) }}</span>
      <span>{{ match.venue }}</span>
    </div>

    <div class="match-card__queue small">
      正式 {{ match.confirmedCount }} · 外挂 {{ match.guestCount }} · 候补
      {{ match.waitlistCount }}
    </div>
  </article>
</template>
