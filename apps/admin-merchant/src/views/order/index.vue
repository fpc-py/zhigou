<template>
  <div>
    <h2 style="margin-bottom:16px">订单管理</h2>

    <el-card>
      <Skeleton v-if="loading" :repeat="5" />
      <ErrorRetry v-else-if="error" @retry="load" />
      <template v-else>
        <el-table :data="orders" style="width:100%" v-if="orders.length">
          <el-table-column prop="orderId" label="订单号" width="220" />
          <el-table-column label="金额" width="120">
            <template #default="{row}">¥{{ ((row.totalAmount ?? row.payAmount ?? 0) / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="orderStatus" label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="statusType(row.orderStatus)">{{ statusLabel(row.orderStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="商品" min-width="220">
            <template #default="{row}">{{ skuSummary(row.items) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{row}">
              <el-button size="small" @click="$router.push('/order/' + row.orderId)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="暂无订单" />
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import http from '@/api/request';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';

const loading = ref(false);
const error = ref(false);
const orders = ref<any[]>([]);

const LABELS: Record<string, string> = {
  INIT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成',
  CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款',
};
const statusLabel = (s: string) => LABELS[s] ?? s;
const statusType = (s: string) =>
  ({ PAID: 'primary', SHIPPED: 'success', COMPLETED: 'info', INIT: 'warning', CLOSED: 'info', REFUNDING: 'danger', REFUNDED: 'danger' } as Record<string, string>)[s] || 'info';
const skuSummary = (items: any[] | undefined) =>
  (items || []).map((i) => `${i.skuName ?? 'SKU' + i.skuId} ×${i.count}`).join('、') || '无商品';

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const res = await http.get('/order/mine');
    orders.value = res.data?.data ?? [];
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
