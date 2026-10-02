<template>
  <div style="text-align:center;padding:60px 0;">
    <el-result icon="error" :title="text || '加载失败'" sub-title="请检查网络后重试">
      <template #extra>
        <el-button type="primary" @click="$emit('retry')">重试</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
defineProps<{ text?: string }>();
defineEmits<{ retry: [] }>();
</script>