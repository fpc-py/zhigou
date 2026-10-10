<template>
  <div class="page wallet-page">
    <!-- 头部 -->
    <header class="wallet-head">
      <div class="back" @click="router.back()"><Icon name="chev" size="sm" style="transform: rotate(180deg)" /></div>
      <p class="head-title">我的钱包</p>
      <div class="head-right" />
    </header>

    <!-- 余额卡 -->
    <section class="balance card">
      <div class="bal-row">
        <div>
          <p class="bal-label">账户余额（元）</p>
          <p class="bal-value">¥{{ (account?.balanceFen ?? 0) / 100 }}</p>
        </div>
        <button class="recharge-btn" @click="openRecharge">充值</button>
      </div>
      <div class="bal-stats">
        <div class="bal-stat"><b>{{ ((account?.totalRechargeFen ?? 0) / 100).toFixed(0) }}</b><span>累计充值</span></div>
        <div class="bal-stat"><b>{{ ((account?.totalConsumeFen ?? 0) / 100).toFixed(0) }}</b><span>累计消费</span></div>
        <div class="bal-stat"><b>{{ account?.points ?? 0 }}</b><span>我的积分</span></div>
      </div>
    </section>

    <!-- 会员等级 -->
    <section v-if="level" class="member card">
      <div class="member-head">
        <h3 class="sec-title"><Icon name="crown" size="xs" /> {{ level.title }}</h3>
        <span class="member-tag">{{ level.level }}</span>
      </div>
      <div class="benefits">
        <span v-for="b in level.benefits" :key="b">{{ b }}</span>
      </div>
      <p class="member-note">{{ level.note }}</p>
    </section>

    <!-- 流水 -->
    <section class="txns card">
      <div class="outfit-head">
        <h3 class="sec-title">收支明细</h3>
        <span class="more">共 {{ txns.length }} 条</span>
      </div>
      <div class="txn-list">
        <div v-for="t in txns" :key="t.id" class="txn">
          <span class="txn-emoji" :style="{ background: typeBg(t.type) }">{{ typeEmoji(t.type) }}</span>
          <div class="txn-info">
            <p class="txn-name">{{ t.remark || t.type }}</p>
            <p class="txn-time">{{ t.createdAt?.slice(0, 16).replace('T', ' ') }}</p>
          </div>
          <span class="txn-amount" :class="t.type === 'RECHARGE' ? 'in' : t.type === 'REFUND' ? 'in' : 'out'">
            {{ t.type === 'RECHARGE' || t.type === 'REFUND' ? '+' : '-' }}{{ (t.amountFen / 100).toFixed(2) }}
          </span>
        </div>
        <p v-if="txns.length === 0" class="empty">暂无收支明细</p>
      </div>
    </section>

    <!-- 充值弹层 -->
    <div v-if="showRecharge" class="mask" @click.self="showRecharge = false">
      <div class="sheet">
        <h3 class="sheet-title">沙箱充值（演示）</h3>
        <div class="amount-preset">
          <span v-for="a in [1000, 5000, 10000, 50000]" :key="a" class="preset" :class="{ on: amountFen === a }" @click="amountFen = a">
            ¥{{ a / 100 }}
          </span>
        </div>
        <input v-model.number="amountFen" class="input" type="number" min="1" placeholder="自定义金额（分）" />
        <p class="sheet-note">演示环境：充值直接入账并赠送等额积分（1 元 = 1 积分），不接真实支付通道。</p>
        <div class="sheet-actions">
          <button class="btn-cancel" @click="showRecharge = false">取消</button>
          <button class="btn-ok" @click="submitRecharge">确认充值</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';
import { getWalletAccount, getWalletTransactions, rechargeWallet, getMemberLevel } from '@/api/wallet';
import type { WalletAccount, WalletTransaction, MemberLevelInfo } from '@/api/wallet';

const router = useRouter();
const account = ref<WalletAccount | null>(null);
const txns = ref<WalletTransaction[]>([]);
const level = ref<MemberLevelInfo | null>(null);
const showRecharge = ref(false);
const amountFen = ref(5000);

function typeEmoji(t: string): string {
  return { RECHARGE: '💰', CONSUME: '🛒', REFUND: '↩️' }[t] || '💳';
}
function typeBg(t: string): string {
  return t === 'RECHARGE' || t === 'REFUND' ? '#E3F7F0' : '#FFEDE7';
}

async function loadAll() {
  try {
    const [acc, ts, lv] = await Promise.all([getWalletAccount(), getWalletTransactions(), getMemberLevel()]);
    account.value = acc;
    txns.value = ts;
    level.value = lv;
  } catch {
    /* 忽略 */
  }
}

