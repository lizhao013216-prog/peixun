<script setup lang="ts">
import Icon from "./Icon.vue";
defineProps<{ title: string; description?: string; icon?: string }>();
</script>
<template>
  <div class="empty-state">
    <div class="empty-icon">
      <Icon :name="icon || 'FolderOpen'" :size="29" />
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description || "创建第一个业务对象，开始本阶段流程。" }}</p>
    <slot />
  </div>
</template>
