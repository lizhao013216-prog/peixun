<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import Icon from "../components/Icon.vue";
const route = useRoute(),
  router = useRouter();
</script>

<template>
  <section class="panel empty-state">
    <Icon name="CircleAlert" :size="38" />
    <h3>没有找到这个页面或业务对象</h3>
    <p v-if="route.query.from">请求地址：{{ route.query.from }}</p>
    <p>系统没有自动选择其他对象替代，请返回对应子系统重新选择。</p>
    <button class="btn primary" @click="router.push('/workbench')">
      返回工作台
    </button>
  </section>
</template>
