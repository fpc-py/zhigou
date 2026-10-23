<template>
  <div>
    <h2 style="margin-bottom:16px">履约异常</h2>

    <Skeleton v-if="loading" :repeat="3" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <!-- 待发货订单 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>
          <span>待发货订单（PAID）</span>
          <el-tag size="small" type="warning" style="margin-left:8px">{{ pendingOrders.length }}</el-tag>
        </template>
        <el-table :data="pendingOrders" size="small" v-if="pendingOrders.length">
          <el-table-column prop="orderId" label="订单号" width="220" />
          <el-table-column label="商品明细" min-width="220">
            <template #default="{row}">{{ skuSummary(row.items) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{row}">¥{{ ((row.payAmount ?? 0) / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag size="small" :type="row.handled ? 'success' : 'warning'">{{ row.handled ? '已处理' : '待发货' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无待发货订单 🎉" :image-size="70" />

        <div style="margin-top:14px;display:flex;gap:10px;flex-wrap:wrap">
          <el-button
            v-for="a in actions"
            :key="a.value"
            :type="a.type"
            :loading="actionLoading === a.value"
            :disabled="!pendingOrders.length"
            @click="runAction(a.value)"
          >
            {{ a.label }}（{{ pendingOrders.length }} 单）
          </el-button>
        </div>
        <div v-if="actionMsg" style="margin-top:10px">
          <el-alert :title="actionMsg" type="success" :closable="false" show-icon />
        </div>
      </el-card>

      <!-- 处理记录 -->
      <el-card shadow="never">
        <template #header>最近处理记录</template>
        <el-table :data="records" size="small" v-if="records.length">
          <el-table-column label="订单号" width="200">
            <template #default="{row}">{{ shortId(row.orderId) }}</template>
          </el-table-column>
          <el-table-column label="动作" width="110">
            <template #default="{row}">
              <el-tag size="small">{{ ACTION_LABELS[row.action] || row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="时间" width="160">
            <template #default="{row}">{{ (row.createTime || '').slice(0, 19) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无处理记录" :image-size="70" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getMerchantFulfillment, getFulfillmentActions, runFulfillmentAction, ACTION_LABELS } from '@/api/merchant';
import type { PendingOrder, FulfillmentActionRecord } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const pendingOrders = ref<PendingOrder[]>([]);
const records = ref<FulfillmentActionRecord[]>([]);
const actionLoading = ref('');
const actionMsg = ref('');

const actions = [
  { value: 'SPLIT', label: '拆分发货', type: 'primary' as const },
  { value: 'DELAY', label: '延期发货', type: 'warning' as const },
  { value: 'REPLENISH', label: '补货后发货', type: 'success' as const },
  { value: 'OFF_SHELF', label: '下架停单', type: 'danger' as const },
];

const shortId = (id: string) => String(id).slice(-8);
const skuSummary = (items: { skuName: string; count: number }[] | undefined) =>
  (items || []).map((i) => `${i.skuName} ×${i.count}`).join('、') || '无商品';

async function runAction(action: string) {
  const ids = pendingOrders.value.map((o) => o.orderId);
  if (!ids.length) {
    actionMsg.value = '暂无待发货订单可处理';
    return;
  }
  actionLoading.value = action;
  actionMsg.value = '';
  try {
    const done = await runFulfillmentAction(action, ids);
    actionMsg.value = `${ACTION_LABELS[action] || action}执行完成：${done ?? 0} 单已写入处理记录`;
    ElMessage.success(actionMsg.value);
    await load();
  } catch {
    actionMsg.value = '处理失败，请稍后再试';
  } finally {
    actionLoading.value = '';
  }
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const [f, r] = await Promise.all([
      getMerchantFulfillment(),
      getFulfillmentActions(10),
    ]);
    if (f) pendingOrders.value = f;
    if (r) records.value = r;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
