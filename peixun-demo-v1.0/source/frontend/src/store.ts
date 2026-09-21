import { reactive } from "vue";
export const store = reactive({
  data: null as any,
  accounts: [] as any[],
  workspaces: [] as any[],
  actor: localStorage.getItem("peixun.actor") || "ADMIN",
  workspace: localStorage.getItem("peixun.workspace") || "demo",
  token: "",
  busy: 0,
  loading: true,
  error: "",
  toast: null as any,
  selectedPlan: "",
  selectedCourse: "",
  selectedAttempt: "",
  selectedRun: "",
  selectedExecution: "",
  selectedArchive: "",
  guide: false,
});
let toastTimer: any;
export function notify(message: string, kind = "success") {
  store.toast = { message, kind };
  clearTimeout(toastTimer);
  toastTimer = setTimeout(
    () => (store.toast = null),
    kind === "error" ? 7000 : 3500,
  );
}
async function request(path: string, options: RequestInit = {}) {
  const headers: any = {
    Authorization: `Bearer ${store.token}`,
    ...options.headers,
  };
  if (options.body && !(options.body instanceof FormData))
    headers["Content-Type"] = "application/json";
  const response = await fetch(`/api/demo${path}`, { ...options, headers });
  const contentType = response.headers.get("Content-Type") || "";
  const body = contentType.includes("json")
    ? await response.json()
    : await response.text();
  if (!response.ok) {
    const error: any = new Error(body?.error?.message || "服务暂时不可用");
    error.code = body?.error?.code;
    error.status = response.status;
    throw error;
  }
  return body;
}
export async function login(actor = store.actor) {
  const r = await request("/session", {
    method: "POST",
    body: JSON.stringify({ actorId: actor }),
  });
  store.token = r.token;
  store.actor = actor;
  localStorage.setItem("peixun.actor", actor);
}
function update(data: any) {
  if (data.id !== store.workspace) return;
  if (
    store.data?.id === data.id &&
    (data.epoch < store.data.epoch ||
      (data.epoch === store.data.epoch && data.revision < store.data.revision))
  )
    return;
  store.data = data;
  store.error = "";
}
export async function refresh() {
  try {
    const s = await request(
      `/state?workspace=${encodeURIComponent(store.workspace)}`,
    );
    update(s);
  } catch (e: any) {
    if (e.status === 401) {
      await login();
      update(
        await request(
          `/state?workspace=${encodeURIComponent(store.workspace)}`,
        ),
      );
    } else throw e;
  }
}
export async function bootstrap() {
  store.loading = true;
  try {
    const b = await request("/bootstrap");
    store.accounts = b.accounts;
    store.workspaces = b.workspaces;
    if (!b.workspaces.some((w: any) => w.id === store.workspace))
      store.workspace = b.workspaces[0].id;
    await login();
    await refresh();
  } catch (e: any) {
    store.error = e.message;
  } finally {
    store.loading = false;
  }
}
export async function refreshWorkspaceList() {
  const data = await request("/bootstrap");
  store.workspaces = data.workspaces;
}
export async function command(
  action: string,
  payload: any = {},
  message = "操作已保存",
) {
  store.busy++;
  const commandId = crypto.randomUUID();
  try {
    let r: any;
    for (let i = 0; i < 2; i++) {
      try {
        r = await request(
          `/commands?workspace=${encodeURIComponent(store.workspace)}`,
          {
            method: "POST",
            body: JSON.stringify({
              action,
              payload,
              commandId,
              runEpoch: store.data.epoch,
              expectedRevision: store.data.revision,
            }),
          },
        );
        break;
      } catch (e: any) {
        if (e.code === "REVISION_CONFLICT" && i === 0) {
          await refresh();
          continue;
        }
        throw e;
      }
    }
    update(r.state);
    if (message) notify(message);
    return r.data;
  } catch (e: any) {
    notify(e.message, "error");
    if (e.status === 409) await refresh().catch(() => {});
    return null;
  } finally {
    store.busy--;
  }
}
export async function switchWorkspace(id: string) {
  store.workspace = id;
  store.data = null;
  store.selectedPlan = store.selectedCourse = store.selectedAttempt = "";
  store.selectedRun = store.selectedExecution = store.selectedArchive = "";
  localStorage.setItem("peixun.workspace", id);
  await refresh();
}
export async function newWorkspace(name: string, clone = false) {
  try {
    const s = await request("/workspaces", {
      method: "POST",
      body: JSON.stringify({ name, cloneFrom: clone ? store.workspace : "" }),
    });
    store.workspaces.push({ id: s.id, name: s.name });
    await switchWorkspace(s.id);
    notify("已创建独立工作区");
    return s;
  } catch (e: any) {
    notify(e.message, "error");
  }
}
export async function upload(file: File) {
  const f = new FormData();
  f.append("file", file);
  store.busy++;
  try {
    const r = await request(
      `/uploads?workspace=${encodeURIComponent(store.workspace)}`,
      { method: "POST", body: f },
    );
    update(r.state);
    notify("文件已入库，模型解析由平台模拟器承接");
  } catch (e: any) {
    notify(e.message, "error");
  } finally {
    store.busy--;
  }
}
export async function download(collection: string, id: string) {
  try {
    const text = await request(
      `/reports/${collection}/${id}.md?workspace=${encodeURIComponent(store.workspace)}`,
    );
    const a = document.createElement("a");
    a.href = URL.createObjectURL(
      new Blob([text], { type: "text/markdown;charset=utf-8" }),
    );
    a.download = `${id}.md`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  } catch (e: any) {
    notify(e.message, "error");
  }
}
export const fmt = (v: any, digits = 0) =>
  v == null
    ? "—"
    : Number(v).toLocaleString("zh-CN", { maximumFractionDigits: digits });
export const time = (v: string) =>
  v
    ? new Date(v).toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
export function selected(items: any[], id: string) {
  return items.find((x) => x.id === id) || items.at(-1);
}

export async function downloadAsset(asset: any) {
  try {
    const r = await fetch(
      `/api/demo/assets/${encodeURIComponent(asset.id)}/file?workspace=${encodeURIComponent(store.workspace)}`,
      { headers: { Authorization: `Bearer ${store.token}` } },
    );
    if (!r.ok) throw new Error("文件读取失败");
    const a = document.createElement("a");
    a.href = URL.createObjectURL(await r.blob());
    a.download = asset.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  } catch (e: any) {
    notify(e.message, "error");
  }
}
