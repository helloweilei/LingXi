import { useCallback, useEffect, useRef, useState } from "react";
import type { Key } from "react";
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Upload,
  message,
} from "antd";
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  FileAddOutlined,
  PlusOutlined,
  RobotOutlined,
  SaveOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { useNavigate, useParams } from "react-router-dom";
import dayjs from "dayjs";
import {
  chapterApi,
  characterApi,
  novelApi,
  uploadApi,
  worldApi,
} from "../api";
import { streamSse } from "../api/ai";
import type {
  ChapterMeta,
  CharacterVO,
  ChatMsg,
  NovelDetail,
  WorldVO,
} from "../api/types";
import AiDrawer from "../components/AiDrawer";
import AutoWritePanel from "../components/AutoWritePanel";
import NovelFormModal, {
  GENRES,
  NOVEL_STATUS,
} from "../components/NovelFormModal";
import MDEditor from "@uiw/react-md-editor";
import "@uiw/react-md-editor/markdown-editor.css";
import "@uiw/react-markdown-preview/markdown.css";

const ROLE_COLOR: Record<string, string> = {
  主角: "magenta",
  配角: "blue",
  反派: "red",
  其他: "default",
};

export default function NovelDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const novelId = Number(id);
  const [novel, setNovel] = useState<NovelDetail | null>(null);
  const [tab, setTab] = useState("chapters");
  const [editOpen, setEditOpen] = useState(false);
  const [outlineOpen, setOutlineOpen] = useState(false);
  const [coverOpen, setCoverOpen] = useState(false);
  const [outline, setOutline] = useState("");
  const [chapterRefresh, setChapterRefresh] = useState(0);

  const load = useCallback(async () => {
    const n = await novelApi.detail(novelId);
    setNovel(n);
    setOutline(n.outline ?? "");
  }, [novelId]);

  useEffect(() => {
    load().catch((e) => message.error(e?.message ?? "加载失败"));
  }, [load]);

  if (!novel) {
    return (
      <div
        className="page-container"
        style={{ textAlign: "center", paddingTop: 100 }}
      >
        <Spin size="large" />
      </div>
    );
  }

  const saveOutline = async (text?: string) => {
    const value = text ?? outline;
    try {
      const saved = await novelApi.update(novelId, { outline: value });
      setNovel(saved);
      setOutline(saved.outline ?? "");
      message.success("大纲已保存");
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    }
  };

  const progress =
    novel.targetWords && novel.targetWords > 0
      ? Math.min(
          100,
          Math.round(((novel.wordCount ?? 0) / novel.targetWords) * 100),
        )
      : null;

  return (
    <div className="page-container">
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate("/")}
        style={{ marginBottom: 12 }}
      >
        返回作品台
      </Button>

      <div className="section-card" style={{ marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 20, alignItems: "flex-start" }}>
          <CoverBox novel={novel} onClick={() => setCoverOpen(true)} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <Space size={10} align="center" wrap>
              <h2 style={{ margin: 0 }}>{novel.title}</h2>
              {(novel.genre ?? "")
                .split(",")
                .map((g) => g.trim())
                .filter(Boolean)
                .map((g, i) => (
                  <Tag key={i} color="purple">
                    {g}
                  </Tag>
                ))}
              <Tag>{novel.status ?? "草稿"}</Tag>
              {novel.languageStyle && (
                <Tag color="cyan">{novel.languageStyle}</Tag>
              )}
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={() => setEditOpen(true)}
              >
                编辑信息
              </Button>
            </Space>
            <p className="muted" style={{ margin: "10px 0 0", maxWidth: 760 }}>
              {novel.description ||
                "（还没有简介，点击“编辑信息”补一句吧，AI 会用它理解你的书）"}
            </p>
            {progress !== null && (
              <div style={{ maxWidth: 420, marginTop: 12 }}>
                <Progress
                  percent={progress}
                  size="small"
                  strokeColor="#6a5acd"
                />
                <span className="muted" style={{ fontSize: 12 }}>
                  {(novel.wordCount ?? 0).toLocaleString()} /{" "}
                  {novel.targetWords?.toLocaleString()} 字
                </span>
              </div>
            )}
          </div>
          <div
            style={{
              textAlign: "right",
              color: "#9a9ab0",
              fontSize: 13,
              lineHeight: 2,
            }}
          >
            <div>
              共 {novel.chapterCount} 章 ·{" "}
              {(novel.wordCount ?? 0).toLocaleString()} 字
            </div>
            <div>创建于 {dayjs(novel.createdAt).format("YYYY-MM-DD")}</div>
          </div>
        </div>
      </div>

      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: "chapters",
            label: "章节",
            children: (
              <ChaptersTab novelId={novelId} refreshKey={chapterRefresh} />
            ),
          },
          {
            key: "characters",
            label: "角色设定",
            children: <CharactersTab novelId={novelId} />,
          },
          {
            key: "world",
            label: "世界观",
            children: <WorldTab novelId={novelId} />,
          },
          {
            key: "outline",
            label: "全书大纲",
            children: (
              <div className="section-card">
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    marginBottom: 12,
                  }}
                >
                  <Space>
                    <b>全书大纲</b>
                    <span className="muted">
                      AI 生成时会结合书名、简介与角色设定
                    </span>
                  </Space>
                  <Space>
                    <Button
                      icon={<RobotOutlined />}
                      type="primary"
                      ghost
                      onClick={() => setOutlineOpen(true)}
                    >
                      AI 生成大纲
                    </Button>
                    <Button
                      icon={<SaveOutlined />}
                      onClick={() => saveOutline()}
                    >
                      保存大纲
                    </Button>
                  </Space>
                </div>
                <div data-color-mode="light">
                  <MDEditor
                    value={outline}
                    onChange={(v) => setOutline(v ?? "")}
                    height="auto"
                    preview="edit"
                    visibleDragbar={false}
                    textareaProps={{
                      placeholder:
                        "在这里写下或让 AI 生成全书大纲：分卷、核心冲突、关键章节梗概……",
                    }}
                  />
                </div>
              </div>
            ),
          },
          {
            key: "chat",
            label: "AI 助手",
            children: <ChatTab novelId={novelId} />,
          },
          {
            key: "auto",
            label: "自动创作",
            children: (
              <AutoWritePanel
                novelId={novelId}
                targetWords={novel.targetWords ?? 0}
                onProgress={() => {
                  load();
                  setChapterRefresh((k) => k + 1);
                }}
              />
            ),
          },
        ]}
      />

      <NovelFormModal
        open={editOpen}
        editing={novel}
        onClose={() => setEditOpen(false)}
        onSaved={(n) => {
          setNovel((prev) =>
            prev
              ? {
                  ...prev,
                  ...n,
                  outline:
                    "outline" in n ? String((n as any).outline) : prev.outline,
                }
              : (n as NovelDetail),
          );
          message.success("已保存");
        }}
      />
      <CoverModal
        open={coverOpen}
        novel={novel}
        onClose={() => setCoverOpen(false)}
        onSaved={(n) => {
          setNovel((prev) => (prev ? { ...prev, ...n } : n));
          setCoverOpen(false);
          message.success("封面已更新");
        }}
      />
      <AiDrawer
        open={outlineOpen}
        onClose={() => setOutlineOpen(false)}
        title="AI 生成全书大纲"
        applyText="应用到大纲"
        onApply={(t) => {
          setOutline(t);
          saveOutline(t);
        }}
        run={(h) => streamSse(`/api/ai/novels/${novelId}/outline`, null, h)}
      />
    </div>
  );
}

