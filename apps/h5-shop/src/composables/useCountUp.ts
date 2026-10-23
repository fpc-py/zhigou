/**
 * useCountUp — 数字滚动（P5 动效系统）
 * 基于 motion（framer-motion/dom）命令式 API 驱动，spring 缓动，天然尊重
 * prefers-reduced-motion（motion 在 reduced-motion 下自动跳过动画）。
 * 用途：金额/件数/统计数字变化时的逐位滚动，增强"数字变化"的感知反馈。
 */
import { ref, onUnmounted } from 'vue';
import { animate } from 'motion';

export function useCountUp(target: number, duration = 700) {
  const value = ref(0);
  let controls: ReturnType<typeof animate> | null = null;

  function start(to: number = target) {
    controls?.stop?.();
    controls = animate(0, to, {
      duration: duration / 1000,
      ease: 'easeOut',
      onUpdate: (v: number) => {
        value.value = Math.round(v);
      },
    });
  }

  onUnmounted(() => controls?.stop?.());

  return { value, start };
}
