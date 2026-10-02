<template>
  <div>
    <h2 style="margin-bottom:20px">数据看板</h2>

    <div v-if="loading">
      <Skeleton w="100%" h="100px" :repeat="4" />
    </div>

    <div v-else-if="error">
      <ErrorRetry text="看板数据加载失败" @retry="load" />
    </div>

    <template v-else>
      <!-- 统计卡片 -->
      <el-row :gutter="16" style="margin-bottom:24px">
        <el-col v-for="card in cards" :key="card.label" :span="6">
          <el-card shadow="hover">
            <div style="font-size:13px;color:#999">{{ card.label }}</div>
            <div style="font-size:28px;font-weight:700;margin-top:8px">{{ card.value }}</div>
            <div style="font-size:12px;color:#999;margin-top:4px">{{ card.sub }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 最近订单 -->
      <el-card>
        <template #header>最近订单</template>
        <el-table :data="recentOrders" style="width:100%" v-if="recentOrders.length">
          <el-table-column prop="orderId" label="订单号" width="180" />
          <el-table-column label="金额" width="120">
            <template #default="{row}">¥{{ (row.totalAmount / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="orderStatus" label="状态" width="100" />
          <el-table-column label="商品数" width="80">
            <template #default="{row}">{{ row.items?.length || 0 }}</template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="暂无订单数据" />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getDashboardStats } from '@/api/dashboard';

const loading = ref(true);
const error = ref(false);
const cards = ref([
  { label: '今日 GMV', value: '¥12,800.00', sub: '较昨日 +12%' },
  { label: '订单数', value: '86', sub: '较昨日 +8%' },
  { label: '客单价', value: '¥148.84', sub: '较昨日 -2%' },
  { label: '退款率', value: '2.3%', sub: '较昨日 -0.1%' },
]);
const recentOrders = ref<any[]>([]);

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const stats = await getDashboardStats();
    cards.value = [
      { label: '今日 GMV', value: `¥${(stats.todayGmv / 100).toFixed(2)}`, sub: '今日数据' },
      { label: '订单数', value: String(stats.orderCount), sub: '今日' },
      { label: '客单价', value: `¥${(stats.avgOrderAmount / 100).toFixed(2)}`, sub: '平均' },
      { label: '退款率', value: `${stats.refundRate}%`, sub: '今日' },
    ];
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>