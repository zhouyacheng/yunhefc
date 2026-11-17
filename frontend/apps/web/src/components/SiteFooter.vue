<script setup lang="ts">
const START_YEAR = 2026;
const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="site-footer">
    <p class="site-footer__copyright">
      © {{ START_YEAR }}-{{ currentYear }} 云河FC All Rights Reserved
    </p>
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
      苏ICP备2026090382号
    </a>
  </footer>
</template>
