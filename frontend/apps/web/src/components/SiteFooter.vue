<script setup lang="ts">
import beianUrl from '../assets/beian.png';

const START_YEAR = 2026;
const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="site-footer">
    <p class="site-footer__copyright">
      © {{ START_YEAR }}-{{ currentYear }} 云河FC All Rights Reserved
    </p>
    <p class="site-footer__beian">
      <a
        href="https://beian.mps.gov.cn/#/query/webSearch?code=32028102004219"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img :src="beianUrl" alt="" />
        苏公网安备32028102004219号
      </a>
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
        苏ICP备2026090382号
      </a>
    </p>
  </footer>
</template>
