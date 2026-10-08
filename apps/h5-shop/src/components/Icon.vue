<template>
  <svg class="ic" :class="[sizeClass]" viewBox="0 0 24 24" :style="{ stroke: 'currentColor' }" fill="none" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <path v-for="(d, i) in paths" :key="i" :d="d" />
    <circle v-if="name === 'home-star' && false" cx="0" cy="0" r="0" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ name: string; size?: 'xs' | 'sm' | 'lg' | 'md' }>();

/** 图标 path 库（与原型 HTML 的 SVG symbol 一致） */
const ICONS: Record<string, string[]> = {
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5', 'M9.5 21v-6h5v6'],
  ai: ['M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z', 'M19 15l.9 2.6L22.5 18.5l-2.6.9L19 22l-.9-2.6-2.6-.9 2.6-.9z'],
  closet: ['M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z', 'M4 10h16M10 3v7M10 10v11'],
  comm: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'm15.5 8.5-2 5-5 2 2-5z'],
  me: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5'],
  mic: ['M9 3h6v11H9z', 'M5 11a7 7 0 0 0 14 0M12 18v3'],
  cam: ['M4 8h3l2-2h6l2 2h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z', 'M12 13.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z'],
  search: ['M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14z', 'm20 20-3.8-3.8'],
  back: ['m14.5 5-7 7 7 7'],
  chev: ['m9 5 7 7-7 7'],
  down: ['m5 9 7 7 7-7'],
  cart: ['M9 20a1.4 1.4 0 1 0 0-2.8A1.4 1.4 0 0 0 9 20z', 'M17 20a1.4 1.4 0 1 0 0-2.8 1.4 1.4 0 0 0 0 2.8z', 'M3 4h2.2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.2h7.6a1.5 1.5 0 0 0 1.5-1.2L20 8H6'],
  heart: ['M12 20.5S3.5 15 3.5 9.2C3.5 6.3 5.8 4 8.7 4c1.9 0 3.1 1.1 3.3 1.3.2-.2 1.4-1.3 3.3-1.3 2.9 0 5.2 2.3 5.2 5.2 0 5.8-8.5 11.3-8.5 11.3z'],
  star: ['m12 3 2.7 5.6 6.1.8-4.5 4.2 1.1 6-5.4-2.9-5.4 2.9 1.1-6L3.2 9.4l6.1-.8z'],
  shield: ['M12 3 5 6v6c0 4.5 3 8.3 7 9 4-.7 7-4.5 7-9V6z', 'm9 12 2 2 4-4'],
  tag: ['M3 12V4h8l10 10-8 8z', 'M8 8a1.4 1.4 0 1 0 0-2.8A1.4 1.4 0 0 0 8 8z'],
  send: ['M21 3 10.5 13.5M21 3l-7 18-3.5-7.5L3 10z'],
  check: ['m4.5 12.5 5 5 10-11'],
  plus: ['M12 5v14M5 12h14'],
  lock: ['M5 11h14v9H5z', 'M8 11V8a4 4 0 0 1 8 0v3'],
  gift: ['M4 9h16v11H4z', 'M4 13h16M12 9v11M12 9s-5.5-.4-5.5-3.2C6.5 4 8 3.4 9.2 4.2 10.8 5.2 12 9 12 9zm0 0s5.5-.4 5.5-3.2c0-1.8-1.5-2.4-2.7-1.6C13.2 5.2 12 9 12 9z'],
  eye: ['M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
  'arrow-up': ['M12 19V5M5 12l7-7 7 7'],
  trash: ['M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6'],
  doc: ['M6 3h9l4 4v14H6z', 'M14 3v5h5M9 12h7M9 16h7'],
  loc: ['M12 21s-7-6.1-7-11a7 7 0 0 1 14 0c0 4.9-7 11-7 11z', 'M12 10a2.6 2.6 0 1 0 0-5.2A2.6 2.6 0 0 0 12 10z'],
  head: ['M4 14a8 8 0 0 1 16 0', 'M2.5 14h4v6h-4z', 'M17.5 14h4v6h-4z', 'M20 20a4 4 0 0 1-4 2h-1'],
  edit: ['M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17z', 'm13.5 6.5 3 3'],
  refresh: ['M20 12a8 8 0 1 1-2.3-5.6', 'M20 3v4h-4'],
  truck: ['M2 6h12v11H2z', 'M14 10h4l3 3v4h-7', 'M6.5 17.5a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z', 'M17 17.5a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z'],
  bell: ['M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6', 'M10 19a2 2 0 0 0 4 0'],
  flash: ['M13 3 5 13.5h6L11 21l8-10.5h-6z'],
  chart: ['M4 20V10M10 20V4M16 20v-7M21 20H3'],
  wallet: ['M3 6h18v14H3z', 'M3 10h18M16 15h2.5'],
  crown: ['m3 8 4.5 3L12 5l4.5 6L21 8l-1.5 10h-15z'],
  card: ['M2.5 5.5h19v13h-19z', 'M2.5 10h19M6 15h4'],
  layers: ['m12 3 9 5-9 5-9-5z', 'm3 13 9 5 9-5M3 17l9 5 9-5'],
  sun: ['M12 7a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9z', 'M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5 5l1.8 1.8M17.2 17.2 19 19M19 5l-1.8 1.8M6.8 17.2 5 19'],
  wifi: ['M2.5 9a15 15 0 0 1 19 0M5.5 12.5a10.5 10.5 0 0 1 13 0M8.8 16a6 6 0 0 1 6.4 0'],
  batt: ['M2.5 8h16v8h-16z', 'M21 11v2'],
  scan: ['M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3', 'M3.5 12h17'],
  'arrow-right': ['m9 5 7 7-7 7'],
};

const paths = computed(() => ICONS[props.name] ?? []);
const sizeClass = computed(() => {
  switch (props.size ?? 'md') {
    case 'xs': return 'ic-xs';
    case 'sm': return 'ic-sm';
    case 'lg': return 'ic-lg';
    default: return '';
  }
});
</script>

<style scoped>
.ic { width: 22px; height: 22px; flex: none; }
.ic-sm { width: 16px; height: 16px; }
.ic-xs { width: 13px; height: 13px; }
.ic-lg { width: 26px; height: 26px; }
</style>
