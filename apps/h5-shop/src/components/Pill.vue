<template>
  <span class="pill" :class="typeClass"><slot /></span>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{ type?: 'brand' | 'accent' | 'mint' | 'amber' | 'line' }>(), {
  type: 'brand',
});

const typeClass = computed(() => {
  const map: Record<string, string> = {
    brand: 'pill-brand',
    accent: 'pill-accent',
    mint: 'pill-mint',
    amber: 'pill-amber',
    line: 'pill-line',
  };
  return map[props.type] ?? 'pill-brand';
});
</script>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 9px;
  border-radius: 999px;
}
.pill-brand { background: var(--brand-soft); color: var(--brand); }
.pill-accent { background: var(--accent-soft); color: var(--accent); }
.pill-mint { background: var(--mint-soft); color: var(--mint); }
.pill-amber { background: var(--amber-soft); color: #b87900; }
.pill-line { background: #fff; border: 1px solid var(--line-2); color: var(--ink-2); }
</style>