function openRecharge() {
  amountFen.value = 5000;
  showRecharge.value = true;
}

async function submitRecharge() {
  const fen = Number(amountFen.value) || 0;
  if (fen <= 0) {
    showToast('请输入充值金额');
    return;
  }
  try {
    await rechargeWallet({ amountFen: fen });
    showToast('充值成功（沙箱演示）');
    showRecharge.value = false;
    loadAll();
  } catch (e: any) {
    showToast(e.message || '充值失败');
  }
}

onMounted(loadAll);
</script>

<style scoped>
.wallet-page { min-height: 100vh; padding: 16px 14px 30px; }
.wallet-head { display: flex; align-items: center; justify-content: space-between; padding: 2px 0 14px; }
.back { width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; color: var(--ink); }
.head-title { font-size: 17px; font-weight: 800; }
.head-right { width: 30px; }
.card { background: var(--card); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
.balance { background: linear-gradient(135deg, #4C5CFF, #7A6BFF 70%, #9A7BFF); color: #fff; }
.bal-row { display: flex; align-items: flex-start; justify-content: space-between; }
.bal-label { font-size: 12px; opacity: 0.85; }
.bal-value { font-size: 30px; font-weight: 800; margin-top: 6px; font-variant-numeric: tabular-nums; }
.recharge-btn { background: #fff; color: var(--brand); font-size: 12.5px; font-weight: 700; border-radius: 999px; padding: 8px 20px; }
.bal-stats { display: flex; margin-top: 18px; border-top: 1px solid rgba(255, 255, 255, 0.25); padding-top: 14px; }
.bal-stat { flex: 1; text-align: center; }
.bal-stat + .bal-stat { border-left: 1px solid rgba(255, 255, 255, 0.25); }
.bal-stat b { display: block; font-size: 16px; font-weight: 700; }
.bal-stat span { font-size: 10.5px; opacity: 0.85; }
.member-head { display: flex; align-items: center; justify-content: space-between; }
.sec-title { font-size: 14px; font-weight: 700; display: flex; align-items: center; gap: 5px; }
.sec-title svg { color: var(--brand); }
.member-tag { font-size: 10.5px; font-weight: 700; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 4px 10px; }
.benefits { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 12px; }
.benefits span { font-size: 11px; color: var(--ink-2); background: var(--bg); border-radius: 999px; padding: 5px 11px; }
.member-note { font-size: 10.5px; color: var(--ink-3); margin-top: 10px; }
.outfit-head { display: flex; align-items: center; justify-content: space-between; }
.more { font-size: 11px; color: var(--ink-3); }
.txn-list { margin-top: 10px; }
.txn { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--line); }
.txn:last-child { border-bottom: none; }
.txn-emoji { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 17px; }
.txn-info { flex: 1; min-width: 0; }
.txn-name { font-size: 13px; font-weight: 600; }
.txn-time { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.txn-amount { font-size: 13.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.txn-amount.in { color: var(--mint); }
.txn-amount.out { color: var(--ink); }
.empty { font-size: 12px; color: var(--ink-3); text-align: center; padding: 16px 0; }
.mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-end; z-index: 99; }
.sheet { width: 100%; background: var(--card); border-radius: 16px 16px 0 0; padding: 18px 16px 24px; }
.sheet-title { font-size: 15px; font-weight: 700; margin-bottom: 12px; }
.amount-preset { display: flex; gap: 8px; margin-bottom: 10px; }
.preset { flex: 1; text-align: center; font-size: 13px; font-weight: 700; color: var(--ink-2); background: var(--bg); border: 1px solid var(--line); border-radius: 10px; padding: 10px 0; cursor: pointer; }
.preset.on { color: #fff; background: var(--brand); border-color: var(--brand); }
.input { width: 100%; box-sizing: border-box; height: 40px; border: 1px solid var(--line); border-radius: 10px; padding: 0 12px; font-size: 13px; background: var(--bg); margin-bottom: 10px; }
.sheet-note { font-size: 10.5px; color: var(--ink-3); margin-bottom: 12px; }
.sheet-actions { display: flex; gap: 10px; }
.btn-cancel { flex: 1; height: 42px; border-radius: 999px; background: var(--bg); color: var(--ink-2); font-size: 13.5px; font-weight: 600; }
.btn-ok { flex: 1; height: 42px; border-radius: 999px; background: var(--brand); color: #fff; font-size: 13.5px; font-weight: 600; }
</style>
