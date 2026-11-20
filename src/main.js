import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { homeDir } from "@tauri-apps/api/path";
import { open, save } from "@tauri-apps/plugin-dialog";

const ARCHIVE_EXTS = [
  "7z", "zip", "rar", "tar", "gz", "bz2", "xz", "wim", "iso", "cab", "arj",
  "lzh", "z", "tgz", "tbz", "txz", "r00", "001",
];

const I18N = {
  zh: {
    up: "上移", add: "添加", extract: "解压", test: "测试", open: "打开",
    hash: "哈希", bench: "基准", shell: "右键集成", colName: "名称",
    colSize: "大小", colType: "类型", colDate: "修改时间", dropHint: "将文件拖放到此处",
    dropHintSub: "拖入压缩包可打开，拖入文件可添加到压缩包", dropRelease: "松开以继续",
    compressTitle: "创建压缩包", archivePath: "压缩包路径", items: "要添加的项目",
    browse: "浏览…", addFiles: "添加文件…", addFolder: "添加文件夹…", clear: "清空",
    format: "格式", level: "压缩级别", method: "压缩方法", password: "密码",
    encryptHeaders: "加密文件名（仅 7z）", sfx: "创建自解压 .exe（仅 7z）",
    split: "分卷大小（留空不分卷）", custom: "自定义…", cancel: "取消", start: "开始",
    extractTitle: "解压", dest: "解压到", overwrite: "覆盖已存在的文件",
    passwordTitle: "需要密码", ok: "确定", close: "关闭", about: "关于",
    aboutDesc: "基于官方 7-Zip 的扁平化现代压缩软件",
    aboutLicense: "本软件集成了 7-Zip。7-Zip 版权归 Igor Pavlov 所有，采用 GNU LGPL 许可证分发，源代码可在 www.7-zip.org 获取。",
    folder: "文件夹", file: "文件", items_sel: "已选 {n} 项", items_total: "{n} 项",
    ready: "就绪", done: "完成", failed: "失败", testing: "测试中…", hashing: "计算哈希中…",
    benchmarking: "基准测试中…", compressing: "压缩中…", extracting: "解压中…",
    noSelection: "请先选择文件", wrongPassword: "密码错误或需要密码", shellOn: "右键集成：已启用",
    shellOff: "右键集成：已关闭", parent: "上级目录", archive: "压缩包", noArchives: "没有可解压的压缩包",
  },
  en: {
    up: "Up", add: "Add", extract: "Extract", test: "Test", open: "Open",
    hash: "Hash", bench: "Bench", shell: "Shell", colName: "Name",
    colSize: "Size", colType: "Type", colDate: "Modified", dropHint: "Drop files here",
    dropHintSub: "Drop an archive to open it, or files to add them to an archive",
    dropRelease: "Release to continue",
    compressTitle: "Create Archive", archivePath: "Archive path", items: "Items to add",
    browse: "Browse…", addFiles: "Add files…", addFolder: "Add folder…", clear: "Clear",
    format: "Format", level: "Compression level", method: "Method", password: "Password",
    encryptHeaders: "Encrypt file names (7z only)", sfx: "Create self-extracting .exe (7z only)",
    split: "Volume size (empty = none)", custom: "Custom…", cancel: "Cancel", start: "Start",
    extractTitle: "Extract", dest: "Extract to", overwrite: "Overwrite existing files",
    passwordTitle: "Password required", ok: "OK", close: "Close", about: "About",
    aboutDesc: "A flat, modern archiver built on official 7-Zip",
    aboutLicense: "This app bundles 7-Zip. 7-Zip is Copyright (C) Igor Pavlov, licensed under GNU LGPL; source at www.7-zip.org.",
    folder: "Folder", file: "File", items_sel: "{n} selected", items_total: "{n} items",
    ready: "Ready", done: "Done", failed: "Failed", testing: "Testing…", hashing: "Hashing…",
    benchmarking: "Benchmarking…", compressing: "Compressing…", extracting: "Extracting…",
    noSelection: "Please select files first", wrongPassword: "Wrong password or password required",
    shellOn: "Shell integration: enabled", shellOff: "Shell integration: disabled",
    parent: "Parent", archive: "Archive", noArchives: "No archives to extract",
  },
};

const state = {
  lang: "zh",
  mode: "fs", // "fs" | "archive"
  currentPath: "",
  archivePath: "",
  entries: [],
  selection: new Set(),
  busy: false,
};

function t(key) {
  return (I18N[state.lang] && I18N[state.lang][key]) || key;
}

