import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import {
  CopyOutlined,
  DeleteOutlined,
  HistoryOutlined,
  RollbackOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import { chapterApi } from "../api";
import type { Chapter, ChapterVersion } from "../api/types";

const { Paragraph, Text } = Typography;

/** 快照来源 → 中文说明 */
const REASON_META: Record<string, { color: string; text: string }> = {
  save: { color: "default", text: "手动保存" },
  ai: { color: "blue", text: "AI 写入" },
  "ai-title": { color: "cyan", text: "AI 命名" },
  restore: { color: "purple", text: "回滚留档" },
};

/**
 * 章节版本历史：查看历次快照（手动保存 / AI 写入 / 回滚留档），支持预览正文与一键回滚。
 * 回滚本身也可回退——服务端会在回滚前先为当前内容留一份档。
 */
export default function VersionHistoryModal({
  open,
  chapter,
  onClose,
  onRestored,
}: {
  open: boolean;
  chapter: Chapter | null;
  onClose: () => void;
  /** 回滚成功后把章节新内容回传给编辑器 */
  onRestored: (c: Chapter) => void;
}) {
  const [versions, setVersions] = useState<ChapterVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<ChapterVersion | null>(null);
  const [restoringId, setRestoringId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!chapter) return;
    setLoading(true);
    try {
      const list = await chapterApi.versions(chapter.id);
      // 按版本号倒序：最新在最前
      setVersions([...list].sort((a, b) => b.versionNo - a.versionNo));
    } catch (e: any) {
      message.error(e?.message ?? "加载版本历史失败");
    } finally {
      setLoading(false);
    }
  }, [chapter]);

  useEffect(() => {
    if (open) {
      setPreview(null);
      void load();
    }
  }, [open, load]);

  /** 回滚到指定快照 */
  const restore = async (v: ChapterVersion) => {
    if (!chapter) return;
    setRestoringId(v.id);
    try {
      const saved = await chapterApi.restoreVersion(chapter.id, v.id);
      message.success(`已回滚到 v${v.versionNo}`);
      onRestored(saved);
      onClose();
    } catch (e: any) {
      message.error(e?.message ?? "回滚失败");
    } finally {
      setRestoringId(null);
    }
  };

  /** 删除指定快照（不影响章节当前内容） */
  const removeVersion = async (v: ChapterVersion) => {
    if (!chapter) return;
    setDeletingId(v.id);
    try {
      await chapterApi.deleteVersion(chapter.id, v.id);
      setVersions((prev) => prev.filter((x) => x.id !== v.id));
      if (preview?.id === v.id) {
        setPreview(null);
      }
      message.success(`已删除 v${v.versionNo}`);
    } catch (e: any) {
      message.error(e?.message ?? "删除失败");
    } finally {
      setDeletingId(null);
    }
  };

  /** 一键复制文本；clipboard 在非安全上下文不可用时降级为 execCommand */
  const copyText = async (text: string) => {
    if (!text.trim()) {
      message.warning("没有可复制的内容");
      return;
    }
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.style.position = "fixed";
        ta.style.opacity = "0";
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      message.success("已复制到剪贴板");
    } catch {
      message.error("复制失败，请手动选中文本复制");
    }
  };

  return (
    <>
      <Modal
        title={
          <Space>
            <HistoryOutlined />
            版本历史
            {chapter && (
              <Text type="secondary" style={{ fontWeight: 400, fontSize: 13 }}>
                {chapter.title}
              </Text>
            )}
          </Space>
        }
        open={open}
        onCancel={onClose}
        width={680}
        footer={<Button onClick={onClose}>关闭</Button>}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="每次正文或章节名变化都会自动留档（AI 覆盖生成也会）。回滚前系统会再为当前内容留一份档，因此回滚也能撤销。"
        />
        {loading ? (
          <Spin />
        ) : versions.length === 0 ? (
          <Empty
            description="暂无版本快照（内容发生变化后会自动记录）"
            style={{ padding: "30px 0" }}
          />
        ) : (
          <List
            dataSource={versions}
            pagination={false}
            style={{ maxHeight: 460, overflowY: "auto" }}
            renderItem={(v) => {
              const meta = REASON_META[v.reason ?? ""] ?? {
                color: "default",
                text: v.reason ?? "未知",
              };
              return (
                <List.Item
                  actions={[
                    <Button
                      key="preview"
                      size="small"
                      onClick={() => setPreview(v)}
                    >
                      预览
                    </Button>,
                    <Popconfirm
                      key="restore"
                      title={`回滚到 v${v.versionNo}？`}
                      description="当前内容会先自动留档，回滚后仍可再切回。"
                      okText="回滚"
                      cancelText="取消"
                      onConfirm={() => restore(v)}
                    >
                      <Button
                        size="small"
                        type="primary"
                        ghost
                        icon={<RollbackOutlined />}
                        loading={restoringId === v.id}
                      >
                        回滚
                      </Button>
                    </Popconfirm>,
                    <Popconfirm
                      key="delete"
                      title={`删除 v${v.versionNo} 这份快照？`}
                      description="仅删除该历史档，不影响章节当前内容，且不可恢复。"
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => removeVersion(v)}
                    >
                      <Button
                        size="small"
                        danger
                        type="text"
                        icon={<DeleteOutlined />}
                        loading={deletingId === v.id}
                      />
                    </Popconfirm>,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <Space size={8} wrap>
                        <b>v{v.versionNo}</b>
                        <Tag color={meta.color}>{meta.text}</Tag>
                        <span className="muted" style={{ fontSize: 12, fontWeight: 400 }}>
                          {(v.wordCount ?? 0).toLocaleString()} 字
                        </span>
                      </Space>
                    }
                    description={
                      <span className="muted" style={{ fontSize: 12 }}>
                        {v.title} · {dayjs(v.createdAt).format("YYYY-MM-DD HH:mm:ss")}
                      </span>
                    }
                  />
                </List.Item>
              );
            }}
          />
        )}
      </Modal>

      {/* 快照正文预览 */}
      <Modal
        title={preview ? `预览 v${preview.versionNo} · ${preview.title}` : "预览"}
        open={!!preview}
        onCancel={() => setPreview(null)}
        width={760}
        footer={
          <Space>
            {preview && (
              <Button icon={<CopyOutlined />} onClick={() => copyText(preview.content ?? "")}>
                复制正文
              </Button>
            )}
            <Button onClick={() => setPreview(null)}>关闭</Button>
            {preview && (
              <Popconfirm
                title={`回滚到 v${preview.versionNo}？`}
                description="当前内容会先自动留档，回滚后仍可再切回。"
                okText="回滚"
                cancelText="取消"
                onConfirm={() => {
                  const v = preview;
                  setPreview(null);
                  void restore(v);
                }}
              >
                <Button type="primary" icon={<RollbackOutlined />}>
                  回滚到此版本
                </Button>
              </Popconfirm>
            )}
            {preview && (
              <Popconfirm
                title={`删除 v${preview.versionNo} 这份快照？`}
                description="仅删除该历史档，不影响章节当前内容，且不可恢复。"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => {
                  const v = preview;
                  setPreview(null);
                  void removeVersion(v);
                }}
              >
                <Button danger icon={<DeleteOutlined />}>
                  删除此快照
                </Button>
              </Popconfirm>
            )}
          </Space>
        }
        destroyOnClose
      >
        {preview && (
          <>
            <Space size={8} style={{ marginBottom: 10 }} wrap>
              <Tag color={(REASON_META[preview.reason ?? ""] ?? { color: "default" }).color}>
                {(REASON_META[preview.reason ?? ""] ?? { text: preview.reason ?? "未知" }).text}
              </Tag>
              <span className="muted" style={{ fontSize: 12 }}>
                {dayjs(preview.createdAt).format("YYYY-MM-DD HH:mm:ss")} ·{" "}
                {(preview.wordCount ?? 0).toLocaleString()} 字
              </span>
            </Space>
            <Input.TextArea
              readOnly
              value={preview.content ?? ""}
              autoSize={{ minRows: 10, maxRows: 22 }}
              style={{ fontFamily: "inherit" }}
            />
          </>
        )}
      </Modal>
    </>
  );
}