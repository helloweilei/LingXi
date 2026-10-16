import { useCallback, useEffect, useState } from "react";
import {
  Button,
  Input,
  Modal,
  Progress,
  Space,
  Spin,
  Tabs,
  Tag,
  Upload,
  message,
} from "antd";
import {
  ArrowLeftOutlined,
  EditOutlined,
  RobotOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { useNavigate, useParams } from "react-router-dom";
import dayjs from "dayjs";
import { novelApi, uploadApi } from "../api";
import { streamSse } from "../api/ai";
import type { NovelDetail } from "../api/types";
import AiDrawer from "../components/AiDrawer";
import AutoWritePanel from "../components/AutoWritePanel";
import NovelBookOutlineTab from "../components/NovelBookOutlineTab";
import NovelChaptersTab from "../components/NovelChaptersTab";
import NovelCharactersTab from "../components/NovelCharactersTab";
import NovelChatTab from "../components/NovelChatTab";
import NovelOutlinesTab from "../components/NovelOutlinesTab";
import NovelWorldTab from "../components/NovelWorldTab";
import NovelFormModal, { styleToArray } from "../components/NovelFormModal";

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
              {styleToArray(novel.languageStyle).map((s, i) => (
                <Tag
                  key={i}
                  color="cyan"
                  title={s}
                  style={{ maxWidth: 240, overflow: "hidden", textOverflow: "ellipsis", verticalAlign: "middle" }}
                >
                  {s}
                </Tag>
              ))}
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
            label: "章节内容",
            children: (
              <NovelChaptersTab
                novelId={novelId}
                refreshKey={chapterRefresh}
                totalWords={novel.wordCount ?? 0}
              />
            ),
          },
          {
            key: "outlines",
            label: "章节大纲",
            children: (
              <NovelOutlinesTab
                novelId={novelId}
                targetWords={novel.targetWords ?? null}
                onChanged={() => {
                  // 正文生成后同步刷新作品字数统计与章节列表
                  load();
                  setChapterRefresh((k) => k + 1);
                }}
              />
            ),
          },
          {
            key: "characters",
            label: "角色设定",
            children: <NovelCharactersTab novelId={novelId} />,
          },
          {
            key: "world",
            label: "世界观",
            children: <NovelWorldTab novelId={novelId} />,
          },
          {
            key: "outline",
            label: "全书大纲",
            children: (
              <NovelBookOutlineTab
                outline={outline}
                onChange={setOutline}
                onSave={() => saveOutline()}
                onGenerate={() => setOutlineOpen(true)}
              />
            ),
          },
          {
            key: "chat",
            label: "AI 助手",
            children: <NovelChatTab novelId={novelId} />,
          },
          {
            key: "auto",
            label: "自动创作",
            children: (
              <AutoWritePanel
                novelId={novelId}
                targetWords={novel.targetWords ?? 0}
                languageStyle={novel.languageStyle ?? null}
                onStyleChange={() => load()}
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
  const [promptText, setPromptText] = useState("");
  const [authorName, setAuthorName] = useState("");
  const [generatingPrompt, setGeneratingPrompt] = useState(false);
  const [promptDirty, setPromptDirty] = useState(false);

  useEffect(() => {
    if (open) {
      setCoverUrl(novel.coverUrl ?? null);
      setPromptText(novel.coverPrompt ?? "");
      setAuthorName(novel.authorName ?? "");
      setPromptDirty(false);
    }
  }, [open, novel.coverUrl, novel.coverPrompt, novel.authorName]);

  /** 流式生成封面绘图提示词（AI 依据书名/题材/简介/人物/大纲生成；作者名原样写入，不允许改写） */
  const genCoverPrompt = async () => {
    setGeneratingPrompt(true);
    setPromptText("");
    try {
      await streamSse(
        `/api/ai/novels/${novel.id}/cover-prompt`,
        { authorName: authorName.trim() || null },
        {
          onDelta: (d) => setPromptText((t) => t + d),
        },
      );
      setPromptDirty(true);
      message.success("封面提示词已生成，可直接编辑后保存");
    } catch (e: any) {
      message.error(e?.message ?? "生成失败");
    } finally {
      setGeneratingPrompt(false);
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      const saved = await novelApi.update(novel.id, {
        coverUrl: coverUrl ?? "",
        coverPrompt: promptText,
        authorName: authorName.trim(),
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
      width={720}
      destroyOnHidden
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

      <div style={{ marginTop: 20 }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 12,
            marginBottom: 8,
          }}
        >
          <span style={{ whiteSpace: "nowrap" }}>
            <b>封面提示词</b>
            <span className="muted" style={{ marginLeft: 8, fontSize: 12 }}>
              默认让画面直接排出书名，生成的提示词可投喂给 AI 绘图模型
            </span>
          </span>
          <Button
            type="primary"
            ghost
            icon={<RobotOutlined />}
            loading={generatingPrompt}
            onClick={genCoverPrompt}
          >
            {promptText ? "重新生成" : "AI 生成封面提示词"}
          </Button>
        </div>
        <div
          style={{
            marginBottom: 10,
            display: "flex",
            alignItems: "center",
            gap: 8,
          }}
        >
          <span>作者名（选填）：</span>
          <Input
            value={authorName}
            style={{ width: 220 }}
            onChange={(e) => setAuthorName(e.target.value)}
            maxLength={30}
            allowClear
            placeholder="<笔名>会原样排版到封面上"
            disabled={generatingPrompt}
          />
        </div>
        <Input.TextArea
          value={promptText}
          onChange={(e) => {
            setPromptText(e.target.value);
            setPromptDirty(true);
          }}
          rows={10}
          maxLength={2000}
          showCount
          placeholder="点击右上角按钮自动生成；也可以直接手写。提示词会让画面直接排出书名，出图后即可作为封面使用。"
          disabled={generatingPrompt}
          style={{ marginBottom: 16 }}
        />
        {promptDirty && !generatingPrompt && (
          <p
            className="muted"
            style={{ marginTop: 6, marginBottom: 0, fontSize: 12 }}
          >
            提示词已修改，点「保存」后生效。
          </p>
        )}
      </div>
    </Modal>
  );
}
