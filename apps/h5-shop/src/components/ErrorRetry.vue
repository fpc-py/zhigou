<template>
  <div class="error-retry anim-in">
    <div class="error-ring"><span class="error-icon anim-float">😵</span></div>
    <p class="error-title">{{ title || '网络开了个小差' }}</p>
    <p class="error-text">{{ text || '请检查网络后重试' }}</p>
    <button class="retry-btn btn-brand press" @click="$emit('retry')">
      {{ btnText || '重试' }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  text?: string;
  btnText?: string;
  title?: string;
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
  padding: 48px 24px;
  text-align: center;
}
.error-ring {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--danger-soft), transparent 72%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.error-icon {
  font-size: 44px;
  opacity: 0.9;
}
.error-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
  margin-top: 14px;
}
.error-text {
  color: var(--ink-3);
  font-size: 12.5px;
  margin-top: 8px;
}
.retry-btn {
  margin-top: 18px;
  padding: 11px 28px;
  font-size: 13.5px;
  font-weight: 700;
  border-radius: 999px;
}
</style>
