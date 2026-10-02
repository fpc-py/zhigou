<template>
  <div>
    <h2 style="margin-bottom:16px">订单管理</h2>

    <el-card>
      <Skeleton v-if="loading" :repeat="5" />
      <ErrorRetry v-else-if="error" @retry="load" />
      <template v-else>
        <el-table :data="orders" style="width:100%" v-if="orders.length">
          <el-table-column prop="orderId" label="订单号" width="180" />
          <el-table-column label="金额" width="120">
            <template #default="{row}">¥{{ (row.totalAmount / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="orderStatus" label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="statusType(row.orderStatus)">{{ row.orderStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="商品数" width="80">
            <template #default="{row}">{{ row.items?.length || 0 }}</template>
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

const loading = ref(false);
const error = ref(false);
const orders = ref<any[]>([]);

function statusType(s: string) {
  const m: Record<string, string> = { PAYING: 'warning', PAID: 'primary', SHIPPED: '', RECEIVED: 'success', CLOSED: 'info' };
  return m[s] || 'info';
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    // 后端暂无订单列表接口，展示模拟数据
    orders.value = [
      { orderId: 202610010001, totalAmount: 39900, orderStatus: 'PAID', items: [{ skuId: 1 }] },
      { orderId: 202610010002, totalAmount: 12900, orderStatus: 'SHIPPED', items: [{ skuId: 2 }] },
      { orderId: 202610010003, totalAmount: 69900, orderStatus: 'PAYING', items: [{ skuId: 3 }] },
    ];
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>