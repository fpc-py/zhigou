<template>
  <div class="error-retry">
    <div class="error-icon">⚠</div>
    <p class="error-text">{{ text }}</p>
    <button class="retry-btn" @click="$emit('retry')">
      {{ btnText }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  text?: string;
  btnText?: string;
}>();

defineEmits<{
  retry: [];
}>();
</script>

<style scoped>
.error-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}
.error-icon {
  font-size: 48px;
  margin-bottom: 12px;
}
.error-text {
  color: var(--ink-2);
  font-size: 14px;
  margin-bottom: 20px;
}
.retry-btn {
  padding: 10px 28px;
  background: var(--brand);
  color: #fff;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
}
</style>