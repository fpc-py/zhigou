<template>
  <div>
    <h2 style="margin-bottom:16px">补货中心</h2>

    <Skeleton v-if="loading" :repeat="3" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <!-- 低库存 SKU -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>
          <span>低库存 SKU（余量 ≤ 10）</span>
          <el-tag size="small" type="warning" style="margin-left:8px">{{ lowStock.length }}</el-tag>
        </template>
        <el-table :data="lowStock" size="small" v-if="lowStock.length">
          <el-table-column prop="skuId" label="SKU" width="220" />
          <el-table-column label="剩余库存" width="120">
            <template #default="{row}">
              <el-tag size="small" type="danger">{{ row.available }} 件</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{row}">
              <el-button size="small" type="primary" plain @click="openManual(row.skuId)">手动补货</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无低库存 SKU，库存健康 🎉" :image-size="70" />

        <div style="margin-top:14px;display:flex;align-items:center;gap:10px;flex-wrap:wrap">
          <el-button type="primary" :loading="autoLoading" @click="runAuto">
            一键自动补货（≤10 补至 50 件）
          </el-button>
          <el-alert v-if="autoMsg" :title="autoMsg" type="success" :closable="false" show-icon style="flex:1;min-width:260px" />
        </div>
      </el-card>

      <!-- 补货记录 -->
      <el-card shadow="never">
        <template #header>最近补货记录</template>
        <el-table :data="records" size="small" v-if="records.length">
          <el-table-column prop="skuId" label="SKU" width="200" />
          <el-table-column label="变动" width="180">
            <template #default="{row}">{{ row.beforeQty }} → {{ row.afterQty }}（+{{ row.addQty }}）</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{row}">
              <el-tag size="small" :type="row.triggerType === 'AUTO' ? 'success' : 'primary'">
                {{ row.triggerType === 'AUTO' ? '自动' : '手动' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="时间" width="160">
            <template #default="{row}">{{ (row.createTime || '').slice(0, 19) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无补货记录" :image-size="70" />
      </el-card>
    </template>

    <!-- 手动补货弹窗 -->
    <el-dialog v-model="manualDialog" title="手动补货" width="420px">
      <el-form label-width="90px">
        <el-form-item label="SKU">
          <el-input :model-value="manualSkuId" disabled />
        </el-form-item>
        <el-form-item label="补货数量">
          <el-input-number v-model="manualQty" :min="1" :max="1000" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualRemark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialog = false">取消</el-button>
        <el-button type="primary" :loading="manualLoading" @click="confirmManual">确认补货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getMerchantWarnings, getReplenishRecords, autoReplenish, manualReplenish } from '@/api/merchant';
import type { ReplenishRecord } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const lowStock = ref<{ skuId: number; available: number }[]>([]);
const records = ref<ReplenishRecord[]>([]);
const autoLoading = ref(false);
const autoMsg = ref('');
const manualDialog = ref(false);
const manualSkuId = ref('');
const manualQty = ref(10);
const manualRemark = ref('');
const manualLoading = ref(false);

async function runAuto() {
  autoLoading.value = true;
  autoMsg.value = '';
  try {
    const done = await autoReplenish(10, 50);
    if (done && done.length) {
      autoMsg.value = `自动补货完成：${done.length} 个 SKU 已补货（${done.map((r) => 'SKU' + r.skuId).join('、')}）`;
    } else {
      autoMsg.value = '暂无低库存 SKU，无需补货';
    }
    ElMessage.success(autoMsg.value);
    await load();
  } catch {
    autoMsg.value = '补货失败，请稍后再试';
  } finally {
    autoLoading.value = false;
  }
}

function openManual(skuId: number) {
  manualSkuId.value = String(skuId);
  manualQty.value = 10;
  manualRemark.value = '';
  manualDialog.value = true;
}

async function confirmManual() {
  manualLoading.value = true;
  try {
    const r = await manualReplenish(Number(manualSkuId.value), manualQty.value, manualRemark.value || undefined);
    if (r) {
      ElMessage.success(`补货完成：SKU ${r.skuId} ${r.beforeQty} → ${r.afterQty}`);
      manualDialog.value = false;
      await load();
    }
  } catch {
    ElMessage.error('补货失败，请稍后再试');
  } finally {
    manualLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const [w, r] = await Promise.all([
      getMerchantWarnings(),
      getReplenishRecords(10),
    ]);
    if (w) lowStock.value = w.lowStock || [];
    if (r) records.value = r;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
