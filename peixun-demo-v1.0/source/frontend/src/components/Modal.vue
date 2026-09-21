<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import Icon from "./Icon.vue";
defineProps<{ title: string; wide?: boolean }>();
const emit = defineEmits(["close"]);
const close = (e: KeyboardEvent) => {
  if (e.key === "Escape") emit("close");
};
onMounted(() => document.addEventListener("keydown", close));
onUnmounted(() => document.removeEventListener("keydown", close));
</script>
<template>
  <Teleport to="body"
    ><div class="modal-overlay" @click.self="emit('close')">
      <section
        class="modal"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header>
          <h3>{{ title }}</h3>
          <button class="icon-btn" aria-label="关闭" @click="emit('close')">
            <Icon name="X" />
          </button>
        </header>
        <div class="modal-content"><slot /></div>
        <footer v-if="$slots.footer"><slot name="footer" /></footer>
      </section></div
  ></Teleport>
</template>
