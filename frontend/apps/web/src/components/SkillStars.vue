<script setup lang="ts">
import { skillAriaLabel, skillStarParts } from '@yunhefc/shared';
import { computed } from 'vue';

const STAR_PATH =
  'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z';

const props = defineProps<{ skill: number }>();

const parts = computed(() => skillStarParts(props.skill));
</script>

<template>
  <span class="skill-stars" role="img" :aria-label="skillAriaLabel(skill)">
    <svg
      v-for="(part, index) in parts"
      :key="index"
      class="skill-star"
      :class="`skill-star--${part}`"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <path v-if="part !== 'full'" class="skill-star__empty" :d="STAR_PATH" />
      <path
        v-if="part !== 'empty'"
        class="skill-star__fill"
        :class="{ 'skill-star__fill--half': part === 'half' }"
        :d="STAR_PATH"
      />
    </svg>
  </span>
</template>

<style scoped>
.skill-stars {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  line-height: 1;
}

.skill-star {
  display: block;
  flex: none;
  width: 1.12em;
  height: 1.12em;
}

.skill-star__empty {
  fill: #f8fafc;
  stroke: #cbd5e1;
  stroke-width: 1.5;
  stroke-linejoin: round;
}

.skill-star__fill {
  fill: var(--yh-gold);
  stroke: var(--yh-amber);
  stroke-width: 1.5;
  stroke-linejoin: round;
}

.skill-star__fill--half {
  clip-path: inset(0 50% 0 0);
}
</style>