/* ---------------- 章节 ---------------- */

/** 详情页左侧封面：有图显示图片，无图用主题色 + 书名首字占位；点击进入设置 */
function CoverBox({
  novel,
  onClick,
}: {
  novel: NovelDetail;
  onClick: () => void;
}) {
  const bg = novel.coverColor || "#6a5acd";
  return (
    <div
      onClick={onClick}
      style={{
        width: 132,
        height: 176,
        borderRadius: 10,
        overflow: "hidden",
        cursor: "pointer",
        position: "relative",
        flex: "0 0 auto",
        background: bg,
        boxShadow: "0 4px 12px rgba(0,0,0,.18)",
      }}
    >
      {novel.coverUrl ? (
        <img
          src={novel.coverUrl}
          alt="封面"
          style={{
            width: "100%",
            height: "100%",
            objectFit: "cover",
            display: "block",
          }}
        />
      ) : (
        <div
          style={{
            height: "100%",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: 46,
            fontWeight: 700,
          }}
        >
          {novel.title.slice(0, 1)}
        </div>
      )}
      <div
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          bottom: 0,
          background: "rgba(0,0,0,.5)",
          color: "#fff",
          textAlign: "center",
          fontSize: 12,
          padding: "5px 0",
        }}
      >
        设置封面
      </div>
    </div>
  );
}

