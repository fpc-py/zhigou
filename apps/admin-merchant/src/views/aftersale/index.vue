<template>
  <div>
    <h2 style="margin-bottom:16px">售后审核</h2>

    <el-card>
      <Skeleton v-if="loading" :repeat="5" />
      <ErrorRetry v-else-if="error" @retry="load" />
      <template v-else>
        <el-table :data="list" style="width:100%" v-if="list.length">
          <el-table-column prop="aftersaleNo" label="售后单号" width="160" />
          <el-table-column prop="orderNo" label="订单号" width="160" />
          <el-table-column prop="type" label="类型" width="80" />
          <el-table-column prop="reason" label="原因" min-width="120" show-overflow-tooltip />
          <el-table-column label="退款金额" width="120">
            <template #default="{row}">¥{{ (row.amount / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{row}">
              <el-button
                v-if="row.status === 'APPLYING'"
                size="small"
                type="primary"
                @click="handleApprove(row)"
              >
                同意
              </el-button>
              <el-button
                v-if="row.status === 'APPLYING'"
                size="small"
                type="danger"
                @click="handleReject(row)"
              >
                拒绝
              </el-button>
              <el-tag v-else type="info" size="small">已处理</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="暂无售后申请" />
      </template>
    </el-card>

    <!-- 拒绝理由弹窗 -->
    <el-dialog v-model="rejectDialog" title="拒绝退款" width="400px">
      <el-form>
        <el-form-item label="拒绝理由">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="输入拒绝理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { approveRefund, rejectRefund } from '@/api/aftersale';
import type { AftersaleItem } from '@/api/aftersale';

const loading = ref(false);
const error = ref(false);
const list = ref<AftersaleItem[]>([]);
const rejectDialog = ref(false);
const rejectReason = ref('');
const currentItem = ref<AftersaleItem | null>(null);

function statusType(s: string) {
  const m: Record<string, string> = {
    APPLYING: 'warning',
    SELLER_APPROVED: 'primary',
    REFUNDING: 'primary',
    REFUNDED: 'success',
    REJECTED: 'danger',
    CANCELED: 'info',
  };
  return m[s] || 'info';
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    // 后端暂无待审列表接口，用模拟数据
    list.value = [
      {
        aftersaleNo: 'AS20261001001',
        orderNo: '202610010001',
        userId: 1001,
        type: '退款',
        reason: '商品质量问题',
        amount: 39900,
        status: 'APPLYING',
        applyAt: '2026-10-01 14:30',
      },
      {
        aftersaleNo: 'AS20261001002',
        orderNo: '202610010002',
        userId: 1002,
        type: '退款',
        reason: '不想要了',
        amount: 12900,
        status: 'APPLYING',
        applyAt: '2026-10-01 15:00',
      },
    ];
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

async function handleApprove(row: AftersaleItem) {
  try {
    await ElMessageBox.confirm(`确定同意退款 ¥${(row.amount / 100).toFixed(2)}？`, '提示', { type: 'warning' });
    await approveRefund(row.aftersaleNo);
    ElMessage.success('已同意退款');
    await load();
  } catch {
    // 取消
  }
}

function handleReject(row: AftersaleItem) {
  currentItem.value = row;
  rejectReason.value = '';
  rejectDialog.value = true;
}

async function confirmReject() {
  if (!currentItem.value || !rejectReason.value) {
    ElMessage.warning('请输入拒绝理由');
    return;
  }
  try {
    await rejectRefund(currentItem.value.aftersaleNo, rejectReason.value);
    ElMessage.success('已拒绝退款');
    rejectDialog.value = false;
    await load();
  } catch {
    // handled
  }
}

onMounted(load);
</script>