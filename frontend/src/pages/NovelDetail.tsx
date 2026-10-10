import { useCallback, useEffect, useState } from "react";
import {
  Button,
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
import NovelFormModal from "../components/NovelFormModal";

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
              <NovelChaptersTab novelId={novelId} refreshKey={chapterRefresh} />
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
            key: "outlines",
            label: "章节大纲",
            children: <NovelOutlinesTab novelId={novelId} />,
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
