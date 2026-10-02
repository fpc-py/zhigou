<template>
  <el-empty :description="text || '暂无数据'" />
</template>

<script setup lang="ts">
defineProps<{ text?: string }>();
</script>