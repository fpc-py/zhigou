<template>
  <div class="page tryon-page">
    <header class="tryon-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">AR 试穿</span>
      <span class="head-spacer" />
    </header>

    <!-- 试穿舞台 -->
    <div class="stage">
      <div class="mannequin">
        <span class="mannequin-body" />
        <span class="mannequin-head" />
        <div class="scan-line" />
      </div>
      <div class="stage-chips">
        <button v-for="c in tryOnChips" :key="c" class="stage-chip" :class="{ on: c === activeChip }" @click="activeChip = c">{{ c }}</button>
      </div>
      <!-- AI 搭配评分 -->
      <div class="ai-rate">
        <div class="rate-ring" :style="{ background: `conic-gradient(var(--brand) ${score * 3.6}deg, rgba(255,255,255,.25) 0deg)` }">
          <div class="rate-inner"><b>{{ score }}</b><span>搭配分</span></div>
        </div>
      </div>
    </div>

    <!-- 搭配单品 -->
    <section class="mix card">
      <h3 class="sec-title">AI 搭配单品</h3>
      <div class="mix-strip">
        <div v-for="(m, i) in mix" :key="i" class="mix-item" @click="goProduct(m.spuId)">
          <div class="mix-img">{{ m.emoji }}</div>
          <p class="mix-name">{{ m.name }}</p>
          <p class="mix-price">¥{{ m.price }}</p>
        </div>
      </div>
    </section>

    <!-- 底部操作 -->
    <div class="tryon-bar">
      <button class="bar-btn line" @click="toast('已保存试穿效果')"><Icon name="check" size="sm" /> 保存</button>
      <button class="bar-btn brand" @click="toast('分享功能规划中')"><Icon name="send" size="sm" /> 分享</button>
    </div>

    <div class="planning-note">AR 试穿为原型演示页，虚拟试穿能力规划中</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';

const router = useRouter();
const activeChip = ref('通勤风');
const score = 86;
const tryOnChips = ['通勤风', '休闲风', '约会风'];
const mix = [
  { spuId: '1001', emoji: '🧥', name: '燕麦色风衣', price: '599' },
  { spuId: '1002', emoji: '👔', name: '针织打底衫', price: '199' },
  { spuId: '1003', emoji: '👖', name: '直筒西裤', price: '299' },
];

function goProduct(spuId: string) {
  router.push(`/product/${spuId}`);
}

function toast(msg: string) {
  showToast(msg);
}
</script>

<style scoped>
.tryon-page { min-height: 100vh; padding-bottom: 110px; }
.tryon-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.stage {
  position: relative;
  margin: 4px 14px 0;
  height: 430px;
  border-radius: var(--radius);
  background: linear-gradient(160deg, #E8EBFF 0%, #F7F3FF 55%, #E3F7F0 100%);
  overflow: hidden;
}
.mannequin { position: absolute; left: 50%; bottom: 0; transform: translateX(-50%); width: 150px; height: 360px; }
.mannequin-body {
  position: absolute;
  left: 50%; bottom: 0; transform: translateX(-50%);
  width: 120px; height: 250px;
  background: linear-gradient(180deg, #6E79F5, #5560E8);
  border-radius: 60px 60px 22px 22px;
}
.mannequin-head {
  position: absolute;
  left: 50%; top: 18px; transform: translateX(-50%);
  width: 62px; height: 78px;
  background: #F3D9C8;
  border-radius: 50% 50% 46% 46%;
}
.scan-line {
  position: absolute;
  left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, transparent, var(--brand), transparent);
  animation: scan 3.2s ease-in-out infinite;
  filter: drop-shadow(0 0 6px var(--brand));
}
@keyframes scan { 0%,100% { top: 8%; } 50% { top: 88%; } }
.stage-chips { position: absolute; top: 14px; left: 12px; display: flex; flex-direction: column; gap: 8px; }
.stage-chip {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-2);
  background: rgba(255,255,255,.85);
  border-radius: 999px;
  padding: 6px 12px;
}
.stage-chip.on { color: #fff; background: var(--brand); }
.ai-rate { position: absolute; right: 14px; top: 14px; }
.rate-ring {
  width: 78px; height: 78px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: conic-gradient(var(--brand) 310deg, rgba(255,255,255,.25) 0deg);
}
.rate-inner {
  width: 62px; height: 62px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.rate-inner b { font-size: 20px; color: var(--brand); line-height: 1.1; }
.rate-inner span { font-size: 9px; color: var(--ink-3); }
.card { margin: 12px 14px 0; background: var(--card); border-radius: var(--radius); padding: 16px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 12px; }
.mix-strip { display: flex; gap: 10px; overflow-x: auto; }
.mix-item { flex: none; width: 92px; text-align: center; cursor: pointer; }
.mix-img { width: 92px; height: 92px; border-radius: 14px; background: var(--bg); display: flex; align-items: center; justify-content: center; font-size: 34px; }
.mix-name { font-size: 11px; margin-top: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mix-price { font-size: 11px; color: var(--accent); font-weight: 700; margin-top: 2px; }
.tryon-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  display: flex;
  gap: 10px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
  background: rgba(255,255,255,.96);
  border-top: 1px solid var(--line);
}
.bar-btn { flex: 1; height: 44px; border-radius: 999px; font-size: 14px; font-weight: 600; display: flex; align-items: center; justify-content: center; gap: 6px; }
.bar-btn.line { background: var(--bg); color: var(--ink-2); border: 1px solid var(--line-2); }
.bar-btn.brand { background: var(--brand); color: #fff; }
.planning-note { text-align: center; font-size: 10.5px; color: var(--ink-3); padding: 14px 0 4px; }
</style>
