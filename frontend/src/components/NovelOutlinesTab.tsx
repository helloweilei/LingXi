import { useCallback, useEffect, useState } from "react";
import { Button, Dropdown, Empty, Input, Modal, Space, Spin, Table, Typography, message } from "antd";
import { DeleteOutlined, EditOutlined, FileAddOutlined, RobotOutlined } from "@ant-design/icons";
import { outlineApi } from "../api";
import { streamSse } from "../api/ai";
import type { ChapterOutlineItem } from "../api/types";

const { Paragraph } = Typography;

export default function OutlineTab({ novelId }: { novelId: number }) {
  const [items, setItems] = useState<ChapterOutlineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<ChapterOutlineItem | null>(null);
  const [editText, setEditText] = useState("");
  const [saving, setSaving] = useState(false);
  const [writeOpen, setWriteOpen] = useState(false);
  const [writeTitle, setWriteTitle] = useState("");
  const [writeText, setWriteText] = useState("");
  const [writingId, setWritingId] = useState<number | null>(null);

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

  const generateAll = async () => {
    setGenerating(true);
    try {
      setItems(await outlineApi.generate(novelId));
      message.success("大纲已生成");
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setGenerating(false);
    }
  };

  const generateOne = async (chapterId: number) => {
    try {
      setItems(await outlineApi.generate(novelId, [chapterId]));
      message.success("大纲已生成");
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    }
  };

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

  const remove = async (chapterId: number) => {
    try {
      await outlineApi.remove(novelId, chapterId);
      message.success("已删除");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "删除失败");
    }
  };

  /** 根据章节大纲生成整章正文：流式输出并落库（覆盖旧正文，ChapterService 自动留版本快照） */
  const writeOne = async (item: ChapterOutlineItem) => {
    setWriteOpen(true);
    setWriteTitle(
      `AI 生成正文 · 第${item.chapterOrder}章《${item.chapterTitle}》`,
    );
    setWriteText("");
    setWritingId(item.chapterId);
    try {
      await streamSse(
        `/api/ai/novels/${novelId}/chapters/${item.chapterId}/write`,
        null,
        {
          onDelta: (d) => setWriteText((t) => t + d),
          onError: (m) => message.error(m),
        },
      );
      message.success("正文已生成并保存");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setWritingId(null);
      setWriteOpen(false);
    }
  };

  /** 生成前覆盖确认：章节已有正文时弹窗提示，否则直接生成 */
  const confirmWrite = (item: ChapterOutlineItem) => {
    Modal.confirm({
      title: "确认",
      content:
        "如果该章已有正文，生成将覆盖旧内容（已自动留版本快照，可回滚）。确定继续？",
      okText: "生成",
      okButtonProps: { danger: false },
      onOk: () => writeOne(item),
    });
  };

  /** 一键为全部已规划大纲的章节生成正文（顺序执行） */
  const writeAll = async () => {
    const targets = items.filter((i) => i.hasOutline);
    if (targets.length === 0) {
      message.warning("请先为章节生成大纲，再据此生成正文");
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
          null,
          {
            onDelta: (d) => setWriteText((t) => t + d),
            onError: (m) => message.error(`第${it.chapterOrder}章：${m}`),
          },
        );
      }
      message.success("正文已全部生成并保存");
      load();
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setWriteOpen(false);
      setWritingId(null);
    }
  };

  /** 批量生成前覆盖确认：若任一目标章已有正文则弹窗，否则直接生成 */
  const confirmWriteAll = () => {
    const targets = items.filter((i) => i.hasOutline);
    if (targets.some((i) => (i.wordCount ?? 0) > 0)) {
      Modal.confirm({
        title: "覆盖确认",
        content:
          "将为全部已规划大纲的章节生成正文；若某章已有正文，将被覆盖（已自动留版本快照，可回滚）。确定继续？",
        okText: "生成正文",
        okButtonProps: { danger: true },
        onOk: () => writeAll(),
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
            loading={generating}
            disabled={items.length === 0}
            onClick={generateAll}
          >
            一键生成大纲{missing > 0 ? `（${missing} 章待生成）` : ""}
          </Button>
          <Button
            icon={<FileAddOutlined />}
            disabled={items.length === 0}
            onClick={() => confirmWriteAll()}
          >
            一键生成正文
          </Button>
        </Space>
      </div>
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="还没有章节，先去“章节”页新建，再来规划大纲" />
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
              title: "预计字数",
              dataIndex: "wordCount",
              width: 110,
              render: (w: number) => `${(w ?? 0).toLocaleString()}`,
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
                            label: "生成正文",
                            icon: <FileAddOutlined />,
                            danger: false,
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
