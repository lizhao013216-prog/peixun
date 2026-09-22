import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { legacyRoutes, pages, pathFor } from "./catalog";
import "./style.css";
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/workbench" },
    ...pages.map((p) => ({
      path: p.path,
      component: { template: "<div />" },
      meta: p,
    })),
    ...legacyRoutes.map((legacy) => ({
      path: legacy.path,
      redirect: (to: any) =>
        pathFor(legacy.targetId, String(to.params.id || "current")),
    })),
    {
      path: "/not-found",
      component: { template: "<div />" },
      meta: {
        id: "NOT_FOUND",
        title: "页面不存在",
        system: null,
        feature: "not-found",
        view: "not-found",
        scope: "PUBLIC",
        section: "工作空间",
        icon: "CircleAlert",
        description: "请从当前系统导航重新选择页面",
      },
    },
    {
      path: "/:pathMatch(.*)*",
      redirect: (to) => ({ path: "/not-found", query: { from: to.fullPath } }),
    },
  ],
});
createApp(App).use(router).mount("#app");