const $ = (id) => document.getElementById(id);
const els = {};
["engineVersion", "filelistBody", "emptyState", "statusCount", "statusInfo",
 "address", "progressWrap", "progressFill", "progressText", "dropOverlay",
 "modalBackdrop", "compressDlg", "extractDlg", "pwdDlg", "textDlg", "aboutDlg",
 "aboutVersion", "textDlgTitle", "textDlgBody"].forEach((id) => (els[id] = $(id)));

function fmtSize(n) {
  if (n == null || isNaN(n)) return "";
  if (n < 1024) return n + " B";
  const u = ["KB", "MB", "GB", "TB"];
  let i = -1;
  do { n /= 1024; i++; } while (n >= 1024 && i < u.length - 1);
  return n.toFixed(n < 10 ? 1 : 0) + " " + u[i];
}

function fmtDate(secs) {
  if (!secs) return "";
  const d = new Date(secs * 1000);
  const p = (x) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

function extOf(name) {
  const i = name.lastIndexOf(".");
  return i > 0 ? name.slice(i + 1).toLowerCase() : "";
}

function basename(p) {
  const parts = String(p || "").split(/[\\/]/);
  return parts[parts.length - 1];
}

function typeOf(name, isDir) {
  if (isDir) return t("folder");
  const e = extOf(name);
  if (e === "7z") return "7z " + t("archive");
  if (e === "zip") return "zip " + t("archive");
  if (e === "rar") return "rar " + t("archive");
  if (ARCHIVE_EXTS.includes(e)) return e.toUpperCase() + " " + t("archive");
  if (["txt", "md", "log", "ini", "json", "js", "css", "html"].includes(e)) return "文本文件";
  if (["exe", "dll", "msi"].includes(e)) return "应用程序";
  if (["png", "jpg", "jpeg", "gif", "bmp", "svg", "webp"].includes(e)) return "图片";
  if (["mp3", "wav", "flac", "mp4", "mkv", "avi"].includes(e)) return "媒体文件";
  return e ? e.toUpperCase() + " " + t("file") : t("file");
}

function iconOf(name, isDir) {
  if (isDir) {
    return '<svg viewBox="0 0 16 16" width="16" height="16" fill="#f59e0b"><path d="M2 4.5A1.5 1.5 0 0 1 3.5 3h2.6l1.5 2H13a1 1 0 0 1 1 1v6.5A1.5 1.5 0 0 1 12.5 14h-9A1.5 1.5 0 0 1 2 12.5z"/></svg>';
  }
  if (ARCHIVE_EXTS.includes(extOf(name))) {
    return '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="#4f46e5" stroke-width="1.4"><rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/><line x1="6" y1="6.5" x2="10" y2="6.5"/><line x1="6" y1="10" x2="10" y2="10"/></svg>';
  }
  return '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="#9ca3af" stroke-width="1.4" stroke-linejoin="round"><path d="M4 2h5l3 3v9H4z"/><path d="M9 2v3h3"/></svg>';
}

function isArchiveName(name) {
  return ARCHIVE_EXTS.includes(extOf(name));
}

function applyI18n() {
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const k = el.getAttribute("data-i18n");
    if (el.tagName === "INPUT" || el.tagName === "TEXTAREA") el.placeholder = t(k);
    else el.textContent = t(k);
  });
  $("btnLang").textContent = state.lang === "zh" ? "EN" : "中";
  render();
}

function toast(msg, isErr = false) {
  let el = document.createElement("div");
  el.textContent = msg;
  el.style.cssText = `position:fixed;bottom:40px;left:50%;transform:translateX(-50%);background:${isErr ? "#dc2626" : "#1f2937"};color:#fff;padding:8px 16px;border-radius:8px;font-size:12.5px;z-index:200;box-shadow:0 4px 16px rgba(0,0,0,.2);`;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 3000);
}

/* ===== Modal helpers ===== */
function showModal(id) {
  els.modalBackdrop.classList.remove("hidden");
  $(id).classList.remove("hidden");
}
function hideModal(id) {
  $(id).classList.add("hidden");
  if (!document.querySelector(".modal:not(.hidden)")) els.modalBackdrop.classList.add("hidden");
}
function closeAllModals() {
  document.querySelectorAll(".modal").forEach((m) => m.classList.add("hidden"));
  els.modalBackdrop.classList.add("hidden");
}
document.querySelectorAll("[data-close]").forEach((btn) => {
  btn.addEventListener("click", () => hideModal(btn.getAttribute("data-close")));
});
els.modalBackdrop.addEventListener("click", closeAllModals);

