import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Dropdown,
  Empty,
  Input,
  InputNumber,
  Modal,
  Progress,
  Space,
  Spin,
  Table,
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
  PlusOutlined,
  RobotOutlined,
  RollbackOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { outlineApi, outlineTaskApi } from "../api";
import { streamSse } from "../api/ai";
import { copyText } from "../utils/clipboard";
import type { ChapterOutlineItem, OutlineTaskVO } from "../api/types";

const { Paragraph } = Typography;

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
  const startTask = async (req: { outlineIds?: number[]; chapters?: number }) => {
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
    void startTask({});
  };

  /** 确认从零规划：按章节数创建规划行并逐章生成大纲（不生成正文） */
  const confirmPlan = () => {
    setPlanOpen(false);
    void startTask({ chapters: planChapters });
  };

  /** 单条生成/重生成：走同步接口（一次调用，不会超时）。按钮有 loading，且禁用其他生成入口防重复点击 */
  const generateOne = async (outlineId: number) => {
    if (generatingId !== null) {
      return;
    }
    setGeneratingId(outlineId);
    message.info("正在生成大纲，请稍候（约需十几秒）…");
    try {
      setItems(await outlineApi.generate(novelId, [outlineId]));
      message.success("大纲已生成");
      // 生成大纲会产出有意义的章节名；若该规划已落地章节，通知父组件刷新章节列表
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setGeneratingId(null);
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
  const writeOne = async (item: ChapterOutlineItem, append = false) => {
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
        { totalChapters: items.length },
        {
          onDelta: (d) => setWriteText((t) => t + d),
        },
      );
      message.success(append ? "已接着写完并保存" : "正文已生成并保存");
      // 重新拉取：本条规划刚落地出章节，需取到真实 chapterId 供「去阅读」使用
      await load();
      setLastWrittenId(
        (await outlineApi.list(novelId)).find((x) => x.outlineId === item.outlineId)?.chapterId ?? null,
      );
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      // 只结束「生成中」状态，**不自动关闭弹窗**：
      // 用户需要时间阅读/复制刚生成的内容，自动关闭会让弹窗里的按钮失去意义。
      setWritingId(null);
    }
  };

  /** 从当前内容接着写（追加，不覆盖）：仅在已有正文时可用 */
  const continueWrite = (item: ChapterOutlineItem) => {
    if (!item.hasContent) {
      message.info("本章还没有正文，直接用「生成正文」写一整章即可");
      return;
    }
    writeOne(item, true);
  };

  /** 生成前覆盖确认：章节已有正文时弹窗提示（说明是整章重写），否则直接生成 */
  const confirmWrite = (item: ChapterOutlineItem) => {
    if (item.hasContent) {
      const inst = Modal.confirm({
        title: "覆盖确认",
        content:
          "将基于本章大纲**整章重写**（忽略当前正文，从上一章结尾重新写）。旧内容会自动留版本快照，可回滚；也可以取消后用「更多 → 从当前内容接着写」在原文后追加。确定继续？",
        okText: "整章重写",
        cancelText: "取消",
        okButtonProps: { danger: true },
        maskClosable: true,
        onOk: () => {
          // 确认后立即关闭提示，避免与正文生成弹窗叠加
          inst.destroy();
          writeOne(item);
        },
      });
    } else {
      writeOne(item);
    }
  };

  /** 一键为全部已规划大纲的章节生成正文（顺序执行）；仅在存在大纲时由按钮触发 */
  const writeAll = async () => {
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
          { totalChapters: targets.length },
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
    } catch (e: any) {
      message.error(`第${done} 章生成失败：${e?.message ?? "AI 服务暂时不可用"}`);
    } finally {
      // 同样保留弹窗，让用户查看最后一章内容或复制后再关闭
      setWritingId(null);
    }
  };

  /** 批量生成前覆盖确认：若任一目标章已有正文则弹窗，否则直接生成 */
  const confirmWriteAll = () => {
    const targets = items.filter((i) => i.hasOutline);
    if (targets.some((i) => i.hasContent)) {
      const inst = Modal.confirm({
        title: "覆盖确认",
        content:
          "将为全部已规划大纲的章节生成正文；若某章已有正文，将被覆盖（已自动留版本快照，可回滚）。确定继续？",
        okText: "生成正文",
        cancelText: "取消",
        okButtonProps: { danger: true },
        maskClosable: true,
        onOk: () => {
          // 确认后立即关闭提示，避免与正文生成弹窗叠加
          inst.destroy();
          void writeAll();
        },
      });
    } else {
      writeAll();
    }
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
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="还没有大纲规划。点「一键生成大纲」由 AI 直接规划全书（自动创建规划行），或点「新增大纲」手动写一条；正文会在你点「生成正文」时才生成对应章节。" />
      ) : (
        <Table
          rowKey="outlineId"
          dataSource={items}
          pagination={false}
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
                    ellipsis={{ rows: 3, expandable: true }}
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
                    onClick={() => generateOne(r.outlineId)}
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
                          r.hasContent ? confirmWrite(r) : writeOne(r);
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
          <InputNumber
            min={1}
            max={100}
            step={1}
            value={planChapters}
            onChange={(v) => setPlanChapters(Number(v) || 1)}
            style={{ width: 150 }}
            addonAfter="章"
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
            <InputNumber
              min={100}
              max={20000}
              step={100}
              value={newWords}
              onChange={(v) => setNewWords(Number(v) || 0)}
              style={{ width: 110 }}
              addonAfter="字"
            />
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
              <InputNumber
                min={100}
                max={20000}
                step={100}
                value={editWords}
                onChange={(v) => setEditWords(Number(v) || 0)}
                style={{ width: 110 }}
                addonAfter="字"
              />
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
