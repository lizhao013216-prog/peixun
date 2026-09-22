<script setup lang="ts">
import { computed } from "vue";
import { statusText } from "../catalog";
const props = defineProps<{ status: string }>();
const color = computed(() =>
  /PUBLISHED|APPROVED$|COMPLETED|ACCEPTED|CONNECTED|CONFIRMED|SUCCEEDED|CLOSED/.test(
    props.status,
  )
    ? "green"
    : /FAIL|ALARM|RETURNED|RECTIFICATION/.test(props.status)
      ? "red"
      : /PENDING|SUBMITTED|REVIEW|DISPATCHED|BUILDING|PROCESSING/.test(
            props.status,
          )
        ? "amber"
        : /RUNNING|READY|ASSIGNED|ISSUED|PLANNED|IN_PROGRESS/.test(props.status)
          ? "blue"
          : "gray",
);
</script>
<template>
  <span class="badge" :class="color"
    ><i></i>{{ statusText[status] || status }}</span
  >
</template>