/* ===== Progress ===== */
function setProgress(pct, text) {
  if (pct == null) {
    els.progressWrap.classList.add("hidden");
    return;
  }
  els.progressWrap.classList.remove("hidden");
  els.progressFill.style.width = Math.min(100, Math.max(0, pct)) + "%";
  els.progressText.textContent = pct + "%";
  if (text) els.statusInfo.textContent = text;
}
async function initProgressListener() {
  await listen("archive://progress", (e) => {
    const { percent, file } = e.payload;
    setProgress(percent, file || "");
  });
}

/* ===== Rendering ===== */
function render() {
  const body = els.filelistBody;
  body.innerHTML = "";
  els.emptyState.classList.toggle("hidden", state.entries.length > 0);

  for (const entry of state.entries) {
    const name = entry.name ?? basename(entry.path);
    const isDir = entry.is_dir ?? entry.folder ?? false;

    const tr = document.createElement("tr");
    if (state.selection.has(entry.path)) tr.classList.add("selected");

    const tdName = document.createElement("td");
    tdName.className = "col-name";
    const fname = document.createElement("div");
    fname.className = "fname";
    fname.innerHTML = `<span class="f-icon">${iconOf(name, isDir)}</span><span>${escapeHtml(name)}</span>${entry.encrypted ? '<span class="enc-badge">AES</span>' : ""}`;
    tdName.appendChild(fname);

    const tdSize = document.createElement("td");
    tdSize.className = "col-size";
    tdSize.textContent = isDir ? "" : fmtSize(entry.size);

    const tdType = document.createElement("td");
    tdType.className = "col-type";
    tdType.textContent = typeOf(name, isDir);

    const tdDate = document.createElement("td");
    tdDate.className = "col-date";
    tdDate.textContent = state.mode === "archive" ? entry.modified?.slice(0, 19) ?? "" : fmtDate(entry.modified_secs);

    tr.append(tdName, tdSize, tdType, tdDate);
    tr.addEventListener("click", (ev) => {
      if (ev.ctrlKey || ev.metaKey) {
        state.selection.has(entry.path) ? state.selection.delete(entry.path) : state.selection.add(entry.path);
      } else {
        state.selection = new Set([entry.path]);
      }
      render();
      updateStatus();
    });
    tr.addEventListener("dblclick", () => onActivate(entry));
    body.appendChild(tr);
  }
  updateStatus();
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function updateStatus() {
  const n = state.selection.size;
  els.statusCount.textContent = n > 0 ? t("items_sel").replace("{n}", n) : t("items_total").replace("{n}", state.entries.length);
}

/* ===== Navigation ===== */
async function navigate(path) {
  try {
    state.entries = await invoke("list_dir", { path });
    state.mode = "fs";
    state.currentPath = path;
    state.archivePath = "";
    state.selection.clear();
    els.address.value = path;
    render();
  } catch (e) {
    toast(e, true);
  }
}

async function openArchive(path, password) {
  try {
    const entries = await invoke("list_archive", { archive: path, password: password ?? null });
    state.entries = entries;
    state.mode = "archive";
    state.archivePath = path;
    state.selection.clear();
    els.address.value = path;
    render();
    return true;
  } catch (e) {
    const msg = String(e);
    if (/password|密码/i.test(msg)) {
      promptPassword((pwd) => openArchive(path, pwd), () => {});
      return false;
    }
    toast(msg, true);
    return false;
  }
}

function onActivate(entry) {
  if (state.mode === "archive") return; // v1: no drill-down in archive
  if (entry.is_dir) {
    navigate(entry.path);
  } else if (isArchiveName(entry.name)) {
    openArchive(entry.path);
  } else {
    invoke("open_path", { path: entry.path }).catch((e) => toast(e, true));
  }
}

/* ===== Actions ===== */
async function actionOpen() {
  const picked = await open({ multiple: false, filters: [{ name: t("archive"), extensions: ARCHIVE_EXTS }] });
  if (typeof picked === "string") openArchive(picked);
}

async function actionExtract() {
  if (state.mode !== "archive") { toast(t("noArchives"), true); return; }
  const base = state.archivePath.replace(/\.[^.]+$/, "");
  $("eDest").value = base + "\\";
  $("ePassword").value = "";
  $("eOverwrite").checked = true;
  showModal("extractDlg");
}

async function actionTest() {
  if (state.mode !== "archive") { toast(t("noArchives"), true); return; }
  await runOp(t("testing"), () => invoke("test_archive", { archive: state.archivePath, password: null }));
}

async function actionHash() {
  let paths = [];
  if (state.mode === "fs") {
    paths = [...state.selection];
    if (paths.length === 0) { toast(t("noSelection"), true); return; }
  } else {
    paths = [state.archivePath];
  }
  await runOp(t("hashing"), async () => {
    const out = await invoke("hash_files", { paths, algo: "SHA256" });
    $("textDlgTitle").textContent = "SHA-256";
    $("textDlgBody").textContent = out;
    showModal("textDlg");
  });
}

async function actionBenchmark() {
  await runOp(t("benchmarking"), async () => {
    const out = await invoke("benchmark");
    $("textDlgTitle").textContent = t("bench");
    $("textDlgBody").textContent = out;
    showModal("textDlg");
  });
}

/* Wrap an async operation with busy state + progress reset. */
async function runOp(label, fn) {
  if (state.busy) return;
  state.busy = true;
  setProgress(0, label);
  try {
    await fn();
    setProgress(null);
  } catch (e) {
    setProgress(null);
    toast(String(e), true);
  } finally {
    state.busy = false;
  }
}

/* ===== Compress ===== */
function openCompressDialog(initialItems = []) {
  $("cArchive").value = state.mode === "fs" && state.currentPath
    ? state.currentPath.replace(/[\\/]$/, "") + "\\新建压缩包.7z"
    : "新建压缩包.7z";
  setCompressItems(initialItems.length ? initialItems : [...state.selection]);
  $("cFormat").value = "7z";
  $("cLevel").value = "5";
  $("cMethod").value = "";
  $("cPassword").value = "";
  $("cEncryptHeaders").checked = false;
  $("cSfx").checked = false;
  $("cSplit").value = "";
  $("cSplitCustom").classList.add("hidden");
  $("cSplitCustom").value = "";
  showModal("compressDlg");
}

let compressItems = [];
function setCompressItems(items) {
  compressItems = items;
  $("cItems").value = items.join("\n");
}

async function doCompress() {
  const archive = $("cArchive").value.trim();
  if (!archive) { toast(t("archivePath"), true); return; }
  if (compressItems.length === 0) { toast(t("noSelection"), true); return; }
  let split = $("cSplit").value;
  if (split === "custom") split = $("cSplitCustom").value.trim();
  const options = {
    format: $("cFormat").value,
    level: Number($("cLevel").value),
    method: $("cMethod").value || null,
    password: $("cPassword").value || null,
    encryptHeaders: $("cEncryptHeaders").checked,
    split: split || null,
    sfx: $("cSfx").checked,
  };
  hideModal("compressDlg");
  await runOp(t("compressing"), () => invoke("compress", { archive, inputs: compressItems, options }));
  if (state.mode === "fs") navigate(state.currentPath);
}

/* ===== Extract ===== */
async function doExtract() {
  const dest = $("eDest").value.trim();
  const password = $("ePassword").value || null;
  const overwrite = $("eOverwrite").checked;
  hideModal("extractDlg");
  await runOp(t("extracting"), () => invoke("extract", { archive: state.archivePath, dest, password, overwrite }));
}

/* ===== Password prompt ===== */
function promptPassword(onOk, onCancel) {
  $("pwdInput").value = "";
  showModal("pwdDlg");
  $("pwdOk").onclick = () => {
    const pwd = $("pwdInput").value;
    hideModal("pwdDlg");
    onOk(pwd);
  };
}

/* ===== Shell integration ===== */
async function refreshShellState() {
  try {
    const on = await invoke("is_shell_registered");
    $("btnShell").classList.toggle("tb-primary", on);
    $("btnShell").title = on ? t("shellOn") : t("shellOff");
  } catch (_) {}
}
async function toggleShell() {
  try {
    const on = await invoke("is_shell_registered");
    if (on) { await invoke("unregister_shell"); toast(t("shellOff")); }
    else { await invoke("register_shell"); toast(t("shellOn")); }
    refreshShellState();
  } catch (e) { toast(String(e), true); }
}

/* ===== Drag & drop ===== */
async function initDrop() {
  await getCurrentWebview().onDragDropEvent((event) => {
    const { type, paths } = event.payload;
    if (type === "over") {
      els.dropOverlay.classList.remove("hidden");
    } else if (type === "drop") {
      els.dropOverlay.classList.add("hidden");
      handleDropped(paths);
    } else {
      els.dropOverlay.classList.add("hidden");
    }
  });
}

function handleDropped(paths) {
  if (!paths || paths.length === 0) return;
  if (paths.length === 1 && isArchiveName(paths[0].split(/[\\/]/).pop())) {
    openArchive(paths[0]);
  } else {
    openCompressDialog(paths);
  }
}

/* ===== CLI args (right-click / file association) ===== */
function handleArgs(args) {
  if (!args || !args.length) return;
  const last = args[args.length - 1];
  if (args.includes("--add") && last) {
    openCompressDialog([last]);
  } else if (args.includes("--extract-here") && last) {
    invoke("extract", { archive: last, dest: last.replace(/[\\/][^\\/]+$/, ""), password: null, overwrite: true }).catch((e) => toast(e, true));
  } else if (args.includes("--extract-to") && last) {
    invoke("extract", { archive: last, dest: last.replace(/\.[^.]+$/, "") + "\\", password: null, overwrite: true }).catch((e) => toast(e, true));
  } else if (last && !last.startsWith("-")) {
    // File association: double-clicked an archive (or a folder) in Explorer.
    if (isArchiveName(basename(last))) openArchive(last);
    else navigate(last);
  }
}

/* ===== Wire up UI ===== */
function wire() {
  const win = getCurrentWindow();
  $("btnMin").onclick = () => win.minimize();
  $("btnMax").onclick = () => win.toggleMaximize();
  $("btnClose").onclick = () => win.close();

  $("btnLang").onclick = () => { state.lang = state.lang === "zh" ? "en" : "zh"; applyI18n(); };
  $("btnAbout").onclick = () => showModal("aboutDlg");
  $("btnUp").onclick = () => {
    if (state.mode === "archive") {
      const dir = state.archivePath.replace(/[\\/][^\\/]+$/, "");
      navigate(dir || state.currentPath || "C:\\");
    } else if (state.currentPath) {
      const parent = state.currentPath.replace(/[\\/][^\\/]+[\\/]?$/, "");
      navigate(parent || "C:\\");
    }
  };
  $("btnRefresh").onclick = () => state.mode === "archive" ? openArchive(state.archivePath) : navigate(state.currentPath);
  $("address").addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      const v = els.address.value.trim();
      if (isArchiveName(v.split(/[\\/]/).pop())) openArchive(v);
      else navigate(v);
    }
  });

  $("btnOpen").onclick = actionOpen;
  $("btnExtract").onclick = actionExtract;
  $("btnTest").onclick = actionTest;
  $("btnHash").onclick = actionHash;
  $("btnBench").onclick = actionBenchmark;
  $("btnShell").onclick = toggleShell;

  $("btnAdd").onclick = () => openCompressDialog();
  $("cBrowse").onclick = async () => {
    const p = await save({ defaultPath: $("cArchive").value || "新建压缩包.7z", filters: [{ name: t("archive"), extensions: [($("cFormat").value) || "7z"] }] });
    if (p) $("cArchive").value = p;
  };
  $("cAddFiles").onclick = async () => {
    const picked = await open({ multiple: true });
    if (picked) setCompressItems([...new Set([...compressItems, ...(Array.isArray(picked) ? picked : [picked])])]);
  };
  $("cAddFolder").onclick = async () => {
    const picked = await open({ directory: true });
    if (picked) setCompressItems([...new Set([...compressItems, picked])]);
  };
  $("cClearItems").onclick = () => setCompressItems([]);
  $("cSplit").addEventListener("change", () => $("cSplitCustom").classList.toggle("hidden", $("cSplit").value !== "custom"));
  $("cStart").onclick = doCompress;

  $("eBrowse").onclick = async () => {
    const p = await open({ directory: true });
    if (p) $("eDest").value = p + "\\";
  };
  $("eStart").onclick = doExtract;
}

/* ===== Init ===== */
async function init() {
  wire();
  applyI18n();
  await initProgressListener();
  await initDrop();
  await listen("cli://args", (e) => handleArgs(e.payload));

  // Auto-register Explorer context menu + file association on first launch.
  try {
    const on = await invoke("is_shell_registered");
    if (!on) {
      await invoke("register_shell");
      toast(t("shellOn"));
    }
  } catch (_) {}
  refreshShellState();

  try {
    const info = await invoke("get_engine_info");
    els.engineVersion.textContent = info.version.replace(/^7-Zip\s*/, "").split(" :")[0] || info.version;
    $("aboutVersion").textContent = "引擎：" + info.version;
  } catch (_) {}

  let start = "C:\\";
  try { start = await homeDir(); } catch (_) {}
  await navigate(start);
  handleArgs(await invoke("get_startup_args").catch(() => []));
}

init();
