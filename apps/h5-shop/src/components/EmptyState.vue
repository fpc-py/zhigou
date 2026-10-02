<template>
  <div class="empty-state">
    <div class="empty-illustration">{{ illustration }}</div>
    <p class="empty-text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  illustration?: string;
  text?: string;
}>();
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}
.empty-illustration {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.4;
}
.empty-text {
  color: var(--ink-3);
  font-size: 14px;
}
</style>