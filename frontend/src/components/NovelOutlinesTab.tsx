import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Drawer,
  Dropdown,
  Empty,
  Input,
  InputNumber,
  Modal,
  Progress,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FileAddOutlined,
  FlagOutlined,
  PlusOutlined,
  RobotOutlined,
  RollbackOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { foreshadowApi, outlineApi, outlineTaskApi } from "../api";
import { streamSse } from "../api/ai";
import { copyText } from "../utils/clipboard";
import { LIST_PAGINATION } from "../utils/pagination";
import type {
  ChapterOutlineItem,
  ForeshadowNewSuggest,
  ForeshadowResolvedSuggest,
  ForeshadowSuggest,
  ForeshadowingItem,
  OutlineTaskVO,
} from "../api/types";

const { Paragraph } = Typography;

/** 生成正文前的待生成目标：单章（可整章重写或接着写）或一键全部 */
type WriteTarget =
  | { mode: "one"; item: ChapterOutlineItem; append: boolean }
  | { mode: "all" };

export default function OutlineTab({
  novelId,
  targetWords,
  onChanged,
}: {
  novelId: number;
  /** 全书目标字数：用于估算「一键从零规划」的建议章节数 */
  targetWords?: number | null;
  /** 正文/章节数据变化后通知父组件刷新字数统计与章节列表 */
  onChanged?: () => void;
}) {
  const navigate = useNavigate();
  const [items, setItems] = useState<ChapterOutlineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<ChapterOutlineItem | null>(null);
  const [editText, setEditText] = useState("");
  const [editTitle, setEditTitle] = useState("");
  const [editWords, setEditWords] = useState<number>(0);
  const [saving, setSaving] = useState(false);
  // 「新增大纲」弹窗：收集章节名 + 大纲内容 + 目标字数，提交后表格新增一行
  const [createOpen, setCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newOutline, setNewOutline] = useState("");
  const [newWords, setNewWords] = useState<number>(0);
  const [creating, setCreating] = useState(false);
  // 章节名行内编辑：标题主场在本页，点击即可改（生成正文时同步到章节）
  const [editingTitleId, setEditingTitleId] = useState<number | null>(null);
  const [editingTitle, setEditingTitle] = useState("");
  const [savingTitle, setSavingTitle] = useState(false);
  const [writeOpen, setWriteOpen] = useState(false);
  const [writeTitle, setWriteTitle] = useState("");
  const [writeText, setWriteText] = useState("");
  const [writingId, setWritingId] = useState<number | null>(null);
  /** 最近一次成功生成的章节 id：生成结束后（writingId 置空）用于「去阅读」跳转 */
  const [lastWrittenId, setLastWrittenId] = useState<number | null>(null);
  const [task, setTask] = useState<OutlineTaskVO | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 本章目标字数编辑
  const [editingWordsId, setEditingWordsId] = useState<number | null>(null);
  const [editingWords, setEditingWords] = useState<number>(0);
  const [savingWords, setSavingWords] = useState(false);
  // 「一键从零规划」：全书还没有规划行时，先确认章节数，再由 AI 逐章规划
  const [planOpen, setPlanOpen] = useState(false);
  const [planChapters, setPlanChapters] = useState(10);
  // 单章「生成/重生成」进行中的规划行 id：按钮 loading + 禁用其他生成入口，避免重复点击
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  // 「作者有话说」弹窗：生成正文前让作者对本章内容加以干预（重点、禁区、写法要求等，可选）
  // 待生成目标：单章（item + 是否接着写）或一键全部
  const [writeCfgTarget, setWriteCfgTarget] = useState<WriteTarget | null>(null);
  // 作者有话说内容：保留上次输入，方便连续多章复用同一批要求
  const [authorNote, setAuthorNote] = useState("");
  // 「作者有话说」弹窗（大纲版）：生成/重生成单条大纲、一键补全缺失大纲前填写
  const [outlineCfgTarget, setOutlineCfgTarget] = useState<
    | { kind: "one"; outlineId: number; title: string }
    | { kind: "all" }
    | null
  >(null);
  // 大纲规划的作者有话说：保留上次输入，方便多章复用；与正文生成的输入相互独立
  const [outlineNote, setOutlineNote] = useState("");
  // 「伏笔账本」抽屉：跨章节悬念登记；OPEN 条目自动注入大纲/正文生成的 prompt
  const [fsOpen, setFsOpen] = useState(false);
  const [fsItems, setFsItems] = useState<ForeshadowingItem[]>([]);
  const [fsLoading, setFsLoading] = useState(false);
  const [fsSaving, setFsSaving] = useState(false);
  // 编辑中的伏笔 id；null 表示处于「新增」模式
  const [fsEditingId, setFsEditingId] = useState<number | null>(null);
  const [fsTitle, setFsTitle] = useState("");
  const [fsContent, setFsContent] = useState("");
  const [fsPlanted, setFsPlanted] = useState<number | null>(null);
  const [fsExpected, setFsExpected] = useState<number | null>(null);
  // 半自动伏笔记账：正文生成完成后 AI 提炼的建议（确认弹窗）；带 checked 供作者勾选
  const [suggOpen, setSuggOpen] = useState(false);
  const [suggChapter, setSuggChapter] = useState("");
  const [suggNew, setSuggNew] = useState<(ForeshadowNewSuggest & { checked: boolean })[]>([]);
  const [suggResolved, setSuggResolved] = useState<(ForeshadowResolvedSuggest & { checked: boolean })[]>([]);
  const [suggApplying, setSuggApplying] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await outlineApi.list(novelId));
    } catch (e: any) {
      message.error(e?.message ?? "加载失败");
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load]);

  const missing = items.filter((i) => !i.hasOutline).length;
  const hasOutlines = items.some((i) => i.hasOutline);
  const running = task != null && ["QUEUED", "RUNNING"].includes(task.status);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  /** 轮询任务进度；任务结束后刷新章节大纲列表 */
  const pollTask = useCallback(
    async (taskId: number) => {
      try {
        const t = await outlineTaskApi.get(taskId);
        setTask(t);
        if (!["QUEUED", "RUNNING"].includes(t.status)) {
          stopPolling();
          load();
          // 大纲生成可能同时替换了占位章节名，需通知父组件刷新章节列表
          onChanged?.();
          if (t.status === "DONE") {
            message.success(
              `大纲生成完成：成功 ${t.succeeded} 章${t.failed > 0 ? `，失败 ${t.failed} 章` : ""}`,
            );
          } else if (t.status === "CANCELLED") {
            message.info(`已取消，完成 ${t.succeeded} 章`);
          } else if (t.status === "FAILED") {
            message.error(t.error ?? "生成失败");
          }
        }
      } catch (e: any) {
        stopPolling();
        message.error(e?.message ?? "查询进度失败");
      }
    },
    [load, onChanged]
  );

  /**
   * 提交异步任务：后台逐条生成，不阻塞页面。
   * - chapters 有值：全书还没有规划行时，先按数量创建规划行再逐章生成（一键从零规划）；
   * - 否则：仅对已存在的规划行生成内容，不创建规划/章节。
   */
  const startTask = async (req: { outlineIds?: number[]; chapters?: number; authorNote?: string }) => {
    try {
      const t = await outlineTaskApi.start(novelId, req);
      setTask(t);
      stopPolling();
      pollRef.current = setInterval(() => pollTask(t.id), 2000);
      message.info("已开始生成大纲，可离开本页，进度会自动刷新");
    } catch (e: any) {
      message.error(e?.message ?? "提交失败");
    }
  };

  const cancelTask = async () => {
    if (!task) return;
    try {
      const t = await outlineTaskApi.cancel(task.id);
      setTask(t);
      message.info("已取消");
    } catch (e: any) {
      message.error(e?.message ?? "取消失败");
    }
  };

  /** 建议规划章节数：按全书目标字数 ÷ 每章约 3000 字估算，夹在 1-100 */
  const suggestedChapters = () => {
    const t = targetWords ?? 0;
    if (t <= 0) {
      return 10;
    }
    return Math.min(100, Math.max(1, Math.ceil(t / 3000)));
  };

  /** 一键生成大纲内容：为所有还没有大纲内容的规划行补全章节名与大纲 */
  const generateAll = () => {
    if (running || generatingId !== null) {
      return;
    }
    if (items.length === 0) {
      // 全书还没有规划：让 AI 从零规划——先确认章节数（后端据此创建规划行），再逐章生成
      setPlanChapters(suggestedChapters());
      setPlanOpen(true);
      return;
    }
    if (missing === 0) {
      message.info("所有规划都已填写大纲内容，无需重复生成");
      return;
    }
    // 先弹「作者有话说」让作者干预规划（可选），确认后开始批量任务
    setOutlineCfgTarget({ kind: "all" });
  };

  /** 确认从零规划：按章节数创建规划行并逐章生成大纲（不生成正文）；「作者有话说」在规划弹窗内填写 */
  const confirmPlan = () => {
    setPlanOpen(false);
    void startTask({ chapters: planChapters, authorNote: outlineNote.trim() || undefined });
  };

  /** 单条生成/重生成前弹窗：填写「作者有话说」（可选）后开始 */
  const openOutlineCfg = (item: ChapterOutlineItem) => {
    if (running || generatingId !== null) {
      return;
    }
    setOutlineCfgTarget({
      kind: "one",
      outlineId: item.outlineId,
      title: `${item.hasOutline ? "重生成大纲" : "生成大纲"} · 第${item.chapterOrder}章${
        item.chapterTitle ? `《${item.chapterTitle}》` : ""
      }`,
    });
  };

  /** 单条生成/重生成：走同步接口（一次调用，不会超时）。按钮有 loading，且禁用其他生成入口防重复点击 */
  const generateOne = async (outlineId: number, note = "") => {
    if (generatingId !== null) {
      return;
    }
    setGeneratingId(outlineId);
    message.info("正在生成大纲，请稍候（约需十几秒）…");
    try {
      setItems(await outlineApi.generate(novelId, [outlineId], note));
      message.success("大纲已生成");
      // 生成大纲会产出有意义的章节名；若该规划已落地章节，通知父组件刷新章节列表
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setGeneratingId(null);
    }
  };

  // ===== 伏笔账本：跨章节悬念登记（OPEN 条目自动注入大纲/正文生成 prompt） =====

  const loadForeshadows = useCallback(async () => {
    setFsLoading(true);
    try {
      setFsItems(await foreshadowApi.list(novelId));
    } catch (e: any) {
      message.error(e?.message ?? "伏笔账本加载失败");
    } finally {
      setFsLoading(false);
    }
  }, [novelId]);

  // 挂载即取一次：工具栏角标需要未回收条数
  useEffect(() => {
    void loadForeshadows();
  }, [loadForeshadows]);

  const resetFsForm = () => {
    setFsEditingId(null);
    setFsTitle("");
    setFsContent("");
    setFsPlanted(null);
    setFsExpected(null);
  };

  const submitForeshadow = async () => {
    if (!fsTitle.trim()) {
      message.warning("请填写伏笔名");
      return;
    }
    setFsSaving(true);
    try {
      const req = {
        title: fsTitle.trim(),
        content: fsContent.trim() || null,
        plantedOrder: fsPlanted,
        expectedOrder: fsExpected,
      };
      if (fsEditingId != null) {
        await foreshadowApi.update(novelId, fsEditingId, req);
        message.success("伏笔已更新");
      } else {
        await foreshadowApi.create(novelId, req);
        message.success("伏笔已登记，生成大纲/正文时会自动带入");
      }
      resetFsForm();
      await loadForeshadows();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setFsSaving(false);
    }
  };

  const editForeshadow = (f: ForeshadowingItem) => {
    setFsEditingId(f.id);
    setFsTitle(f.title);
    setFsContent(f.content ?? "");
    setFsPlanted(f.plantedOrder);
    setFsExpected(f.expectedOrder);
  };

  const changeForeshadowStatus = async (f: ForeshadowingItem, status: ForeshadowingItem["status"]) => {
    try {
      await foreshadowApi.changeStatus(novelId, f.id, status, null);
      message.success(
        status === "RESOLVED" ? "已标记回收" : status === "ABANDONED" ? "已标记放弃" : "已重新打开",
      );
      await loadForeshadows();
    } catch (e: any) {
      message.error(e?.message ?? "操作失败");
    }
  };

  const removeForeshadow = (f: ForeshadowingItem) => {
    Modal.confirm({
      title: `确定删除伏笔「${f.title}」？`,
      content: "删除后不再注入生成上下文；如只是不再回收，建议用「放弃」保留记录。",
      okText: "删除",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await foreshadowApi.remove(novelId, f.id);
          message.success("已删除");
          await loadForeshadows();
        } catch (e: any) {
          message.error(e?.message ?? "删除失败");
        }
      },
    });
  };

  // ===== 半自动伏笔记账：正文生成完成后 AI 提炼「新埋 / 已回收」建议，作者勾选后才写入 =====

  /** 弹出建议确认卡（仅当确有建议时）；chapterOrder 用于回收章回填 */
  const openSuggestModal = (
    suggest: ForeshadowSuggest,
    chapterLabel: string,
    chapterOrder: number | null,
  ) => {
    const newItems = suggest.newItems.map((n) => ({ ...n, checked: true }));
    const resolved = suggest.resolved.map((r) => ({
      ...r,
      checked: true,
      resolvedOrder: r.resolvedOrder ?? chapterOrder,
    }));
    if (newItems.length === 0 && resolved.length === 0) {
      return;
    }
    setSuggChapter(chapterLabel);
    setSuggNew(newItems);
    setSuggResolved(resolved);
    setSuggOpen(true);
  };

  /** 静默请求记账建议：网络层失败也降级为 null，绝不打扰生成主流程 */
  const fetchSuggest = async (chapterId: number | null | undefined) => {
    if (!chapterId) {
      return null;
    }
    try {
      return await foreshadowApi.suggest(novelId, chapterId);
    } catch {
      return null;
    }
  };

  /** 应用勾选的建议：新伏笔逐条登记、回收逐条标记 RESOLVED；完成后刷新账本角标 */
  const applySuggest = async () => {
    setSuggApplying(true);
    try {
      let n = 0;
      for (const it of suggNew.filter((x) => x.checked)) {
        await foreshadowApi.create(novelId, {
          title: it.title,
          content: it.content,
          plantedOrder: it.plantedOrder,
          expectedOrder: null,
        });
        n++;
      }
      for (const it of suggResolved.filter((x) => x.checked)) {
        await foreshadowApi.changeStatus(novelId, it.id, "RESOLVED", it.resolvedOrder);
        n++;
      }
      message.success(n > 0 ? `伏笔记账完成：已写入 ${n} 条` : "未选择任何建议");
      setSuggOpen(false);
      await loadForeshadows();
    } catch (e: any) {
      message.error(e?.message ?? "记账失败，请到伏笔账本手动处理");
    } finally {
      setSuggApplying(false);
    }
  };

  // 组件卸载时清理轮询
  useEffect(() => stopPolling, []);

  const openEdit = (item: ChapterOutlineItem) => {
    setEditing(item);
    setEditText(item.outline ?? "");
    setEditTitle(item.chapterTitle ?? "");
    // 默认填当前生效字数；未单独设定时沿用全书均摊，用户可在此覆盖
    setEditWords(item.wordCount ?? 0);
    setEditOpen(true);
  };

  const save = async () => {
    if (!editing) {
      return;
    }
    if (!editText.trim()) {
      message.warning("请填写大纲内容");
      return;
    }
    setSaving(true);
    try {
      // 章节名、大纲内容与目标字数一并保存（字数传 null 表示沿用全书均摊，对应后端 @Min(100) 约束）
      await outlineApi.save(
        novelId,
        editing.outlineId,
        editText.trim(),
        editWords > 0 ? editWords : null,
        editTitle.trim() || null,
      );
      message.success(editing.hasOutline ? "已保存" : "大纲已创建");
      setEditOpen(false);
      setEditing(null);
      load();
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSaving(false);
    }
  };

  /** 开始编辑规划章节名 */
  const startEditTitle = (item: ChapterOutlineItem) => {
    setEditingTitleId(item.outlineId);
    setEditingTitle(item.chapterTitle ?? "");
  };

  /**
   * 保存规划章节名：只改大纲，不动已生成的章节正文。
   * 下次生成正文时，章节标题会以这里的名字为准（单向同步）。
   */
  const commitTitle = async () => {
    if (editingTitleId == null) {
      return;
    }
    const item = items.find((x) => x.outlineId === editingTitleId);
    if (!item) {
      setEditingTitleId(null);
      return;
    }
    const title = editingTitle.trim();
    if (!title) {
      message.warning("章节名不能为空（留空则由 AI 自动起名）");
      return;
    }
    if (title === (item.chapterTitle ?? "")) {
      setEditingTitleId(null);
      return;
    }
    setSavingTitle(true);
    try {
      await outlineApi.saveTitle(novelId, item.outlineId, title);
      message.success("章节名已更新（生成正文时会同步到章节）");
      setEditingTitleId(null);
      setItems(await outlineApi.list(novelId));
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSavingTitle(false);
    }
  };

  /** 提交「新增大纲」：只建规划行，不创建章节；章节在生成正文时才落地 */
  const submitCreate = async () => {
    if (!newOutline.trim() && !newTitle.trim()) {
      message.warning("请至少填写章节名或大纲内容");
      return;
    }
    setCreating(true);
    try {
      setItems(
        await outlineApi.create(novelId, {
          chapterTitle: newTitle.trim() || null,
          outline: newOutline.trim(),
          targetWords: newWords > 0 ? newWords : null,
        }),
      );
      message.success("已新增大纲，可直接生成正文");
      setCreateOpen(false);
    } catch (e: any) {
      message.error(e?.message ?? "新增失败");
    } finally {
      setCreating(false);
    }
  };

  /** 一键复制预览中的正文（剪贴板不可用时降级为 execCommand，见 utils/clipboard） */
  const copyWriteText = async () => {
    const text = writeText;
    if (!text.trim()) {
      message.warning("没有可复制的内容");
      return;
    }
    try {
      await copyText(text);
      message.success("正文已复制到剪贴板");
    } catch (e: any) {
      message.error("复制失败，请手动选中文本复制");
    }
  };

  /** 开始编辑本条目标字数：默认填当前生效值，未设置过则填全书均摊值 */
  const startEditWords = (item: ChapterOutlineItem) => {
    setEditingWordsId(item.outlineId);
    setEditingWords(item.wordCount ?? 3000);
  };

  /** 保存本条目标字数（独立于大纲，可只改字数不动大纲） */
  const commitWords = async (outlineId: number) => {
    if (!editingWords || editingWords < 100) {
      message.warning("请输入不小于 100 的字数");
      return;
    }
    setSavingWords(true);
    try {
      await outlineApi.saveWords(novelId, outlineId, editingWords);
      message.success("本章目标字数已更新");
      setEditingWordsId(null);
      load();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSavingWords(false);
    }
  };

  /** 恢复为全书均摊值 */
  const resetWords = async (outlineId: number) => {
    try {
      await outlineApi.saveWords(novelId, outlineId, 0);
      message.success("已恢复为全书均摊值");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "恢复失败");
    }
  };

  const remove = async (outlineId: number) => {
    try {
      await outlineApi.remove(novelId, outlineId);
      message.success("已删除");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "删除失败");
    }
  };

  /**
   * 生成整章正文：流式输出并落库。
   * - append=false（默认）：**整章重写**，忽略已有正文，从上一章结尾重新写
   * - append=true：从本章当前内容末尾**接着写**（只增不重）
   * 两种模式都会留版本快照，可回滚。
   */
  const writeOne = async (item: ChapterOutlineItem, append = false, note = "") => {
    setWriteOpen(true);
    setWriteTitle(
      append
        ? `AI 接着写 · 第${item.chapterOrder}章《${item.chapterTitle}》`
        : `AI 生成正文 · 第${item.chapterOrder}章《${item.chapterTitle}》`,
    );
    setWriteText("");
    setWritingId(item.outlineId);
    try {
      await streamSse(
        `/api/ai/novels/${novelId}/outlines/${item.outlineId}/${
          append ? "write-continue" : "write"
        }`,
        { totalChapters: items.length, authorNote: note.trim() || undefined },
        {
          onDelta: (d) => setWriteText((t) => t + d),
        },
      );
      message.success(append ? "已接着写完并保存" : "正文已生成并保存");
      // 重新拉取：本条规划刚落地出章节，需取到真实 chapterId 供「去阅读」使用
      await load();
      const freshId =
        (await outlineApi.list(novelId)).find((x) => x.outlineId === item.outlineId)?.chapterId ?? null;
      setLastWrittenId(freshId);
      onChanged?.();
      // 半自动伏笔记账：本章写完后请求 AI 建议（静默，仅确有建议时弹确认卡）
      const suggest = await fetchSuggest(freshId);
      if (suggest) {
        openSuggestModal(suggest, `第${item.chapterOrder}章《${item.chapterTitle}》`, item.chapterOrder);
      }
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      // 只结束「生成中」状态，**不自动关闭弹窗**：
      // 用户需要时间阅读/复制刚生成的内容，自动关闭会让弹窗里的按钮失去意义。
      setWritingId(null);
    }
  };

  /** 从当前内容接着写（追加，不覆盖）：仅在已有正文时可用；先经「作者有话说」弹窗再开始 */
  const continueWrite = (item: ChapterOutlineItem) => {
    if (!item.hasContent) {
      message.info("本章还没有正文，直接用「生成正文」写一整章即可");
      return;
    }
    setWriteCfgTarget({ mode: "one", item, append: true });
  };

  /**
   * 生成前弹窗：填写「作者有话说」（可选）后开始生成。
   * 章节已有正文时弹窗内提示整章重写（旧内容自动留版本快照，可回滚），否则直接进入填写。
   */
  const confirmWrite = (item: ChapterOutlineItem) => {
    setWriteCfgTarget({ mode: "one", item, append: false });
  };

  /** 一键为全部已规划大纲的章节生成正文（顺序执行）；「作者有话说」对每章生效 */
  const writeAll = async (note = "") => {
    const targets = items.filter((i) => i.hasOutline);
    if (targets.length === 0) {
      message.warning("还没有章节大纲，请先点「一键生成大纲」规划后再生成正文");
      return;
    }
    setWriteOpen(true);
    setWriteText("");
    setWritingId(-1);
    let done = 0;
    try {
      for (const it of targets) {
        done++;
        setWriteTitle(
          `AI 生成正文 · 第${it.chapterOrder}章《${it.chapterTitle}》（${done}/${targets.length}）`,
        );
        setWriteText("");
        await streamSse(
          `/api/ai/novels/${novelId}/outlines/${it.outlineId}/write`,
          { totalChapters: targets.length, authorNote: note.trim() || undefined },
          {
            onDelta: (d) => setWriteText((t) => t + d),
          },
        );
      }
      message.success("正文已全部生成并保存");
      const fresh = await outlineApi.list(novelId);
      setItems(fresh);
      setLastWrittenId(
        fresh.find((x) => x.outlineId === targets[targets.length - 1]?.outlineId)?.chapterId ?? null,
      );
      onChanged?.();
      // 半自动伏笔记账：整批写完后逐章收集建议，合并为一张确认卡（静默失败，不阻断）
      const hide = message.loading("正在分析伏笔记账建议…", 0);
      const mergedNew: (ForeshadowNewSuggest & { checked: boolean })[] = [];
      const mergedResolved: (ForeshadowResolvedSuggest & { checked: boolean })[] = [];
      const seenIds = new Set<number>();
      for (const it of targets) {
        const s = await fetchSuggest(fresh.find((x) => x.outlineId === it.outlineId)?.chapterId);
        if (!s) {
          continue;
        }
        s.newItems.forEach((n) => mergedNew.push({ ...n, checked: true }));
        s.resolved.forEach((r) => {
          if (!seenIds.has(r.id)) {
            seenIds.add(r.id);
            mergedResolved.push({
              ...r,
              checked: true,
              resolvedOrder: r.resolvedOrder ?? it.chapterOrder,
            });
          }
        });
      }
      hide();
      if (mergedNew.length > 0 || mergedResolved.length > 0) {
        setSuggChapter(`本次批量生成的 ${targets.length} 章`);
        setSuggNew(mergedNew);
        setSuggResolved(mergedResolved);
        setSuggOpen(true);
      }
    } catch (e: any) {
      message.error(`第${done} 章生成失败：${e?.message ?? "AI 服务暂时不可用"}`);
    } finally {
      // 同样保留弹窗，让用户查看最后一章内容或复制后再关闭
      setWritingId(null);
    }
  };

  /** 批量生成前弹窗：填写「作者有话说」（可选，对每章生效）；任一目标章已有正文时弹窗内提示覆盖 */
  const confirmWriteAll = () => {
    setWriteCfgTarget({ mode: "all" });
  };

  return (
    <div className="section-card">
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 12,
          marginBottom: 14,
        }}
      >
        <b style={{ whiteSpace: "nowrap", flexShrink: 0 }}>章节大纲</b>
        <Space size={8} style={{ flexShrink: 0 }}>
          <Tooltip title="手动新增一条章节大纲规划，提交后表格新增一行（只建大纲，章节在生成正文时才创建）">
            <Button
              icon={<PlusOutlined />}
              onClick={() => {
                setNewTitle("");
                setNewOutline("");
                setNewWords(0);
                setCreateOpen(true);
              }}
            >
              新增大纲
            </Button>
          </Tooltip>
          <Tooltip title="由 AI 逐章规划全书（章节名 + 写作大纲）。还没有规划时会先按章节数创建规划行；每章一次 AI 调用，开始后可随时取消">
            <Button
              type="primary"
              icon={<RobotOutlined />}
              loading={running}
              disabled={running || generatingId !== null}
              onClick={generateAll}
            >
              一键生成大纲{missing > 0 ? `（${missing} 条待生成）` : ""}
            </Button>
          </Tooltip>
          <Badge
            count={fsItems.filter((f) => f.status === "OPEN").length}
            size="small"
            offset={[-2, 6]}
          >
            <Tooltip title="伏笔账本：登记跨章节悬念。未回收条目会自动注入大纲/正文生成的 prompt，避免长程伏笔被 AI 遗忘">
              <Button icon={<FlagOutlined />} onClick={() => setFsOpen(true)}>
                伏笔账本
              </Button>
            </Tooltip>
          </Badge>
          <Tooltip
            title={
              hasOutlines
                ? "按每条大纲规划生成整章正文；尚未生成的章节会被自动创建（已有正文会被重写，旧内容可回滚）"
                : "请先有大纲内容：正文需要依据大纲写作，避免跑偏"
            }
          >
            <span>
              <Button
                icon={<FileAddOutlined />}
                disabled={!hasOutlines}
                onClick={() => confirmWriteAll()}
              >
                一键生成正文
              </Button>
            </span>
          </Tooltip>
        </Space>
      </div>
      {running && task && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
              <span>
                正在生成大纲：第 {task.currentIndex} / {task.total || "?"} 章
                {task.currentTitle ? `《${task.currentTitle}》` : ""}
                {task.failed > 0 ? `（失败 ${task.failed} 章）` : ""}
              </span>
              <Progress
                percent={
                  task.total > 0 ? Math.round((task.currentIndex / task.total) * 100) : 0
                }
                size="small"
                style={{ width: 160 }}
              />
              <Button size="small" danger onClick={cancelTask}>
                取消
              </Button>
            </div>
          }
          description="大纲逐章生成中，可离开本页；每章一次 AI 调用，章数越多耗时越久。"
        />
      )}
      {items.length === 0 && loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="还没有大纲规划。点「一键生成大纲」由 AI 直接规划全书（自动创建规划行），或点「新增大纲」手动写一条；正文会在你点「生成正文」时才生成对应章节。" />
      ) : (
        <Table
          rowKey="outlineId"
          dataSource={items}
          loading={loading}
          pagination={LIST_PAGINATION}
          columns={[
            { title: "#", dataIndex: "chapterOrder", width: 60 },
            {
              title: (
                <Tooltip title="章节名以本页为准：点击编辑图标即可修改，生成正文时会同步为章节标题。标题过长时会省略显示，悬浮可查看全文">
                  章节名
                </Tooltip>
              ),
              dataIndex: "chapterTitle",
              width: 190,
              render: (t: string | null, r: ChapterOutlineItem) => {
                if (editingTitleId === r.outlineId) {
                  return (
                    <Space.Compact size="small">
                      <Input
                        size="small"
                        maxLength={120}
                        value={editingTitle}
                        autoFocus
                        onChange={(e) => setEditingTitle(e.target.value)}
                        onPressEnter={() => commitTitle()}
                        style={{ width: 112 }}
                      />
                      <Button size="small" type="primary" loading={savingTitle} onClick={() => commitTitle()}>
                        保存
                      </Button>
                      <Button size="small" onClick={() => setEditingTitleId(null)}>
                        取消
                      </Button>
                    </Space.Compact>
                  );
                }
                const display = t || `第${r.chapterOrder}章${r.hasContent ? "（已生成）" : "（未命名）"}`;
                // 长标题不截断溢出：超出列宽显示省略号，悬浮 Tooltip 展示全文
                return (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 6, maxWidth: "100%" }}>
                    <Tooltip
                      title={
                        t
                          ? `${display}${t.length > 12 ? "\n点击编辑图标可修改章节名；生成正文时会用这个名字作为章节标题" : ""}`
                          : "尚未命名，点击编辑图标补充章节名"
                      }
                    >
                      <a
                        onClick={() => startEditTitle(r)}
                        style={{
                          minWidth: 0,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {t ? display : <span className="muted">{display}</span>}
                      </a>
                    </Tooltip>
                    <Tooltip title="点击编辑章节名（生成正文时会同步为章节标题）">
                      <EditOutlined
                        style={{ color: "#1677ff", cursor: "pointer", flexShrink: 0 }}
                        onClick={() => startEditTitle(r)}
                      />
                    </Tooltip>
                  </span>
                );
              },
            },
            {
              title: (
                <Tooltip title="点击编辑图标可修改本章目标字数；默认 = 全书总目标字数 ÷ 章节数">
                  预计字数
                </Tooltip>
              ),
              dataIndex: "wordCount",
              width: 130,
              render: (w: number | null, r: ChapterOutlineItem) => {
                if (editingWordsId === r.outlineId) {
                  return (
                    <Space.Compact size="small">
                      <InputNumber
                        size="small"
                        min={100}
                        max={20000}
                        step={100}
                        value={editingWords}
                        autoFocus
                        style={{ width: 86 }}
                        onChange={(v) => setEditingWords(Number(v) || 0)}
                        onPressEnter={() => commitWords(r.outlineId)}
                      />
                      <Button
                        size="small"
                        type="primary"
                        loading={savingWords}
                        onClick={() => commitWords(r.outlineId)}
                      >
                        保存
                      </Button>
                      <Button size="small" onClick={() => setEditingWordsId(null)}>
                        取消
                      </Button>
                    </Space.Compact>
                  );
                }
                if (!w) {
                  return (
                    <Space size={4}>
                      <span className="muted">—</span>
                      <Tooltip title="点击编辑图标单独设定本章目标字数">
                        <EditOutlined
                          style={{ color: "#1677ff", cursor: "pointer" }}
                          onClick={() => startEditWords(r)}
                        />
                      </Tooltip>
                    </Space>
                  );
                }
                return (
                  <Space size={4}>
                    <span>{Math.round(w).toLocaleString()}</span>
                    <Tooltip title={r.wordCountCustomized ? "本章单独设定，点击编辑图标可修改" : "沿用全书均摊，点击编辑图标可单独设定"}>
                      <EditOutlined
                        style={{ color: "#1677ff", cursor: "pointer" }}
                        onClick={() => startEditWords(r)}
                      />
                    </Tooltip>
                    {r.wordCountCustomized && (
                      <Tooltip title="恢复为全书均摊值">
                        <Button
                          type="link"
                          size="small"
                          icon={<RollbackOutlined />}
                          onClick={() => resetWords(r.outlineId)}
                        />
                      </Tooltip>
                    )}
                  </Space>
                );
              },
            },
            {
              title: "大纲（写作前的章节规划，指导正文、避免跑偏）",
              dataIndex: "outline",
              render: (_: any, r: ChapterOutlineItem) =>
                r.hasOutline && r.outline ? (
                  <Paragraph
                    style={{ margin: 0, whiteSpace: "pre-wrap" }}
                    // expandable: "collapsible"：展开后显示「收起」，可再次折叠回 3 行
                    ellipsis={{ rows: 3, expandable: "collapsible" }}
                  >
                    {r.outline}
                  </Paragraph>
                ) : (
                  <span className="muted">未填写（点「生成」由 AI 规划，或点「编辑」手动写）</span>
                ),
            },
            {
              title: "操作",
              width: 260,
              render: (_: any, r: ChapterOutlineItem) => (
                <Space>
                  <Button
                    type="link"
                    size="small"
                    icon={<RobotOutlined />}
                    loading={generatingId === r.outlineId}
                    disabled={generatingId !== null}
                    onClick={() => openOutlineCfg(r)}
                  >
                    {r.hasOutline ? "重生成" : "生成"}
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openEdit(r)}
                  >
                    编辑
                  </Button>
                  <Dropdown
                    menu={{
                      items: [{
                        key: "generate",
                        label: r.hasContent ? "整章重写" : "生成正文",
                        icon: <FileAddOutlined />,
                        danger: false,
                      },
                        {
                          key: "append",
                          label: "从当前内容接着写",
                          icon: <EditOutlined />,
                          danger: false,
                          // 没有正文时「接着写」无意义
                          disabled: !r.hasContent,
                        },
                        r.chapterId
                          ? {
                              key: "read",
                              label: "阅读本章",
                              icon: <EyeOutlined />,
                              danger: false,
                            }
                          : null,
                        {
                          key: "divider",
                          type: "divider" as const,
                        },
                        {
                          key: "delete",
                          label: "删除这条大纲",
                          icon: <DeleteOutlined />,
                          danger: true,
                        },
                      ].filter(Boolean) as any,
                      onClick: ({ key }) => {
                        if (key === "delete") {
                          Modal.confirm({
                            title: `确定删除第${r.chapterOrder}章的大纲？`,
                            content: r.hasContent
                              ? "该章正文已生成，删除大纲不会删除正文，但正文将不再按规划重写。"
                              : "仅删除这条规划，不会创建或影响章节。",
                            okText: "删除",
                            okButtonProps: { danger: true },
                            onOk: () => remove(r.outlineId),
                          });
                        } else if (key === "write") {
                          confirmWrite(r);
                        } else if (key === "append") {
                          continueWrite(r);
                        } else if (key === "read" && r.chapterId) {
                          navigate(`/read/${r.chapterId}`);
                        } else if (key === "generate") {
                          confirmWrite(r);
                        }
                      },
                    }}
                  >
                    <Button type="link" size="small">
                      更多
                    </Button>
                  </Dropdown>
                </Space>
              ),
            },
          ]}
        />
      )}
      {/* 伏笔账本：跨章节悬念登记；OPEN 条目自动注入大纲/正文生成的 prompt */}
      <Drawer
        title="伏笔账本"
        // 桌面 960px 保证「埋设描述」列有充足空间；小屏回退到 92vw 防溢出
        width="min(960px, 92vw)"
        open={fsOpen}
        onClose={() => setFsOpen(false)}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 14 }}
          message="未回收的伏笔会自动注入大纲与正文生成的上下文，AI 会优先承接并在合适的章节回收——解决「很多章之前埋的悬念被遗忘」的问题。"
        />
        <div
          style={{
            marginBottom: 16,
            padding: 12,
            border: "1px solid #f0f0f0",
            borderRadius: 8,
            background: "#fafafa",
          }}
        >
          <div className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
            {fsEditingId != null ? `编辑伏笔（#${fsEditingId}）` : "登记新伏笔"}
          </div>
          <Input
            placeholder="伏笔名（如：师父的身世之谜）"
            value={fsTitle}
            onChange={(e) => setFsTitle(e.target.value)}
            maxLength={60}
            showCount
            style={{ marginBottom: 8 }}
          />
          <Input.TextArea
            placeholder="埋设描述：埋了什么、以什么形式呈现、回收时应给出什么（可选）"
            value={fsContent}
            onChange={(e) => setFsContent(e.target.value)}
            maxLength={500}
            showCount
            autoSize={{ minRows: 2, maxRows: 4 }}
            style={{ marginBottom: 8 }}
          />
          <Space style={{ marginBottom: 8 }} wrap size={4}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
              <span className="muted" style={{ fontSize: 12 }}>埋于第</span>
              <InputNumber
                min={1}
                max={10000}
                value={fsPlanted}
                onChange={(v) => setFsPlanted(v == null ? null : Number(v))}
                placeholder="可空"
                style={{ width: 110 }}
              />
              <span className="muted" style={{ fontSize: 12 }}>章</span>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
              <span className="muted" style={{ fontSize: 12 }}>预期第</span>
              <InputNumber
                min={1}
                max={10000}
                value={fsExpected}
                onChange={(v) => setFsExpected(v == null ? null : Number(v))}
                placeholder="可空"
                style={{ width: 110 }}
              />
              <span className="muted" style={{ fontSize: 12 }}>章前后回收</span>
            </span>
          </Space>
          <div>
            <Button type="primary" loading={fsSaving} onClick={submitForeshadow}>
              {fsEditingId != null ? "保存修改" : "登记"}
            </Button>
            {fsEditingId != null && (
              <Button style={{ marginLeft: 8 }} onClick={resetFsForm}>
                取消编辑
              </Button>
            )}
          </div>
        </div>
        <Table
          size="small"
          rowKey="id"
          loading={fsLoading}
          dataSource={fsItems}
          pagination={LIST_PAGINATION}
          locale={{
            emptyText: (
              <Empty description="还没有登记伏笔。埋设悬念后登记在这里，AI 生成时就不会遗忘" />
            ),
          }}
          columns={[
            {
              title: "埋设章",
              dataIndex: "plantedOrder",
              width: 70,
              render: (v: number | null) => v ?? "—",
            },
            {
              title: "伏笔名",
              dataIndex: "title",
              width: 160,
              render: (t: string, r: ForeshadowingItem) => (
                <Tooltip title={r.content ?? t}>
                  <span style={{ fontWeight: 500 }}>{t}</span>
                </Tooltip>
              ),
            },
            {
              title: "埋设描述",
              dataIndex: "content",
              render: (c: string | null) =>
                c ? (
                  <Paragraph
                    style={{ margin: 0, whiteSpace: "pre-wrap" }}
                    ellipsis={{ rows: 2, expandable: "collapsible" }}
                  >
                    {c}
                  </Paragraph>
                ) : (
                  <span className="muted">—</span>
                ),
            },
            {
              title: "预期回收",
              dataIndex: "expectedOrder",
              width: 80,
              render: (v: number | null) => (v == null ? "—" : `第 ${v} 章`),
            },
            {
              title: "状态",
              dataIndex: "status",
              width: 110,
              render: (s: ForeshadowingItem["status"], r: ForeshadowingItem) =>
                s === "OPEN" ? (
                  <Tag color="orange">未回收</Tag>
                ) : s === "RESOLVED" ? (
                  <Tag color="green">已回收{r.resolvedOrder ? `·第${r.resolvedOrder}章` : ""}</Tag>
                ) : (
                  <Tag>已放弃</Tag>
                ),
            },
            {
              title: "操作",
              width: 190,
              render: (_: any, r: ForeshadowingItem) => (
                <Space size={0}>
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={() => editForeshadow(r)}>
                    编辑
                  </Button>
                  {r.status === "OPEN" && (
                    <>
                      <Button
                        type="link"
                        size="small"
                        onClick={() => changeForeshadowStatus(r, "RESOLVED")}
                      >
                        标记回收
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        onClick={() => changeForeshadowStatus(r, "ABANDONED")}
                      >
                        放弃
                      </Button>
                    </>
                  )}
                  {r.status !== "OPEN" && (
                    <Button type="link" size="small" onClick={() => changeForeshadowStatus(r, "OPEN")}>
                      重新打开
                    </Button>
                  )}
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeForeshadow(r)} />
                </Space>
              ),
            },
          ]}
        />
      </Drawer>
      {/* 生成正文前设置：填写「作者有话说」（可选）对创作加以干预，同时承担覆盖确认职责 */}
      <Modal
        title={
          writeCfgTarget?.mode === "all"
            ? `一键生成正文（${items.filter((i) => i.hasOutline).length} 章）`
            : writeCfgTarget?.mode === "one"
              ? writeCfgTarget.append
                ? `AI 接着写 · 第${writeCfgTarget.item.chapterOrder}章《${writeCfgTarget.item.chapterTitle}》`
                : `AI 生成正文 · 第${writeCfgTarget.item.chapterOrder}章《${writeCfgTarget.item.chapterTitle}》`
              : ""
        }
        open={writeCfgTarget !== null}
        onCancel={() => setWriteCfgTarget(null)}
        onOk={() => {
          const t = writeCfgTarget;
          if (!t) return;
          setWriteCfgTarget(null);
          if (t.mode === "all") {
            void writeAll(authorNote);
          } else {
            void writeOne(t.item, t.append, authorNote);
          }
        }}
        okText={writeCfgTarget?.mode === "one" && writeCfgTarget.append ? "开始接着写" : "开始生成"}
        cancelText="取消"
        width={560}
        maskClosable={true}
        destroyOnHidden
      >
        {writeCfgTarget?.mode === "one" && !writeCfgTarget.append && writeCfgTarget.item.hasContent && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="将基于本章大纲整章重写（忽略当前正文，从上一章结尾重新写）。旧内容会自动留版本快照，可回滚；想追加请用「更多 → 从当前内容接着写」。"
          />
        )}
        {writeCfgTarget?.mode === "one" && writeCfgTarget.append && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="将从本章当前内容末尾接着写（只增不重）。"
          />
        )}
        {writeCfgTarget?.mode === "all" && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="将为全部已规划大纲的章节按顺序生成正文；若某章已有正文，将被覆盖（已自动留版本快照，可回滚）。"
          />
        )}
        <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
          作者有话说（可选，AI 将优先遵循）
        </div>
        <Input.TextArea
          value={authorNote}
          onChange={(e) => setAuthorNote(e.target.value)}
          placeholder={
            "向 AI 交代你对本章的要求，例如：\n· 本章重点写主角与反派的正面交锋，感情线先按下不表；\n· 多写对话，节奏紧凑，不要大段环境描写；\n· 某配角本章不要出场 / 结尾留在悬念上。"
          }
          maxLength={500}
          showCount
          autoSize={{ minRows: 4, maxRows: 8 }}
        />
      </Modal>
      {/* 生成大纲前设置：填写「作者有话说」（可选）对规划加以干预（单条 / 一键补全） */}
      <Modal
        title={
          outlineCfgTarget?.kind === "one"
            ? outlineCfgTarget.title
            : outlineCfgTarget?.kind === "all"
              ? `一键生成大纲（${missing} 条待生成）`
              : ""
        }
        open={outlineCfgTarget !== null}
        onCancel={() => setOutlineCfgTarget(null)}
        onOk={() => {
          const t = outlineCfgTarget;
          if (!t) return;
          setOutlineCfgTarget(null);
          if (t.kind === "all") {
            void startTask({ authorNote: outlineNote.trim() || undefined });
          } else {
            void generateOne(t.outlineId, outlineNote);
          }
        }}
        okText="开始生成"
        cancelText="取消"
        width={560}
        maskClosable={true}
        destroyOnHidden
      >
        {outlineCfgTarget?.kind === "all" && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={`将为所有大纲内容为空的规划行生成章节名与写作大纲（共 ${missing} 条）；每章一次 AI 调用，开始后可离开本页，进度自动刷新。`}
          />
        )}
        <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
          作者有话说（可选，AI 将优先遵循）
        </div>
        <Input.TextArea
          value={outlineNote}
          onChange={(e) => setOutlineNote(e.target.value)}
          placeholder={
            "向 AI 交代你对本章规划的要求，例如：\n· 本章必须出现主角获得关键道具的事件；\n· 回收伏笔账本里第 3 章埋的「师父的身世」；\n· 本章不要安排感情戏，重点推进势力冲突。"
          }
          maxLength={500}
          showCount
          autoSize={{ minRows: 4, maxRows: 8 }}
        />
      </Modal>
      {/* 半自动伏笔记账：AI 对刚写完的正文提炼的建议，作者勾选确认后才写入账本 */}
      <Modal
        title={suggChapter ? `伏笔记账建议 · ${suggChapter}` : "伏笔记账建议"}
        open={suggOpen}
        onCancel={() => setSuggOpen(false)}
        onOk={applySuggest}
        okButtonProps={{ disabled: suggApplying }}
        confirmLoading={suggApplying}
        okText="写入账本"
        cancelText="忽略"
        width={560}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {suggNew.length > 0 && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>AI 认为本章埋下了以下悬念（建议登记）</div>
              {suggNew.map((n, i) => (
                <label
                  key={i}
                  style={{ display: "flex", gap: 8, alignItems: "flex-start", padding: "4px 0", cursor: "pointer" }}
                >
                  <Checkbox
                    checked={n.checked}
                    onChange={(e) =>
                      setSuggNew((arr) => arr.map((x, j) => (j === i ? { ...x, checked: e.target.checked } : x)))
                    }
                    style={{ marginTop: 4 }}
                  />
                  <span>
                    <b>{n.title}</b>
                    {n.content && (
                      <span style={{ color: "var(--text-secondary, #888)" }}>：{n.content}</span>
                    )}
                    {n.plantedOrder != null && (
                      <span style={{ color: "var(--text-secondary, #888)" }}>（埋于第 {n.plantedOrder} 章）</span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          )}
          {suggResolved.length > 0 && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>AI 认为以下伏笔在本章已回收（建议标记）</div>
              {suggResolved.map((r, i) => (
                <label
                  key={r.id}
                  style={{ display: "flex", gap: 8, alignItems: "flex-start", padding: "4px 0", cursor: "pointer" }}
                >
                  <Checkbox
                    checked={r.checked}
                    onChange={(e) =>
                      setSuggResolved((arr) =>
                        arr.map((x, j) => (j === i ? { ...x, checked: e.target.checked } : x)),
                      )
                    }
                    style={{ marginTop: 4 }}
                  />
                  <span>
                    <b>{r.title}</b>
                    {r.resolvedOrder != null && (
                      <span style={{ color: "var(--text-secondary, #888)" }}>（回收于第 {r.resolvedOrder} 章）</span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          )}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            以上为 AI 分析建议，仅勾选项会写入伏笔账本；也可以忽略，稍后在「伏笔账本」中手动维护。
          </Typography.Text>
        </div>
      </Modal>
      <Modal
        title={writeTitle || "AI 生成正文"}
        open={writeOpen}
        onCancel={() => setWriteOpen(false)}
        footer={
          <Space>
            <Button
              icon={<CopyOutlined />}
              disabled={writingId !== null || !writeText.trim()}
              onClick={copyWriteText}
            >
              复制正文
            </Button>
            <Button
              type="primary"
              icon={<EyeOutlined />}
              disabled={writingId !== null || !writeText.trim()}
              onClick={() => {
                if (lastWrittenId) {
                  setWriteOpen(false);
                  navigate(`/read/${lastWrittenId}`);
                }
              }}
            >
              去阅读
            </Button>
            <Button
              type="primary"
              ghost={writingId !== null}
              onClick={() => setWriteOpen(false)}
            >
              关闭
            </Button>
          </Space>
        }
        width={760}
        maskClosable={false}
        closable={writingId === null}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            marginBottom: 10,
            color: writingId !== null ? "#1677ff" : "#999",
          }}
        >
          {writingId !== null && <Spin size="small" />}
          <span>
            {writingId !== null
              ? "正在根据大纲生成正文，请勿关闭…"
              : "正文已生成并保存"}
          </span>
          {writeText.trim() && (
            <span className="muted" style={{ marginLeft: "auto", fontSize: 12 }}>
              共 {writeText.replace(/\s/g, "").length.toLocaleString()} 字
            </span>
          )}
        </div>
        <Input.TextArea
          readOnly
          value={writeText}
          autoSize={{ minRows: 14, maxRows: 24 }}
          style={{ fontFamily: "inherit" }}
          placeholder="（实时显示 AI 生成的正文）"
        />
      </Modal>
      {/* 一键从零规划：全书还没有规划行时，先确认章节数，再逐章生成大纲 */}
      <Modal
        title="AI 规划全书章节大纲"
        open={planOpen}
        onCancel={() => setPlanOpen(false)}
        onOk={confirmPlan}
        okText="开始规划"
        confirmLoading={running}
        width={520}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 14 }}
          message="将逐章生成「章节名 + 写作大纲」，并自动创建对应数量的规划行。"
          description="不会生成正文；之后每章点「生成正文」才按规划写作。每章一次 AI 调用，章数越多耗时越久，开始后可随时取消。"
        />
        <div>
          <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
            规划章节数（按全书目标 {targetWords ? targetWords.toLocaleString() : 0} 字 ÷
            每章约 3,000 字估算，可修改）
          </div>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
            <InputNumber
              min={1}
              max={100}
              step={1}
              value={planChapters}
              onChange={(v) => setPlanChapters(Number(v) || 1)}
              style={{ width: 110 }}
            />
            <span className="muted" style={{ fontSize: 12 }}>章</span>
          </span>
        </div>
        <div style={{ marginTop: 14 }}>
          <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
            作者有话说（可选，AI 将优先遵循，对每章规划生效）
          </div>
          <Input.TextArea
            value={outlineNote}
            onChange={(e) => setOutlineNote(e.target.value)}
            placeholder={
              "向 AI 交代全书规划的要求，例如：\n· 每 3 章左右安排一次小高潮；\n· 女主在第 5 章前不要登场；\n· 各章结尾都留悬念，但第 10 章给一个阶段性收束。"
            }
            maxLength={500}
            showCount
            autoSize={{ minRows: 3, maxRows: 8 }}
          />
        </div>
      </Modal>
      {/* 新增大纲：收集章节名 + 大纲内容 + 目标字数，提交后表格新增一行 */}
      <Modal
        title={`新增章节大纲（第 ${items.length + 1} 章）`}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={submitCreate}
        okText="新增"
        confirmLoading={creating}
        width={720}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="这里新增的是一条「章节大纲规划」，提交后表格新增一行。章节正文会在你点「生成正文」时才创建。"
        />
        <div style={{ marginBottom: 12 }}>
          <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
            章节名（选填）
          </div>
          <Input
            maxLength={120}
            allowClear
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            placeholder="留空则由 AI 在生成大纲时自动起名"
          />
        </div>
        <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
          大纲内容（选填，留空可稍后让 AI 规划）
        </div>
        <Input.TextArea
          rows={10}
          maxLength={2000}
          showCount
          value={newOutline}
          onChange={(e) => setNewOutline(e.target.value)}
          placeholder={`建议按下面四点写，情节更容易闭合：
1. 开篇：承接上一章结尾，交代本章处境与目标
2. 推进：本章要发生的关键事件与场景
3. 收束：本章主要矛盾的阶段性结果——必须有结果，不能悬着
4. 钩子：在结果之后为下一章留一个悬念`}
        />
        <div style={{ marginTop: 12 }}>
          <Space size={8} wrap>
            <span className="muted" style={{ fontSize: 12 }}>
              本章目标字数：
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
              <InputNumber
                min={100}
                max={20000}
                step={100}
                value={newWords}
                onChange={(v) => setNewWords(Number(v) || 0)}
                style={{ width: 110 }}
              />
              <span className="muted" style={{ fontSize: 12 }}>字</span>
            </span>
            <span className="muted" style={{ fontSize: 12 }}>
              留空则沿用全书均摊值
            </span>
          </Space>
        </div>
      </Modal>
      <Modal
        title={
          editing
            ? `编辑大纲 · 第${editing.chapterOrder}章${editing.chapterTitle ? `《${editing.chapterTitle}》` : ""}`
            : "新增大纲"
        }
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={save}
        okText="保存"
        confirmLoading={saving}
        width={720}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 10 }}
          message="手动大纲会作为本章的写作依据（AI 生成正文时会严格遵循）。建议按下面四点写，情节更容易闭合："
        />
        <div style={{ marginBottom: 12 }}>
          <div className="muted" style={{ fontSize: 12, marginBottom: 4 }}>
            章节名
          </div>
          <Input
            maxLength={120}
            allowClear
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
            placeholder="留空则由 AI 在生成大纲时自动起名"
          />
        </div>
        <div className="muted" style={{ fontSize: 12, marginBottom: 10, lineHeight: 1.9 }}>
          1. 开篇：从上一章结尾接着写，交代本章处境与目标<br />
          2. 推进：本章要发生的关键事件与场景<br />
          3. 收束：本章主要矛盾的阶段性结果（解决 / 失败 / 反转）——必须有结果，不能悬着<br />
          4. 钩子：在结果之后为下一章留一个悬念
        </div>
        <Input.TextArea
          rows={12}
          maxLength={2000}
          showCount
          value={editText}
          onChange={(e) => setEditText(e.target.value)}
          placeholder={`例如：
1. 开篇：承接上一章结尾，主角发现…
2. 推进：…
3. 收束：本章矛盾以…告一段落
4. 钩子：留下一条新线索…`}
        />
        {editing && (
          <div style={{ marginTop: 12 }}>
            <Space size={8} wrap>
              <span className="muted" style={{ fontSize: 12 }}>
                本章目标字数：
              </span>
              <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                <InputNumber
                  min={100}
                  max={20000}
                  step={100}
                  value={editWords}
                  onChange={(v) => setEditWords(Number(v) || 0)}
                  style={{ width: 110 }}
                />
                <span className="muted" style={{ fontSize: 12 }}>字</span>
              </span>
              {editing.wordCountCustomized && (
                <Button
                  size="small"
                  type="link"
                  onClick={() => setEditWords(editing.wordCount ?? 0)}
                >
                  恢复全书均摊
                </Button>
              )}
              <span className="muted" style={{ fontSize: 12 }}>
                留空则沿用全书均摊值
              </span>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  );
}
