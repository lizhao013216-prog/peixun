<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { guide, currentTour, currentStep, guidePath } from "../guide";
import { store, login, notify } from "../store";
import Icon from "./Icon.vue";
const router = useRouter(),
  route = useRoute();
const tour = computed(currentTour),
  step = computed(currentStep),
  switching = ref(false);
const role = computed(
  () =>
    store.accounts.find((a) => a.id === step.value.role)?.name ||
    step.value.role,
);
const samePage = computed(() => route.meta.id === step.value.page);
async function useRole() {
  switching.value = true;
  try {
    await login(step.value.role);
    notify(`已切换为${role.value}`);
  } catch (e: any) {
    notify(e.message, "error");
  } finally {
    switching.value = false;
  }
}
function move(offset: number) {
  guide.index = Math.max(
    0,
    Math.min(tour.value.steps.length - 1, guide.index + offset),
  );
  guide.collapsed = false;
  router.push(guidePath());
  window.scrollTo({ top: 0, behavior: "smooth" });
}
</script>
<template>
  <section class="demo-guide" aria-label="当前演示步骤">
    <div class="demo-guide-heading">
      <span class="guide-step-number">{{ guide.index + 1 }}</span>
      <div class="guide-heading-copy">
        <span
          >{{ tour.title }} ·
          {{ guide.mode === "present" ? "讲解已有案例" : "跟随操作" }} ·
          {{ guide.index + 1 }} / {{ tour.steps.length }}</span
        >
        <h2>{{ step.title }}</h2>
      </div>
      <button class="text-btn" @click="router.push('/guide')">全部步骤</button>
      <button
        class="icon-btn"
        :aria-label="guide.collapsed ? '展开步骤说明' : '收起步骤说明'"
        :aria-expanded="!guide.collapsed"
        @click="guide.collapsed = !guide.collapsed"
      >
        <Icon :name="guide.collapsed ? 'ChevronDown' : 'ChevronUp'" />
      </button>
      <button
        class="icon-btn"
        aria-label="退出演示指引"
        @click="guide.active = false"
      >
        <Icon name="X" />
      </button>
    </div>
    <template v-if="!guide.collapsed">
      <div class="guide-instructions">
        <div>
          <b class="guide-label">{{
            guide.mode === "present" ? "这一页怎么讲" : "现在怎么操作"
          }}</b>
          <p v-if="guide.mode === 'present'">{{ step.inspect }}</p>
          <ol v-else>
            <li v-for="action in step.actions" :key="action">{{ action }}</li>
          </ol>
        </div>
        <div class="guide-expected">
          <b class="guide-label">{{
            guide.mode === "present" ? "重点说明" : "操作后应该看到"
          }}</b>
          <p>{{ step.result }}</p>
        </div>
      </div>
      <div class="guide-actions">
        <div class="guide-role" v-if="guide.mode === 'practice'">
          <Icon name="Users" :size="17" />本步身份：<b>{{ role }}</b>
          <button
            v-if="store.actor !== step.role"
            class="btn secondary small"
            :disabled="switching"
            @click="useRole"
          >
            切换为{{ role }}
          </button>
          <span v-else class="role-ready"
            ><Icon name="Check" :size="15" />身份已就绪</span
          >
        </div>
        <span v-else class="guide-note"
          >已有成果可直接讲解；重新操作请在演示中心创建练习空间。</span
        >
        <div class="button-row">
          <button
            v-if="!samePage"
            class="btn secondary"
            @click="router.push(guidePath())"
          >
            返回本步页面
          </button>
          <button
            class="btn secondary"
            :disabled="guide.index === 0"
            @click="move(-1)"
          >
            上一步
          </button>
          <button
            v-if="guide.index < tour.steps.length - 1"
            class="btn primary"
            @click="move(1)"
          >
            下一步<Icon name="ArrowRight" :size="16" />
          </button>
          <button
            v-else
            class="btn primary"
            @click="
              guide.active = false;
              router.push('/guide');
            "
          >
            返回演示中心
          </button>
        </div>
      </div>
      <p v-if="guide.mode === 'practice'" class="guide-footnote">
        请先在下方业务页面完成操作，再点“下一步”。翻页只切换指引，不会自动提交、审核或计分。
      </p>
    </template>
  </section>
</template>
