<template>
  <div>
    <h2 style="margin-bottom:16px">经营预警</h2>

    <Skeleton v-if="loading" :repeat="3" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <el-row :gutter="16">
        <!-- 低库存 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <span>低库存 SKU</span>
              <el-tag size="small" type="warning" style="margin-left:8px">{{ warnings.lowStock.length }}</el-tag>
            </template>
            <el-table :data="warnings.lowStock" size="small" v-if="warnings.lowStock.length">
              <el-table-column prop="skuId" label="SKU" width="220" />
              <el-table-column label="剩余库存" width="120">
                <template #default="{row}">
                  <el-tag size="small" type="danger">{{ row.available }} 件</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="建议" min-width="100">
                <template #default>及时补货（余量 ≤ 10）</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="无低库存 SKU，库存健康 🎉" :image-size="70" />
          </el-card>
        </el-col>

        <!-- 近期差评 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <span>近期差评（rating ≤ 3）</span>
              <el-tag size="small" type="danger" style="margin-left:8px">{{ warnings.negative.length }}</el-tag>
            </template>
            <el-table :data="warnings.negative" size="small" v-if="warnings.negative.length">
              <el-table-column label="商品" width="120">
                <template #default="{row}">SPU {{ row.spuId }}</template>
              </el-table-column>
              <el-table-column label="评分" width="70">
                <template #default="{row}">
                  <el-tag size="small" type="danger">{{ row.rating }} 星</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
              <el-table-column label="时间" width="90">
                <template #default="{row}">{{ (row.createTime || '').slice(0, 10) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="无差评，口碑稳定 🎉" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getMerchantWarnings } from '@/api/merchant';
import type { MerchantWarnings } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const warnings = ref<MerchantWarnings>({ lowStock: [], negative: [] });

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const data = await getMerchantWarnings();
    if (data) warnings.value = data;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
