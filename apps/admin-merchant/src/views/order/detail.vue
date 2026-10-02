<template>
  <div>
    <h2 style="margin-bottom:16px">订单详情 #{{ route.params.id }}</h2>

    <Skeleton v-if="loading" :repeat="5" />
    <ErrorRetry v-else-if="error" @retry="load" />

    <template v-else-if="order">
      <el-card style="margin-bottom:16px">
        <template #header>基本信息</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ order.orderId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(order.orderStatus)">{{ order.orderStatus }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ (order.totalAmount / 100).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="实付金额">¥{{ (order.payAmount / 100).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card style="margin-bottom:16px">
        <template #header>商品清单</template>
        <el-table :data="order.items || []" v-if="order.items?.length">
          <el-table-column prop="skuName" label="商品名称" />
          <el-table-column label="单价">
            <template #default="{row}">¥{{ (row.price / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="数量" width="80" />
          <el-table-column label="小计" width="120">
            <template #default="{row}">¥{{ ((row.price * row.count) / 100).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="暂无商品" />
      </el-card>

      <el-card>
        <template #header>操作</template>
        <el-button type="primary" :disabled="order.orderStatus !== 'PAID'" @click="shipDialog = true">
          发货
        </el-button>
        <el-button :disabled="true">备注</el-button>
      </el-card>
    </template>

    <EmptyState v-else text="订单不存在" />

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipDialog" title="发货" width="400px">
      <el-form>
        <el-form-item label="物流单号">
          <el-input v-model="trackingNo" placeholder="输入物流单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" @click="doShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getOrderDetail } from '@/api/order';

const route = useRoute();
const loading = ref(false);
const error = ref(false);
const order = ref<any>(null);
const shipDialog = ref(false);
const trackingNo = ref('');

function statusType(s: string) {
  const m: Record<string, string> = { PAYING: 'warning', PAID: 'primary', SHIPPED: '', RECEIVED: 'success', CLOSED: 'info' };
  return m[s] || 'info';
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    order.value = await getOrderDetail(route.params.id as string);
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function doShip() {
  if (!trackingNo.value) {
    ElMessage.warning('请输入物流单号');
    return;
  }
  ElMessage.success('发货成功（模拟）');
  shipDialog.value = false;
}

onMounted(load);
</script>