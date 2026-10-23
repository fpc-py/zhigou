<template>
  <div>
    <h2 style="margin-bottom:16px">数据看板</h2>

    <Skeleton v-if="loading" :repeat="4" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <!-- 核心指标 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card shadow="hover">
            <div style="color:#909399;font-size:13px">累计销售额（元）</div>
            <div style="font-size:26px;font-weight:700;color:#303133;margin:8px 0 4px">
              ¥{{ (overview.totalSalesFen / 100).toLocaleString() }}
            </div>
            <div style="color:#909399;font-size:12px">累计订单 {{ overview.totalOrders }} 单</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div style="color:#909399;font-size:13px">今日订单</div>
            <div style="font-size:26px;font-weight:700;color:#303133;margin:8px 0 4px">
              {{ overview.todayOrders }} 单
            </div>
            <div style="color:#909399;font-size:12px">今日销售额 ¥{{ (overview.todaySalesFen / 100).toLocaleString() }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div style="color:#909399;font-size:13px">待处理售后</div>
            <div style="font-size:26px;font-weight:700;color:#f56c6c;margin:8px 0 4px">
              {{ overview.pendingAfterSale }}
            </div>
            <div style="color:#909399;font-size:12px">需商家审核</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div style="color:#909399;font-size:13px">数据生成时间</div>
            <div style="font-size:15px;font-weight:600;color:#303133;margin:14px 0 4px">
              {{ (overview.generatedAt || '').slice(0, 19) }}
            </div>
            <div style="color:#909399;font-size:12px">平台聚合口径 · 演示单商家</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <!-- 订单状态分布 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>订单状态分布</template>
            <div v-if="statusRows.length">
              <div v-for="r in statusRows" :key="r.status" style="display:flex;align-items:center;gap:10px;margin-bottom:10px">
                <span style="width:64px;font-size:13px;color:#606266">{{ statusLabel(r.status) }}</span>
                <el-progress
                  :percentage="r.pct"
                  :stroke-width="12"
                  style="flex:1"
                  :color="statusColor(r.status)"
                />
                <span style="width:56px;text-align:right;font-size:13px;color:#606266">{{ r.count }} 单</span>
              </div>
            </div>
            <el-empty v-else description="暂无订单数据" :image-size="70" />
          </el-card>
        </el-col>

        <!-- 热销榜 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>热销商品 Top{{ hotList.length }}</template>
            <el-table :data="hotList" size="small" v-if="hotList.length">
              <el-table-column type="index" label="#" width="48" />
              <el-table-column prop="spuName" label="商品" min-width="140" show-overflow-tooltip />
              <el-table-column prop="soldCount" label="售出" width="90">
                <template #default="{row}">{{ row.soldCount }} 件</template>
              </el-table-column>
              <el-table-column label="销售额" width="130">
                <template #default="{row}">¥{{ (row.salesFen / 100).toLocaleString() }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无销售数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { getMerchantOverview } from '@/api/merchant';
import type { MerchantOverview } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const overview = ref<MerchantOverview>({
  totalOrders: 0, totalSalesFen: 0, todayOrders: 0, todaySalesFen: 0,
  statusDist: {}, hotSpus: [], pendingAfterSale: 0, generatedAt: '',
});

const LABELS: Record<string, string> = {
  INIT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成',
  CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款',
};
const statusLabel = (s: string) => LABELS[s] ?? s;
const statusColor = (s: string) =>
  ({ PAID: '#409eff', SHIPPED: '#67c23a', COMPLETED: '#909399', INIT: '#e6a23c', CLOSED: '#c0c4cc', REFUNDING: '#f56c6c', REFUNDED: '#f56c6c' } as Record<string, string>)[s] ?? '#409eff';

const statusRows = computed(() => {
  const total = Object.values(overview.value.statusDist || {}).reduce((a, b) => a + b, 0);
  return Object.entries(overview.value.statusDist || {})
    .map(([status, count]) => ({ status, count, pct: total ? Math.round((count / total) * 100) : 0 }))
    .sort((a, b) => b.count - a.count);
});
const hotList = computed(() => overview.value.hotSpus || []);

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const data = await getMerchantOverview();
    if (data) overview.value = data;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
