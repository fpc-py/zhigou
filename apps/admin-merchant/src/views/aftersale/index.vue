<template>
  <div>
    <h2 style="margin-bottom:16px">售后审核</h2>

    <Skeleton v-if="loading" :repeat="5" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <template v-else>
      <!-- 状态统计 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col v-for="s in statCards" :key="s.key" :span="6">
          <el-card shadow="hover" :body-style="{ padding: '16px' }">
            <div style="font-size:26px;font-weight:700;color:#303133">{{ counts[s.key] ?? 0 }}</div>
            <div style="color:#909399;font-size:13px;margin-top:4px">{{ s.label }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 状态筛选 -->
      <div style="margin-bottom:12px">
        <el-radio-group v-model="status" size="small" @change="load">
          <el-radio-button value="">待审核</el-radio-button>
          <el-radio-button value="SELLER_APPROVED">待退款</el-radio-button>
          <el-radio-button value="REFUNDING">退款中</el-radio-button>
          <el-radio-button value="REFUNDED">已完结</el-radio-button>
        </el-radio-group>
      </div>

      <el-card>
        <el-table :data="list" style="width:100%" v-if="list.length">
          <el-table-column prop="aftersaleNo" label="售后单号" width="170" />
          <el-table-column prop="orderNo" label="订单号" width="170" />
          <el-table-column label="类型" width="90">
            <template #default="{row}">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          <el-table-column label="退款金额" width="110">
            <template #default="{row}">¥{{ (row.amount / 100).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="110">
            <template #default="{row}">{{ (row.applyAt || '').slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{row}">
              <template v-if="row.status === 'APPLYING'">
                <el-button size="small" type="primary" :loading="operating === row.aftersaleNo" @click="handleApprove(row)">
                  同意
                </el-button>
                <el-button size="small" type="danger" :loading="operating === row.aftersaleNo" @click="handleReject(row)">
                  拒绝
                </el-button>
              </template>
              <el-tag v-else size="small" type="info">已处理</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="当前筛选下无售后单" />
      </el-card>
    </template>

    <!-- 拒绝理由弹窗 -->
    <el-dialog v-model="rejectDialog" title="拒绝退款" width="400px">
      <el-form>
        <el-form-item label="拒绝理由">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="输入拒绝理由（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { getMerchantPending, approveAftersale, rejectAftersale } from '@/api/aftersale';
import type { AftersaleItem } from '@/api/aftersale';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';

const loading = ref(false);
const error = ref(false);
const list = ref<AftersaleItem[]>([]);
const counts = ref<Record<string, number>>({});
const status = ref('');
const operating = ref('');
const rejectDialog = ref(false);
const rejectReason = ref('');
const currentItem = ref<AftersaleItem | null>(null);
const rejecting = ref(false);

const statCards = [
  { key: 'APPLYING', label: '待审核' },
  { key: 'SELLER_APPROVED', label: '待退款' },
  { key: 'REFUNDING', label: '退款中' },
  { key: 'REFUNDED', label: '已完结' },
];

const STATUS_LABELS: Record<string, string> = {
  APPLYING: '待审核', SELLER_APPROVED: '待退款', REFUNDING: '退款中',
  REFUNDED: '已完结', REJECTED: '已拒绝', CANCELED: '已取消',
};
const statusLabel = (s: string) => STATUS_LABELS[s] ?? s;
const statusType = (s: string) =>
  ({ APPLYING: 'warning', SELLER_APPROVED: 'primary', REFUNDING: 'primary', REFUNDED: 'success', REJECTED: 'danger', CANCELED: 'info' } as Record<string, string>)[s] || 'info';
const typeLabel = (t: string) =>
  ({ refund: '仅退款', return_refund: '退货退款', exchange: '换货', repair: '维修', RETURN_GOODS: '退货退款', REFUND_ONLY: '仅退款' } as Record<string, string>)[t] ?? t;

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const d = await getMerchantPending(status.value || undefined);
    if (d) {
      list.value = d.list || [];
      counts.value = d.counts || {};
    }
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

async function handleApprove(row: AftersaleItem) {
  try {
    await ElMessageBox.confirm(`确定同意退款 ¥${(row.amount / 100).toFixed(2)}？`, '提示', { type: 'warning' });
    operating.value = row.aftersaleNo;
    await approveAftersale(row.aftersaleNo);
    ElMessage.success('已同意退款（转入待退款）');
    await load();
  } catch {
    // 取消
  } finally {
    operating.value = '';
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
  rejecting.value = true;
  try {
    await rejectAftersale(currentItem.value.aftersaleNo, rejectReason.value);
    ElMessage.success('已拒绝退款');
    rejectDialog.value = false;
    await load();
  } catch {
    // handled
  } finally {
    rejecting.value = false;
  }
}

onMounted(load);
</script>
