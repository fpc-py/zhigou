<template>
  <div class="page closet-page">
    <!-- 头部 -->
    <header class="closet-head">
      <div>
        <p class="head-title">我的衣橱</p>
        <p class="head-sub"><Icon name="ai" size="xs" /> 小智已记住你的 32 件单品</p>
      </div>
      <button class="add-btn" @click="toast('上传衣橱功能规划中')"><Icon name="plus" /></button>
    </header>

    <!-- 数据统计 -->
    <section class="stats card">
      <div class="stat"><b>32</b><span>收录件数</span></div>
      <div class="stat"><b>18</b><span>搭配次数</span></div>
      <div class="stat"><b>76%</b><span>利用率</span></div>
    </section>

    <!-- 今日穿搭 -->
    <section class="outfit card">
      <div class="outfit-head">
        <h3 class="sec-title">今日穿搭</h3>
        <span class="ai-badge"><Icon name="ai" size="xs" /> AI 搭配</span>
      </div>
      <p class="outfit-desc">通勤风 · 温差 8°C，适合叠穿</p>
      <div class="outfit-chips">
        <span class="o-chip have"><Icon name="check" size="xs" /> 燕麦色风衣</span>
        <span class="o-chip have"><Icon name="check" size="xs" /> 针织打底衫</span>
        <span class="o-chip have"><Icon name="check" size="xs" /> 直筒西裤</span>
        <span class="o-chip need" @click="goProducts">低帮小白鞋 <Icon name="plus" size="xs" /></span>
      </div>
      <button class="wear-btn" @click="toast('生成穿搭海报功能规划中')">一键生成今日海报</button>
    </section>

    <!-- 缺件推荐 -->
    <section class="gap card">
      <div class="gap-head">
        <h3 class="sec-title">还缺 2 件</h3>
        <span class="gap-note">补齐后利用率可提升至 92%</span>
      </div>
      <div class="gap-items">
        <div class="gap-item" @click="goProducts">
          <span class="gap-img">👟</span>
          <div class="gap-info">
            <p class="gap-name">低帮小白鞋</p>
            <p class="gap-tag">百搭 · 通勤</p>
          </div>
          <button class="gap-go">去补货</button>
        </div>
        <div class="gap-item" @click="goProducts">
          <span class="gap-img">🧣</span>
          <div class="gap-info">
            <p class="gap-name">羊毛围巾</p>
            <p class="gap-tag">秋冬 · 叠穿</p>
          </div>
          <button class="gap-go">去补货</button>
        </div>
      </div>
    </section>

    <div class="planning-note">智能衣橱为原型演示页，衣物识别与搭配能力规划中</div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';

const router = useRouter();

function goProducts() {
  router.push('/products');
}

function toast(msg: string) {
  showToast(msg);
}
</script>

<style scoped>
.closet-page { min-height: 100vh; padding: 16px 14px 30px; }
.closet-head { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px 14px; }
.head-title { font-size: 19px; font-weight: 800; }
.head-sub { font-size: 11.5px; color: var(--ink-3); margin-top: 3px; display: flex; align-items: center; gap: 4px; }
.head-sub svg { color: var(--brand); }
.add-btn { width: 34px; height: 34px; border-radius: 12px; background: var(--brand); color: #fff; display: flex; align-items: center; justify-content: center; }
.card { background: var(--card); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
.stats { display: flex; }
.stat { flex: 1; text-align: center; }
.stat + .stat { border-left: 1px solid var(--line); }
.stat b { display: block; font-size: 22px; color: var(--brand); line-height: 1.2; }
.stat span { font-size: 11px; color: var(--ink-3); }
.outfit-head { display: flex; align-items: center; justify-content: space-between; }
.sec-title { font-size: 14px; font-weight: 700; }
.ai-badge { display: inline-flex; align-items: center; gap: 4px; font-size: 10.5px; font-weight: 700; color: var(--brand); background: var(--brand-soft); padding: 4px 9px; border-radius: 999px; }
.outfit-desc { font-size: 12.5px; color: var(--ink-2); margin: 10px 0; }
.outfit-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.o-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 11.5px; border-radius: 999px; padding: 6px 11px; }
.o-chip.have { color: var(--mint); background: var(--mint-soft); }
.o-chip.need { color: var(--accent); background: var(--accent-soft); cursor: pointer; }
.wear-btn { margin-top: 14px; width: 100%; height: 42px; border-radius: 999px; background: linear-gradient(120deg, var(--brand), #7A6BFF); color: #fff; font-size: 13.5px; font-weight: 600; }
.gap-head { display: flex; align-items: baseline; justify-content: space-between; }
.gap-note { font-size: 10.5px; color: var(--ink-3); }
.gap-items { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.gap-item { display: flex; align-items: center; gap: 10px; background: var(--bg); border-radius: 12px; padding: 10px; cursor: pointer; }
.gap-img { width: 44px; height: 44px; border-radius: 10px; background: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.gap-info { flex: 1; }
.gap-name { font-size: 13px; font-weight: 600; }
.gap-tag { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.gap-go { font-size: 11.5px; font-weight: 600; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 7px 13px; }
.planning-note { text-align: center; font-size: 10.5px; color: var(--ink-3); padding: 6px 0; }
</style>
