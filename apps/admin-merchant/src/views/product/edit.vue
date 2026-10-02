<template>
  <div>
    <h2 style="margin-bottom:20px">{{ isEdit ? '编辑商品' : '新建商品' }}</h2>

    <Skeleton v-if="loading" :repeat="6" />

    <el-card v-else style="max-width:800px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="商品名称" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="副标题" />
        </el-form-item>
        <el-form-item label="主图">
          <el-input v-model="form.mainImage" placeholder="图片 URL" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="商品描述" />
        </el-form-item>

        <!-- SKU 列表 -->
        <el-form-item label="SKU">
          <div style="width:100%">
            <div v-for="(sku, i) in form.skus" :key="i" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
              <el-input v-model="sku.specName" placeholder="规格名" style="width:100px" />
              <el-input v-model="sku.specValue" placeholder="规格值" style="width:100px" />
              <el-input-number v-model="sku.price" :min="0" :precision="2" placeholder="价格(元)" style="width:140px" />
              <el-input-number v-model="sku.stock" :min="0" placeholder="库存" style="width:100px" />
              <el-button type="danger" size="small" @click="form.skus.splice(i,1)">删除</el-button>
            </div>
            <el-button type="primary" link @click="addSku">+ 添加规格</el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getSpuDetail, createSpu, updateSpu } from '@/api/product';

const route = useRoute();
const router = useRouter();
const spuId = route.params.spuId as string | undefined;
const isEdit = !!spuId;
const loading = ref(false);
const saving = ref(false);

const form = ref({
  name: '',
  subtitle: '',
  mainImage: '',
  description: '',
  categoryId: 0,
  brandId: 0,
  skus: [] as any[],
});

function addSku() {
  form.value.skus.push({ specName: '', specValue: '', price: 0, stock: 0, image: '' });
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请输入商品名称');
    return;
  }
  saving.value = true;
  try {
    // 金额转分
    const payload = {
      ...form.value,
      skus: form.value.skus.map((s: any) => ({
        ...s,
        price: Math.round((s.price || 0) * 100),
      })),
    };
    if (isEdit) {
      await updateSpu(Number(spuId), payload);
      ElMessage.success('更新成功');
    } else {
      await createSpu(payload);
      ElMessage.success('创建成功');
    }
    router.push('/product');
  } catch {
    // request.ts 已处理 toast
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  if (isEdit) {
    loading.value = true;
    try {
      const detail = await getSpuDetail(Number(spuId));
      form.value = {
        name: detail.name || '',
        subtitle: detail.subtitle || '',
        mainImage: detail.mainImage || '',
        description: detail.description || '',
        categoryId: detail.categoryId || 0,
        brandId: detail.brandId || 0,
        skus: (detail.skus || []).map((s) => ({
          specName: s.specName || '',
          specValue: s.specValue || '',
          price: (s.price || 0) / 100,
          stock: s.stock || 0,
          image: s.image || '',
        })),
      };
    } catch {
      // error handled
    } finally {
      loading.value = false;
    }
  } else {
    addSku();
  }
});
</script>