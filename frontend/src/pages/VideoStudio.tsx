import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Radio,
  Segmented,
  Select,
  Space,
  Spin,
  Tooltip,
  Typography,
  message,
} from "antd";
import { CopyOutlined, VideoCameraOutlined } from "@ant-design/icons";
import { chapterApi, novelApi } from "../api";
import { streamSse } from "../api/ai";
import { copyText } from "../utils/clipboard";
import type { ChapterMeta, Novel } from "../api/types";

/**
 * 视频提示词工作台（独立页面）。
 * 两种来源：
 * - 选择已有作品：走 /api/ai/novels/{id}/video-prompt，角色/世界观锚点来自设定，结果落库（作品详情可见）；
 * - 粘贴外部文本：走 /api/ai/video-prompt/adhoc，锚点由 AI 从正文提炼，结果不落库（自行复制保存）。
 * 两种模式：单章分镜（3-6 镜头）/ 全书宣传片（15-30 秒）。
 * 结果按「锚点 + 逐镜头」组织，复制单镜头时自动附带锚点——锚点逐字复用是避免跨镜头人物漂移的关键。
 */
export default function VideoStudio() {
  const [source, setSource] = useState<"novel" | "paste">("novel");
  const [mode, setMode] = useState<"storyboard" | "trailer">("storyboard");
  const [novels, setNovels] = useState<Novel[]>([]);
  const [novelId, setNovelId] = useState<number | null>(null);
  const [chapters, setChapters] = useState<ChapterMeta[]>([]);
  const [chapterId, setChapterId] = useState<number | null>(null);
  const [pasteTitle, setPasteTitle] = useState("");
  const [pasteContent, setPasteContent] = useState("");
  const [text, setText] = useState("");
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    novelApi
      .list()
      .then((ns) => {
        setNovels(ns);
        if (ns.length > 0) {
          setNovelId(ns[0].id);
        }
      })
      .catch((e: any) => message.error(e?.message ?? "作品列表加载失败"));
  }, []);

  // 选定作品后拉取章节列表（仅元数据），默认选第一章
  useEffect(() => {
    if (novelId == null) {
      setChapters([]);
      setChapterId(null);
      return;
    }
    chapterApi
      .list(novelId)
      .then((cs) => {
        setChapters(cs);
        setChapterId(cs.length > 0 ? cs[0].id : null);
      })
      .catch(() => {
        setChapters([]);
        setChapterId(null);
      });
  }, [novelId]);

  const canGenerate =
    source === "novel"
      ? novelId != null && (mode === "trailer" || chapterId != null)
      : pasteContent.trim().length >= 50;

  const generate = async () => {
    if (!canGenerate || generating) {
      return;
    }
    setText("");
    setGenerating(true);
    try {
      if (source === "novel") {
        await streamSse(
          `/api/ai/novels/${novelId}/video-prompt`,
          { chapterId: mode === "storyboard" ? chapterId : null },
          { onDelta: (d) => setText((p) => p + d) },
        );
        message.success("生成完成，提示词已保存，可在作品详情的「视频」入口查看");
      } else {
        await streamSse(
          "/api/ai/video-prompt/adhoc",
          {
            title: pasteTitle.trim() || null,
            content: pasteContent.trim(),
            storyboard: mode === "storyboard",
          },
          { onDelta: (d) => setText((p) => p + d) },
        );
        message.success("生成完成，外部文本不会保存到平台，请及时复制");
      }
    } catch (e: any) {
      message.error(e?.message ?? "生成失败，请稍后重试");
    } finally {
      setGenerating(false);
    }
  };

  /** 锚点区（风格锚点 + 角色锚点）：第一个镜头块之前的全部内容 */
  const anchorText = useMemo(() => {
    const idx = text.search(/【镜头/);
    return idx >= 0 ? text.slice(0, idx).trimEnd() : "";
  }, [text]);

  /** 逐镜头块：【镜头 N】开头到下一个镜头块之前 */
  const shots = useMemo(() => text.match(/【镜头[\s\S]*?(?=\n【镜头|$)/g) ?? [], [text]);

  const copyAll = async () => {
    try {
      await copyText(text);
      message.success("全部提示词已复制");
    } catch {
      message.error("复制失败，请手动选中文本复制");
    }
  };

  /** 复制单个镜头时连同锚点一起复制——粘贴到视频平台时本就需要完整上下文 */
  const copyShot = async (shot: string) => {
    try {
      await copyText(anchorText ? `${anchorText}\n\n${shot}` : shot);
      message.success("已复制该镜头（含锚点）");
    } catch {
      message.error("复制失败，请手动选中文本复制");
    }
  };

  const copyAnchor = async () => {
    try {
      await copyText(anchorText);
      message.success("锚点已复制");
    } catch {
      message.error("复制失败，请手动选中文本复制");
    }
  };

  return (
    <div className="page-container" style={{ maxWidth: 1000, margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
        <VideoCameraOutlined style={{ fontSize: 22, color: "#6a5acd" }} />
        <h2 style={{ margin: 0 }}>视频提示词工作台</h2>
        <span style={{ color: "#9a9ab0", fontSize: 13 }}>
          生成文生视频分镜提示词，复制到即梦 / 可灵 / Runway 等平台使用
        </span>
      </div>

      <div className="section-card" style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Space wrap size={16}>
            <span>
              <span className="muted" style={{ marginRight: 8, fontSize: 13 }}>来源</span>
              <Segmented
                value={source}
                onChange={(v) => setSource(v as "novel" | "paste")}
                options={[
                  { label: "选择已有作品", value: "novel" },
                  { label: "粘贴外部文本", value: "paste" },
                ]}
              />
            </span>
            <span>
              <span className="muted" style={{ marginRight: 8, fontSize: 13 }}>模式</span>
              <Radio.Group
                value={mode}
                onChange={(e) => setMode(e.target.value)}
                optionType="button"
                buttonStyle="solid"
                options={[
                  { label: "单章分镜", value: "storyboard" },
                  { label: "全书宣传片", value: "trailer" },
                ]}
              />
            </span>
          </Space>

          {source === "novel" ? (
            <Space wrap>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择作品"
                style={{ minWidth: 220 }}
                value={novelId}
                onChange={(v) => setNovelId(v)}
                options={novels.map((n) => ({ label: n.title, value: n.id }))}
                notFoundContent={<Empty description="还没有作品，请先创建" />}
              />
              {mode === "storyboard" && (
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择章节"
                  style={{ minWidth: 260 }}
                  value={chapterId}
                  onChange={(v) => setChapterId(v)}
                  options={chapters.map((c) => ({
                    label: `第${c.chapterOrder}章 ${c.title || "未命名"}`,
                    value: c.id,
                  }))}
                  notFoundContent={<Empty description="该作品还没有章节" />}
                />
              )}
              <span className="muted" style={{ fontSize: 12 }}>
                {mode === "storyboard"
                  ? "锚点来自作品的角色/世界观设定，结果会保存到作品详情"
                  : "依据全书大纲生成 15-30 秒宣传片分镜，结果会保存到作品详情"}
              </span>
            </Space>
          ) : (
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
              <Input
                placeholder="书名（可选，用于宣传片末镜头字幕）"
                maxLength={60}
                value={pasteTitle}
                onChange={(e) => setPasteTitle(e.target.value)}
                style={{ maxWidth: 360 }}
              />
              <Input.TextArea
                placeholder="粘贴小说章节正文（至少 50 字；过长自动截取前部分）。AI 会从正文自行提炼风格与角色锚点。"
                value={pasteContent}
                onChange={(e) => setPasteContent(e.target.value)}
                autoSize={{ minRows: 6, maxRows: 12 }}
                showCount
                maxLength={20000}
              />
              <span className="muted" style={{ fontSize: 12 }}>
                外部文本不会保存到平台，生成结果请及时复制
              </span>
            </Space>
          )}

          <div>
            <Button
              type="primary"
              icon={<VideoCameraOutlined />}
              loading={generating}
              disabled={!canGenerate}
              onClick={() => void generate()}
              style={{
                background: "linear-gradient(135deg, #6a5acd, #9d7bde)",
                border: "none",
              }}
            >
              {text && !generating ? "重新生成（覆盖当前结果）" : "生成提示词"}
            </Button>
          </div>
        </Space>
      </div>

      {generating && !text ? (
        <div className="section-card" style={{ textAlign: "center", padding: "48px 0" }}>
          <Spin />
          <div style={{ color: "#9a9ab0", marginTop: 12 }}>AI 正在拆解镜头…</div>
        </div>
      ) : !text ? (
        <div className="section-card" style={{ textAlign: "center", padding: "40px 0" }}>
          <Empty description="还没有生成结果：选择来源与模式后点击「生成提示词」" />
        </div>
      ) : (
        <div className="section-card">
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: 12,
            }}
          >
            <Typography.Text strong>生成结果</Typography.Text>
            <Tooltip title="粘贴到视频平台生成素材">
              <Button type="primary" icon={<CopyOutlined />} disabled={generating} onClick={() => void copyAll()}>
                复制全部
              </Button>
            </Tooltip>
          </div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="每个镜头复制时会连同「风格锚点 + 角色锚点」一起复制：锚点逐字一致才能避免跨镜头人物变脸。"
          />
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {anchorText && (
              <div className="video-shot-card">
                <div className="video-shot-head">
                  <span className="video-shot-name">锚点（风格 / 角色）</span>
                  <Button size="small" icon={<CopyOutlined />} onClick={() => void copyAnchor()}>
                    复制锚点
                  </Button>
                </div>
                <div className="video-shot-body">{anchorText}</div>
              </div>
            )}
            {shots.map((shot, i) => (
              <div key={`shot-${i}`} className="video-shot-card">
                <div className="video-shot-head">
                  <span className="video-shot-name">
                    {(shot.match(/【镜头[^\]]*\]/)?.[0] ?? `镜头 ${i + 1}`).replace(/[[\]]/g, "")}
                  </span>
                  <Button size="small" icon={<CopyOutlined />} onClick={() => void copyShot(shot)}>
                    复制本镜头（含锚点）
                  </Button>
                </div>
                <div className="video-shot-body">{shot}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
