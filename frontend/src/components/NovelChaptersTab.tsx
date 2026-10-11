import { useCallback, useEffect, useState } from "react";
import type { Key } from "react";
import { Button, Empty, Input, Modal, Popconfirm, Space, Table, Tag, Tooltip, message } from "antd";
import { DeleteOutlined, EditOutlined, FileAddOutlined, RobotOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { chapterApi } from "../api";
import { streamSse } from "../api/ai";
import type { ChapterMeta } from "../api/types";

/** 是否为无信息量的占位章节名（这些才提示/允许 AI 命名） */
function isPlaceholderTitle(t?: string | null): boolean {
  const s = (t ?? "").trim();
  if (!s || s === "未命名章节" || s === "未命名" || s === "待定") return true;
  return /^第?\s*\d+\s*[章回节卷]?\s*[:：、.\s]*$/.test(s);
}

export default function ChaptersTab({
  novelId,
  refreshKey,
}: {
  novelId: number;
  refreshKey: number;
}) {
  const navigate = useNavigate();
  const [chapters, setChapters] = useState<ChapterMeta[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);
  // 章节名编辑（手动改名 / AI 命名后确认）
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingTitle, setEditingTitle] = useState("");
  const [savingTitle, setSavingTitle] = useState(false);
  const [namingId, setNamingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setChapters(await chapterApi.list(novelId));
    } catch (e: any) {
      message.error(e?.message ?? "加载失败");
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  /** 保存手动编辑 / AI 生成的章节名 */
  const saveTitle = async (id: number) => {
    const t = editingTitle.trim();
    if (!t) {
      message.warning("章节名不能为空");
      return;
    }
    setSavingTitle(true);
    try {
      await chapterApi.save(id, { title: t });
      message.success("章节名已更新");
      setEditingId(null);
      load();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSavingTitle(false);
    }
  };

  /** 依据本章大纲/正文流式生成有信息量的章节名，回填到编辑框由用户确认 */
  const aiTitle = async (c: ChapterMeta) => {
    setNamingId(c.id);
    let acc = "";
    try {
      await streamSse(`/api/ai/novels/${novelId}/chapters/${c.id}/title`, null, {
        onDelta: (d) => {
          acc += d;
        },
      });
      const title = acc
        .replace(/章节名\s*[:：]\s*/g, "")
        .replace(/[《》「」『』"'\n\r]/g, "")
        .trim();
      if (!title) {
        message.error("未生成有效标题，请手动填写");
        return;
      }
      setEditingId(c.id);
      setEditingTitle(title.slice(0, 40));
      message.success("已生成章节名，可修改后保存");
    } catch (e: any) {
      message.error(e?.message ?? "AI 命名失败");
    } finally {
      setNamingId(null);
    }
  };

  const createChapter = async () => {
    setCreating(true);
    try {
      const ch = await chapterApi.create(novelId, {
        title: newTitle || undefined,
      });
      message.success("章节已创建");
      setCreateOpen(false);
      setNewTitle("");
      navigate(`/write/${ch.id}`);
    } catch (e: any) {
      message.error(e?.message ?? "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const removeChapter = async (cid: number) => {
    await chapterApi.remove(cid);
    message.success("章节已删除");
    load();
  };

  const removeBatch = async () => {
    if (selectedKeys.length === 0) {
      return;
    }
    setBatchDeleting(true);
    try {
      await chapterApi.removeBatch(selectedKeys.map(Number));
      message.success(`已删除 ${selectedKeys.length} 章`);
      setSelectedKeys([]);
      load();
    } catch (e: any) {
      message.error(e?.message ?? "批量删除失败");
    } finally {
      setBatchDeleting(false);
    }
  };

  return (
    <div className="section-card">
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          marginBottom: 14,
        }}
      >
        <b>章节列表</b>
        <Space>
          {selectedKeys.length > 0 && (
            <Popconfirm
              title={`删除选中的 ${selectedKeys.length} 章？`}
              description="章节正文与历史版本会保留（软删除），后续可恢复。"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={removeBatch}
            >
              <Button danger icon={<DeleteOutlined />} loading={batchDeleting}>
                批量删除（{selectedKeys.length}）
              </Button>
            </Popconfirm>
          )}
          <Button
            type="primary"
            icon={<FileAddOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建章节
          </Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={chapters}
        pagination={false}
        rowSelection={{
          selectedRowKeys: selectedKeys,
          onChange: (keys) => setSelectedKeys(keys),
        }}
        locale={{
          emptyText: <Empty description="还没有章节，点击右上角新建" />,
        }}
        columns={[
          { title: "#章节编号", dataIndex: "chapterOrder", width: 120 },
          {
            title: "章节名",
            dataIndex: "title",
            render: (t: string, r: ChapterMeta) => {
              if (editingId === r.id) {
                return (
                  <Space.Compact style={{ width: "100%" }}>
                    <Input
                      size="small"
                      value={editingTitle}
                      autoFocus
                      maxLength={40}
                      onChange={(e) => setEditingTitle(e.target.value)}
                      onPressEnter={() => saveTitle(r.id)}
                      placeholder="输入章节名"
                    />
                    <Button
                      size="small"
                      type="primary"
                      loading={savingTitle}
                      onClick={() => saveTitle(r.id)}
                    >
                      保存
                    </Button>
                    <Button size="small" onClick={() => setEditingId(null)}>
                      取消
                    </Button>
                  </Space.Compact>
                );
              }
              const placeholder = isPlaceholderTitle(t);
              return (
                <Space size={6}>
                  <a onClick={() => navigate(`/read/${r.id}`)}>{t || "未命名章节"}</a>
                  {placeholder && (
                    <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                      未命名
                    </Tag>
                  )}
                  <Tooltip title="重命名">
                    <Button
                      type="link"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => {
                        setEditingId(r.id);
                        setEditingTitle(t ?? "");
                      }}
                    />
                  </Tooltip>
                  <Tooltip
                    title={
                      placeholder
                        ? "依据本章大纲与正文生成有信息量的章节名"
                        : "重新生成一个更贴切的章节名"
                    }
                  >
                    <Button
                      type="link"
                      size="small"
                      loading={namingId === r.id}
                      icon={<RobotOutlined />}
                      onClick={() => aiTitle(r)}
                    />
                  </Tooltip>
                </Space>
              );
            },
          },
          {
            title: "字数",
            dataIndex: "wordCount",
            width: 110,
            render: (w: number) => `${(w ?? 0).toLocaleString()}`,
          },
          {
            title: "更新时间",
            dataIndex: "updatedAt",
            width: 180,
            render: (t: string) => dayjs(t).format("MM-DD HH:mm"),
          },
          {
            title: "操作",
            width: 160,
            render: (_: any, r: ChapterMeta) => (
              <Space>
                <Button
                  type="link"
                  size="small"
                  onClick={() => navigate(`/write/${r.id}`)}
                >
                  编辑
                </Button>
                <Popconfirm
                  title="确定删除这一章？"
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => removeChapter(r.id)}
                >
                  <Button type="link" size="small" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title="新建章节"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={createChapter}
        okText="创建并开始写作"
        confirmLoading={creating}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="章节名（可留空：生成大纲或用 AI 命名时会自动起名）"
          onPressEnter={createChapter}
        />
        <p className="muted" style={{ margin: "8px 0 0", fontSize: 12 }}>
          建议 6-16 字的具体标题（如「雨夜惊变」）；留空也不影响，之后可在列表里手动改名或用 AI 生成。
        </p>
      </Modal>
    </div>
  );
}
