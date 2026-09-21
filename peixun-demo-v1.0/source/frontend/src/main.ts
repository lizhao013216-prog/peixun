import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import { pages } from "./catalog";
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
    { path: "/:pathMatch(.*)*", redirect: "/workbench" },
  ],
});
createApp(App).use(router).mount("#app");
