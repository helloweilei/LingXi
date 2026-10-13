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
  DeleteOutlined,
  EditOutlined,
  FileAddOutlined,
  RobotOutlined,
  RollbackOutlined,
} from "@ant-design/icons";
import { outlineApi, outlineTaskApi } from "../api";
import { streamSse } from "../api/ai";
import type { ChapterOutlineItem, OutlineTaskVO } from "../api/types";

const { Paragraph } = Typography;

export default function OutlineTab({
  novelId,
  onChanged,
}: {
  novelId: number;
  /** 正文/章节数据变化后通知父组件刷新字数统计与章节列表 */
  onChanged?: () => void;
}) {
  const [items, setItems] = useState<ChapterOutlineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<ChapterOutlineItem | null>(null);
  const [editText, setEditText] = useState("");
  const [saving, setSaving] = useState(false);
  const [writeOpen, setWriteOpen] = useState(false);
  const [writeTitle, setWriteTitle] = useState("");
  const [writeText, setWriteText] = useState("");
  const [writingId, setWritingId] = useState<number | null>(null);
  const [task, setTask] = useState<OutlineTaskVO | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 本章目标字数编辑
  const [editingWordsId, setEditingWordsId] = useState<number | null>(null);
  const [editingWords, setEditingWords] = useState<number>(0);
  const [savingWords, setSavingWords] = useState(false);

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

  /** 提交异步任务：后台逐章生成，不阻塞页面（仅对已存在的章节生成，不创建章节） */
  const startTask = async (req: { chapterIds?: number[] }) => {
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

  /** 一键生成大纲：仅对已存在的章节生成，不创建章节；无章节时引导去「章节」页新建 */
  const generateAll = () => {
    if (running) {
      return;
    }
    if (items.length === 0) {
      message.warning("还没有章节，请先到「章节」页新建章节，再来规划大纲");
      return;
    }
    if (missing === 0) {
      message.info("所有章节都已有大纲，无需重复生成");
      return;
    }
    void startTask({});
  };

  /** 单章生成/重生成：走同步接口（一章一次调用，不会超时） */
  const generateOne = async (chapterId: number) => {
    try {
      setItems(await outlineApi.generate(novelId, [chapterId]));
      message.success("大纲已生成");
      // 单章生成大纲同样会产出有意义的章节名，通知父组件刷新
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    }
  };

  // 组件卸载时清理轮询
  useEffect(() => stopPolling, []);

  const openEdit = (item: ChapterOutlineItem) => {
    setEditing(item);
    setEditText(item.outline ?? "");
    setEditOpen(true);
  };

  const save = async () => {
    if (!editing) {
      return;
    }
    setSaving(true);
    try {
      await outlineApi.save(novelId, editing.chapterId, editText.trim());
      message.success("已保存");
      setEditOpen(false);
      setEditing(null);
      load();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSaving(false);
    }
  };

  /** 开始编辑本章目标字数：默认填当前生效值，未设置过则填全书均摊值 */
  const startEditWords = (item: ChapterOutlineItem) => {
    setEditingWordsId(item.chapterId);
    setEditingWords(item.wordCount ?? 3000);
  };

  /** 保存本章目标字数（独立于大纲，可只改字数不动大纲） */
  const commitWords = async (chapterId: number) => {
    if (!editingWords || editingWords < 100) {
      message.warning("请输入不小于 100 的字数");
      return;
    }
    setSavingWords(true);
    try {
      await outlineApi.saveWords(novelId, chapterId, editingWords);
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
  const resetWords = async (chapterId: number) => {
    try {
      await outlineApi.saveWords(novelId, chapterId, 0);
      message.success("已恢复为全书均摊值");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "恢复失败");
    }
  };

  const remove = async (chapterId: number) => {
    try {
      await outlineApi.remove(novelId, chapterId);
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
    setWritingId(item.chapterId);
    try {
      await streamSse(
        `/api/ai/novels/${novelId}/chapters/${item.chapterId}/${
          append ? "write-continue" : "write"
        }`,
        { totalChapters: items.length },
        {
          onDelta: (d) => setWriteText((t) => t + d),
        },
      );
      message.success(append ? "已接着写完并保存" : "正文已生成并保存");
      load();
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setWritingId(null);
      setWriteOpen(false);
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
          `/api/ai/novels/${novelId}/chapters/${it.chapterId}/write`,
          { totalChapters: targets.length },
          {
            onDelta: (d) => setWriteText((t) => t + d),
          },
        );
      }
      message.success("正文已全部生成并保存");
      load();
      onChanged?.();
    } catch (e: any) {
      message.error(`第${done} 章生成失败：${e?.message ?? "AI 服务暂时不可用"}`);
    } finally {
      setWriteOpen(false);
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
          <Button
            type="primary"
            icon={<RobotOutlined />}
            loading={running}
            disabled={running}
            onClick={generateAll}
          >
            一键生成大纲{missing > 0 ? `（${missing} 章待生成）` : ""}
          </Button>
          <Tooltip
            title={
              hasOutlines
                ? "按已规划的章节大纲，为每章重新生成整章正文（已有正文会被重写，旧内容可回滚）"
                : "请先生成章节大纲：正文需要依据大纲写作，避免跑偏"
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
        <Empty description="还没有章节。请先到「章节」页新建章节，再回到这里规划大纲（大纲生成不会自动创建章节）。" />
      ) : (
        <Table
          rowKey="chapterId"
          dataSource={items}
          pagination={false}
          columns={[
            { title: "#", dataIndex: "chapterOrder", width: 60 },
            {
              title: "章节名",
              dataIndex: "chapterTitle",
              width: 150,
              ellipsis: { showTitle: true },
              render: (t: string) => (
                <span style={{ whiteSpace: "nowrap" }}>{t}</span>
              ),
            },
            {
              title: (
                <Tooltip title="点击可修改本章目标字数；默认 = 全书总目标字数 ÷ 章节数">
                  预计字数
                </Tooltip>
              ),
              dataIndex: "wordCount",
              width: 130,
              render: (w: number | null, r: ChapterOutlineItem) => {
                if (editingWordsId === r.chapterId) {
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
                        onPressEnter={() => commitWords(r.chapterId)}
                      />
                      <Button
                        size="small"
                        type="primary"
                        loading={savingWords}
                        onClick={() => commitWords(r.chapterId)}
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
                    <a onClick={() => startEditWords(r)}>
                      <span className="muted">—</span>
                    </a>
                  );
                }
                return (
                  <Space size={4}>
                    <Tooltip title={r.wordCountCustomized ? "本章单独设定，点击可修改" : "沿用全书均摊，点击可单独设定"}>
                      <a onClick={() => startEditWords(r)}>
                        {Math.round(w).toLocaleString()}
                      </a>
                    </Tooltip>
                    {r.wordCountCustomized && (
                      <Tooltip title="恢复为全书均摊值">
                        <Button
                          type="link"
                          size="small"
                          icon={<RollbackOutlined />}
                          onClick={() => resetWords(r.chapterId)}
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
                  <span className="muted">未规划（可一键生成或手动添加）</span>
                ),
            },
            {
              title: "操作",
              width: 250,
              render: (_: any, r: ChapterOutlineItem) => (
                <Space>
                  <Button
                    type="link"
                    size="small"
                    icon={<RobotOutlined />}
                    onClick={() => generateOne(r.chapterId)}
                  >
                    {r.hasOutline ? "重生成" : "生成"}
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openEdit(r)}
                  >
                    {r.hasOutline ? "编辑" : "添加"}
                  </Button>
                  {r.hasOutline && (
                    <Dropdown
                      menu={{
                        items: [
                          {
                            key: "write",
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
                          {
                            key: "divider",
                            type: "divider" as const,
                          },
                          {
                            key: "delete",
                            label: "删除大纲",
                            icon: <DeleteOutlined />,
                            danger: true,
                          },
                        ],
                        onClick: ({ key }) => {
                          if (key === "delete") {
                            Modal.confirm({
                              title: "确定删除该章大纲？",
                              okText: "删除",
                              okButtonProps: { danger: true },
                              onOk: () => remove(r.chapterId),
                            });
                          } else if (key === "write") {
                            confirmWrite(r);
                          } else if (key === "append") {
                            continueWrite(r);
                          }
                        },
                      }}
                    >
                      <Button type="link" size="small">
                        更多
                      </Button>
                    </Dropdown>
                  )}
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
        footer={null}
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
        </div>
        <Input.TextArea
          readOnly
          value={writeText}
          autoSize={{ minRows: 14, maxRows: 24 }}
          style={{ fontFamily: "inherit" }}
          placeholder="（实时显示 AI 生成的正文）"
        />
      </Modal>
      <Modal
        title={
          editing
            ? `编辑大纲 · 第${editing.chapterOrder}章《${editing.chapterTitle}》`
            : "添加大纲"
        }
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={save}
        okText="保存"
        confirmLoading={saving}
        width={720}
      >
        <Input.TextArea
          rows={14}
          maxLength={2000}
          showCount
          value={editText}
          onChange={(e) => setEditText(e.target.value)}
          placeholder="规划本章：场景节拍、关键进展、人物弧光、收尾钩子（写前规划，指导后续创作）"
        />
      </Modal>
    </div>
  );
}