/** 设置封面弹窗：上传图片 / 移除封面 */
function CoverModal({
  open,
  novel,
  onClose,
  onSaved,
}: {
  open: boolean;
  novel: NovelDetail;
  onClose: () => void;
  onSaved: (n: NovelDetail) => void;
}) {
  const [coverUrl, setCoverUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setCoverUrl(novel.coverUrl ?? null);
    }
  }, [open, novel.coverUrl]);

  const save = async () => {
    setSaving(true);
    try {
      const saved = await novelApi.update(novel.id, {
        coverUrl: coverUrl ?? "",
      });
      onSaved(saved);
      onClose();
    } catch (e: any) {
      message.error(e?.message ?? "保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="设置封面"
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="保存"
      confirmLoading={saving}
    >
      <div style={{ display: "flex", gap: 20, alignItems: "center" }}>
        <div
          style={{
            width: 120,
            height: 160,
            borderRadius: 8,
            overflow: "hidden",
            background: novel.coverColor || "#6a5acd",
            flex: "0 0 auto",
          }}
        >
          {coverUrl ? (
            <img
              src={coverUrl}
              alt="预览"
              style={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
          ) : (
            <div
              style={{
                height: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "#fff",
                fontSize: 40,
                fontWeight: 700,
              }}
            >
              {novel.title.slice(0, 1)}
            </div>
          )}
        </div>
        <div>
          <Upload
            accept="image/*"
            showUploadList={false}
            customRequest={async ({ file, onSuccess, onError }) => {
              setUploading(true);
              try {
                const res = await uploadApi.upload(file as File);
                setCoverUrl(res.url);
                onSuccess?.(res);
              } catch (e: any) {
                onError?.(e as any);
                message.error(e?.message ?? "上传失败");
              } finally {
                setUploading(false);
              }
            }}
          >
            <Button icon={<UploadOutlined />} loading={uploading}>
              上传图片
            </Button>
          </Upload>
          <p className="muted" style={{ marginTop: 8, fontSize: 12 }}>
            支持 jpg/png/webp/gif，≤ 5MB
          </p>
          {coverUrl && (
            <Button
              type="link"
              danger
              style={{ paddingLeft: 0 }}
              onClick={() => setCoverUrl(null)}
            >
              移除封面
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}

function ChaptersTab({
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
          { title: "#", dataIndex: "chapterOrder", width: 60 },
          {
            title: "章节名",
            dataIndex: "title",
            render: (t: string, r: ChapterMeta) => (
              <a onClick={() => navigate(`/read/${r.id}`)}>{t}</a>
            ),
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
          placeholder="章节名（可留空，默认“未命名章节”）"
          onPressEnter={createChapter}
        />
      </Modal>
    </div>
  );
}

/* ---------------- 角色设定 ---------------- */

function CharactersTab({ novelId }: { novelId: number }) {
  const [items, setItems] = useState<CharacterVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<CharacterVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await characterApi.list(novelId));
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load]);

  const openModal = (c: CharacterVO | null) => {
    setEditing(c);
    form.setFieldsValue(c ?? { name: "", role: "配角", description: "" });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing) {
      await characterApi.update(editing.id, values);
    } else {
      await characterApi.create(novelId, values);
    }
    message.success("已保存");
    setModalOpen(false);
    load();
  };

  const remove = async (cid: number) => {
    await characterApi.remove(cid);
    message.success("已删除");
    load();
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
        <b>角色设定</b>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => openModal(null)}
        >
          新增角色
        </Button>
      </div>
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="还没有角色设定。填好角色后，AI 写作会更“懂”他们" />
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((c) => (
            <Col key={c.id} xs={24} sm={12} lg={8}>
              <Card
                size="small"
                title={
                  <span>
                    {c.name}{" "}
                    <Tag color={ROLE_COLOR[c.role] ?? "default"}>{c.role}</Tag>
                  </span>
                }
                actions={[
                  <span key="edit" onClick={() => openModal(c)}>
                    <EditOutlined /> 编辑
                  </span>,
                  <Popconfirm
                    key="del"
                    title="确定删除该角色？"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(c.id)}
                  >
                    <span style={{ color: "#9a9ab0" }}>
                      <DeleteOutlined /> 删除
                    </span>
                  </Popconfirm>,
                ]}
              >
                <p style={{ minHeight: 44, whiteSpace: "pre-wrap", margin: 0 }}>
                  {c.description || <span className="muted">（暂无描述）</span>}
                </p>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Modal
        title={editing ? "编辑角色" : "新增角色"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        okText="保存"
      >
        <Form form={form} layout="vertical">
          <div style={{ display: "flex", gap: 12 }}>
            <Form.Item
              name="name"
              label="姓名"
              rules={[{ required: true, message: "请输入角色名" }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={60} />
            </Form.Item>
            <Form.Item name="role" label="定位" style={{ flex: 1 }}>
              <Select
                options={["主角", "配角", "反派", "其他"].map((v) => ({
                  value: v,
                  label: v,
                }))}
              />
            </Form.Item>
          </div>
          <Form.Item
            name="description"
            label="人物设定（性格 / 背景 / 能力 / 目标）"
          >
            <Input.TextArea rows={5} maxLength={5000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/* ---------------- 世界观 ---------------- */

function WorldTab({ novelId }: { novelId: number }) {
  const [items, setItems] = useState<WorldVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<WorldVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await worldApi.list(novelId));
    } finally {
      setLoading(false);
    }
  }, [novelId]);

  useEffect(() => {
    load();
  }, [load]);

  const openModal = (w: WorldVO | null) => {
    setEditing(w);
    form.setFieldsValue(w ?? { title: "", content: "" });
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (editing) {
      await worldApi.update(editing.id, values);
    } else {
      await worldApi.create(novelId, values);
    }
    message.success("已保存");
    setModalOpen(false);
    load();
  };

  const remove = async (wid: number) => {
    await worldApi.remove(wid);
    message.success("已删除");
    load();
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
        <b>世界观设定</b>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => openModal(null)}
        >
          新增条目
        </Button>
      </div>
      {loading ? (
        <Spin />
      ) : items.length === 0 ? (
        <Empty description="记录力量体系、地理、势力等设定，让 AI 一致地使用它们" />
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((w) => (
            <Col key={w.id} xs={24} sm={12} lg={8}>
              <Card
                size="small"
                title={w.title}
                actions={[
                  <span key="edit" onClick={() => openModal(w)}>
                    <EditOutlined /> 编辑
                  </span>,
                  <Popconfirm
                    key="del"
                    title="确定删除该条目？"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(w.id)}
                  >
                    <span style={{ color: "#9a9ab0" }}>
                      <DeleteOutlined /> 删除
                    </span>
                  </Popconfirm>,
                ]}
              >
                <p style={{ minHeight: 44, whiteSpace: "pre-wrap", margin: 0 }}>
                  {w.content || <span className="muted">（暂无内容）</span>}
                </p>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      <Modal
        title={editing ? "编辑条目" : "新增条目"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        okText="保存"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="条目标题"
            rules={[{ required: true, message: "请输入标题" }]}
          >
            <Input
              maxLength={80}
              placeholder="如：力量体系 / 地理格局 / 势力划分"
            />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={6} maxLength={10000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

/* ---------------- AI 助手对话 ---------------- */

function ChatTab({ novelId }: { novelId: number }) {
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [errorAt, setErrorAt] = useState(-1);
  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const msgRef = useRef<ChatMsg[]>([]);
  msgRef.current = messages;

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages]);

  const send = async () => {
    const q = input.trim();
    if (!q || sending) {
      return;
    }
    const history = msgRef.current
      .filter((m) => m.content)
      .slice(-10)
      .map((m) => ({ role: m.role, content: m.content }));
    setMessages((prev) => [
      ...prev,
      { role: "user", content: q },
      { role: "assistant", content: "" },
    ]);
    setInput("");
    setSending(true);
    setErrorAt(-1);

    const controller = new AbortController();
    abortRef.current = controller;
    const patchLast = (patch: Partial<ChatMsg>) =>
      setMessages((prev) => {
        const copy = [...prev];
        copy[copy.length - 1] = { ...copy[copy.length - 1], ...patch };
        return copy;
      });

    try {
      await streamSse(
        `/api/ai/novels/${novelId}/chat`,
        { message: q, history },
        {
          onDelta: (d) =>
            patchLast({
              content: msgRef.current[msgRef.current.length - 1].content + d,
            }),
          onDone: () => undefined,
          onError: (m) => {
            setErrorAt(msgRef.current.length - 1);
            patchLast({ content: m });
          },
        },
        controller.signal,
      );
    } catch (e: any) {
      if (!controller.signal.aborted) {
        setErrorAt(msgRef.current.length - 1);
        patchLast({ content: e?.message ?? "请求失败" });
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="section-card">
      <div style={{ marginBottom: 10 }}>
        <b>AI 助手</b>
        <span className="muted" style={{ marginLeft: 10 }}>
          结合本书的书名、简介、大纲与设定回答创作问题
        </span>
      </div>
      <div
        className="chat-list"
        ref={listRef}
        style={{ maxHeight: "46vh", overflow: "auto", minHeight: 200 }}
      >
        {messages.length === 0 && (
          <Empty
            description={
              <span className="muted">
                试试问：“主角的动机怎么设计？”、“第二章怎么开头？”、“帮我想三个反转”
              </span>
            }
            style={{ paddingTop: 30 }}
          />
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={`chat-bubble ${m.role}${i === errorAt ? " error" : ""}`}
          >
            {m.content || (sending && i === messages.length - 1 ? "……" : "")}
          </div>
        ))}
      </div>
      <div style={{ display: "flex", gap: 10 }}>
        <Input.TextArea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={sending}
        />
        {sending ? (
          <Button
            danger
            onClick={() => {
              abortRef.current?.abort();
              setSending(false);
            }}
          >
            停止
          </Button>
        ) : (
          <Button type="primary" onClick={send} disabled={!input.trim()}>
            发送
          </Button>
        )}
      </div>
    </div>
  );
}
