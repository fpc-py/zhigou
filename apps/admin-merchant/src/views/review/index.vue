<template>
  <div>
    <h2 style="margin-bottom:16px">评论管理</h2>

    <Skeleton v-if="loading" :repeat="3" />
    <ErrorRetry v-else-if="error" @retry="load" />
    <el-card v-else shadow="never">
      <template #header>
        <span>待回复评论</span>
        <el-tag size="small" type="primary" style="margin-left:8px">{{ list.length }}</el-tag>
        <span style="margin-left:12px;color:#909399;font-size:12px">AI 建议话术 · 负面预警</span>
      </template>

      <el-table :data="list" v-if="list.length">
        <el-table-column label="情感" width="80">
          <template #default="{row}">
            <el-tag size="small" :type="sentimentType(row.sentiment)">
              {{ sentimentLabel(row.sentiment) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户 / 评分" width="130">
          <template #default="{row}">
            <div>{{ row.userName }}</div>
            <div style="color:#e6a23c;font-size:12px">{{ row.rating }} 星</div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评论内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="AI 建议话术" min-width="240">
          <template #default="{row}">
            <div style="background:#f4f8ff;border-radius:6px;padding:6px 8px;font-size:12px;color:#409eff;line-height:1.5">
              {{ row.aiSuggestion }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button size="small" type="primary" plain :loading="replying === row.reviewId" @click="submit(row.reviewId, row.aiSuggestion)">
              采用建议回复
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无待回复评论 🎉" :image-size="70" />

      <el-alert
        title="演示口径：情感由星级规则映射，AI 建议话术模板生成；正式版接入情感模型与商家审核流"
        type="info" :closable="false" show-icon style="margin-top:16px" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getReviewsPending, postReviewReply } from '@/api/merchant';
import type { ReviewPendingItem } from '@/api/merchant';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';

const loading = ref(false);
const error = ref(false);
const list = ref<ReviewPendingItem[]>([]);
const replying = ref('');

const sentimentLabel = (s: string) => (s === 'NEGATIVE' ? '⚠ 负面' : s === 'POSITIVE' ? '✓ 正面' : '· 中性');
const sentimentType = (s: string) => (s === 'NEGATIVE' ? 'danger' as const : s === 'POSITIVE' ? 'success' as const : 'info' as const);

async function submit(reviewId: string, content: string) {
  if (!content) return;
  replying.value = reviewId;
  try {
    const ok = await postReviewReply(reviewId, content);
    if (ok) {
      ElMessage.success('已提交回复');
      list.value = list.value.filter((x) => x.reviewId !== reviewId);
    }
  } catch {
    ElMessage.error('提交失败，请稍后再试');
  } finally {
    replying.value = '';
  }
}

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const d = await getReviewsPending();
    if (d) list.value = d;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
