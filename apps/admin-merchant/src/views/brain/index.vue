<template>
  <div>
    <h2 style="margin-bottom:16px">经营大脑</h2>

    <Skeleton v-if="loading" :repeat="4" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>
          <span style="margin-right:16px">销量预测</span>
          <el-radio-group v-model="fcDays" size="small" @change="switchDays">
            <el-radio-button :value="7">7 天</el-radio-button>
            <el-radio-button :value="30">30 天</el-radio-button>
          </el-radio-group>
        </template>
        <el-table :data="forecast" size="small" v-if="forecast.length" v-loading="fcLoading">
          <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip>
            <template #default="{row}">
              <span>{{ row.productName }}</span>
              <el-tag size="small" :type="hotType(row.hotLevel)" style="margin-left:6px">{{ row.hotLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前库存" width="100">
            <template #default="{row}">{{ row.currentStock }} 件</template>
          </el-table-column>
          <el-table-column label="近 7 日销量" width="110">
            <template #default="{row}">{{ row.last7Total }} 件</template>
          </el-table-column>
          <el-table-column label="趋势" width="90">
            <template #default="{row}">
              <span :style="row.trendPct >= 0 ? 'color:#67c23a' : 'color:#f56c6c'">
                {{ row.trendPct >= 0 ? '+' : '' }}{{ row.trendPct }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="`预测销量（${fcDays} 天）`" width="140">
            <template #default="{row}">≈ {{ row.forecastQty }} 件</template>
          </el-table-column>
          <el-table-column label="建议备货" width="110">
            <template #default="{row}">
              <el-tag size="small" type="warning">+{{ row.suggestStock }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else :description="fcLoading ? '预测计算中…' : '暂无预测数据'" :image-size="70" />
      </el-card>

      <!-- 智能选品 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>智能选品建议</template>
        <el-table :data="selection" size="small" v-if="selection.length">
          <el-table-column type="index" label="排名" width="60">
            <template #default="{row, $index}">
              <el-tag :type="$index < 2 ? 'danger' : 'info'" size="small">{{ $index + 1 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip>
            <template #default="{row}">
              <span>{{ row.productName }}</span>
              <el-tag size="small" :type="hotType(row.hotLevel)" style="margin-left:6px">{{ row.hotLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="选品理由" min-width="260" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="暂无选品建议" :image-size="70" />
      </el-card>

      <!-- 动态定价 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>动态定价建议</template>
        <el-table :data="pricing" size="small" v-if="pricing.length" v-loading="prLoading">
          <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
          <el-table-column label="现价" width="100">
            <template #default="{row}">¥{{ (row.currentPriceFen / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="竞品均价" width="110">
            <template #default="{row}">¥{{ (row.competitorAvgFen / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="建议价" width="110">
            <template #default="{row}">
              <el-tag size="small" :type="priceType(row.action)">¥{{ (row.suggestPriceFen / 100).toFixed(2) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="80">
            <template #default="{row}">
              <el-tag size="small" :type="priceType(row.action)">
                {{ row.action === 'UP' ? '▲ 提价' : row.action === 'DOWN' ? '▼ 降价' : '＝ 维持' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="依据" min-width="200" show-overflow-tooltip />
        </el-table>
        <el-empty v-else :description="prLoading ? '定价计算中…' : '暂无定价建议'" :image-size="70" />
      </el-card>

      <!-- 营销方案 -->
      <el-card shadow="never">
        <template #header>营销方案建议</template>
        <el-table :data="plans" size="small" v-if="plans.length">
          <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
          <el-table-column prop="strategy" label="策略" width="110">
            <template #default="{row}">
              <el-tag size="small" type="primary">{{ row.strategy }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="方案" min-width="200" show-overflow-tooltip />
          <el-table-column label="触达渠道" width="200">
            <template #default="{row}">{{ (row.channels || []).join(' · ') }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无营销方案" :image-size="70" />
      </el-card>

      <el-alert
        title="演示口径：数据底座 product-service daily_sales + marketing-service；正式版接入价格弹性/时序模型/券拼团数据"
        type="info" :closable="false" show-icon style="margin-top:16px" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  getMerchantForecast, getMerchantSelection, getMerchantPricing, getMarketingPlan,
} from '@/api/merchant';
import type { ForecastItem, SelectionItem, PricingItem, MarketingPlanItem } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const fcDays = ref(7);
const fcLoading = ref(false);
const prLoading = ref(false);
const forecast = ref<ForecastItem[]>([]);
const selection = ref<SelectionItem[]>([]);
const pricing = ref<PricingItem[]>([]);
const plans = ref<MarketingPlanItem[]>([]);

const hotType = (h: string) => (h === '热销' ? 'danger' as const : h === '走弱' ? 'warning' as const : 'info' as const);
const priceType = (a: string) => (a === 'UP' ? 'danger' as const : a === 'DOWN' ? 'success' as const : 'info' as const);

async function switchDays() {
  fcLoading.value = true;
  try {
    const d = await getMerchantForecast(fcDays.value);
    if (d) forecast.value = d;
  } catch {
    // 保持空态
  } finally {
    fcLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = false;
  fcLoading.value = true;
  prLoading.value = true;
  try {
    const [f, s, p, m] = await Promise.all([
      getMerchantForecast(7),
      getMerchantSelection(),
      getMerchantPricing(),
      getMarketingPlan(),
    ]);
    if (f) forecast.value = f;
    if (s) selection.value = s;
    if (p) pricing.value = p;
    if (m) plans.value = m;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
    fcLoading.value = false;
    prLoading.value = false;
  }
}

onMounted(load);
</script>
