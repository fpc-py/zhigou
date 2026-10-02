<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h2>商品管理</h2>
      <el-button type="primary" @click="$router.push('/product/edit')">新建商品</el-button>
    </div>

    <!-- 搜索 -->
    <el-card style="margin-bottom:16px">
      <el-form :model="query" inline>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">搜索</el-button>
          <el-button @click="query.keyword='';load()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格: 三种状态 -->
    <el-card>
      <Skeleton v-if="loading" :repeat="5" />
      <ErrorRetry v-else-if="error" @retry="load" />
      <template v-else>
        <el-table :data="list" v-loading="loading" style="width:100%" v-if="list.length">
          <el-table-column prop="spuId" label="SPU ID" width="80" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column label="价格区间" width="140">
            <template #default="{row}">
              ¥{{ (row.priceMin / 100).toFixed(2) }} ~ ¥{{ (row.priceMax / 100).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column prop="salesVolume" label="销量" width="80" />
          <el-table-column label="状态" width="80">
            <template #default="{row}">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{row}">
              <el-button size="small" @click="$router.push('/product/edit/' + row.spuId)">编辑</el-button>
              <el-button
                size="small"
                :type="row.status === 1 ? 'danger' : 'primary'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else text="暂无商品" />

        <!-- 分页 -->
        <div style="margin-top:16px;text-align:right" v-if="total > 0">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { getSpuList, offShelf } from '@/api/product';
import type { SpuItem } from '@/api/product';

const loading = ref(false);
const error = ref(false);
const list = ref<SpuItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);
const query = ref({ keyword: '' });

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const res = await getSpuList({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: query.value.keyword });
    list.value = res.records;
    total.value = res.total;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

async function toggleStatus(row: SpuItem) {
  const action = row.status === 1 ? '下架' : '上架';
  try {
    await ElMessageBox.confirm(`确定${action}商品「${row.name}」吗？`, '提示', { type: 'warning' });
    if (row.status === 1) {
      await offShelf(row.spuId);
    }
    ElMessage.success(`${action}成功`);
    await load();
  } catch {
    // 取消操作不做处理
  }
}

onMounted(load);
</script>