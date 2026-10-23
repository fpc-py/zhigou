<template>
  <div class="empty-state anim-in" :class="{ small }">
    <div class="empty-ring"><span class="empty-art anim-float">{{ illustration || icon }}</span></div>
    <p class="empty-title">{{ title || text || '这里还空着' }}</p>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>
    <button v-if="actionText" class="empty-cta press" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 兼容旧调用：illustration 或 icon 均为插画字符 */
  illustration?: string;
  icon?: string;
  text?: string;
  title?: string;
  desc?: string;
  actionText?: string;
  /** 兼容旧调用：紧凑模式 */
  small?: boolean;
}>();

defineEmits<{
  action: [];
}>();
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}
.empty-state.small { padding: 28px 16px; }
.empty-ring {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--brand-soft), transparent 72%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.empty-state.small .empty-ring { width: 68px; height: 68px; }
.empty-art {
  font-size: 46px;
  opacity: 0.9;
}
.empty-state.small .empty-art { font-size: 34px; }
.empty-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
  margin-top: 14px;
}
.empty-state.small .empty-title { font-size: 14px; margin-top: 10px; }
.empty-desc {
  font-size: 12.5px;
  color: var(--ink-3);
  line-height: 1.7;
  margin-top: 8px;
  max-width: 240px;
}
.empty-state.small .empty-desc { font-size: 11.5px; margin-top: 6px; }
.empty-cta {
  margin-top: 18px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  padding: 11px 24px;
  border-radius: 999px;
  box-shadow: 0 10px 22px rgba(76, 92, 255, 0.32);
}
</style>
