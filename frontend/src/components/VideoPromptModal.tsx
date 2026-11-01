import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Drawer, Popconfirm, Space, Tooltip, message } from 'antd';
import { CopyOutlined, DeleteOutlined, ReloadOutlined, VideoCameraOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { videoPromptApi, type VideoPromptVO } from '../api';
import { streamSse } from '../api/ai';
import { copyText } from '../utils/clipboard';

interface Props {
  open: boolean;
  onClose: () => void;
  novelId: number;
  novelTitle: string;
  /** 非空 → 单章分镜模式；为空 → 全书宣传片模式 */
  chapter?: { id: number; order: number; title: string } | null;
  /** 生成完成 / 删除后通知父级刷新「已生成」角标 */
  onChanged?: () => void;
}

/**
 * 视频提示词生成（阶段一：只产出提示词，不调用视频模型）。
 * 已生成过的直接展示（不再自动重新生成），支持重新生成（覆盖旧稿）与删除。
 * 产出按「风格锚点 + 角色锚点 + 逐镜头」组织——每个镜头可连同锚点一键复制，
 * 直接粘贴到即梦 / 可灵 / Runway 等平台；锚点逐字复用是缓解跨镜头人物漂移的关键。
 */
export default function VideoPromptModal({ open, onClose, novelId, novelTitle, chapter, onChanged }: Props) {
  const [text, setText] = useState('');
  const [generating, setGenerating] = useState(false);
  /** 已持久化的提示词（有 → 直接查看态：可重新生成 / 删除） */
  const [saved, setSaved] = useState<VideoPromptVO | null>(null);
  const [loadingExisting, setLoadingExisting] = useState(false);

  const mode = chapter ? 'storyboard' : 'trailer';
  const targetChapterId = chapter?.id ?? null;

  /** 拉取该目标已生成的提示词；没有则返回 null */
  const fetchExisting = async (): Promise<VideoPromptVO | null> => {
    const list = await videoPromptApi.list(novelId);
    return list.find((p) => p.chapterId === targetChapterId) ?? null;
  };

  const generate = async () => {
    setText('');
    setGenerating(true);
    try {
      await streamSse(
        `/api/ai/novels/${novelId}/video-prompt`,
        { chapterId: targetChapterId },
        { onDelta: (d) => setText((prev) => prev + d) },
      );
      // 生成完成即落库（后端流结束时 upsert）：回读拿到 id / 更新时间，并刷新父级角标
      const latest = await fetchExisting();
      setSaved(latest);
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? '生成失败，请稍后重试');
    } finally {
      setGenerating(false);
    }
  };

  const remove = async () => {
    if (!saved) return;
    try {
      await videoPromptApi.remove(novelId, saved.id);
      setSaved(null);
      setText('');
      message.success('已删除');
      onChanged?.();
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    }
  };

  // 打开抽屉：先查已生成的直接展示；没有才自动生成一次
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoadingExisting(true);
    fetchExisting()
      .then((existing) => {
        if (cancelled) return;
        if (existing) {
          setSaved(existing);
          setText(existing.content);
        } else {
          void generate();
        }
      })
      .catch(() => {
        if (!cancelled) void generate();
      })
      .finally(() => {
        if (!cancelled) setLoadingExisting(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, targetChapterId]);

  /** 锚点区（风格锚点 + 角色锚点）：第一个镜头块之前的全部内容 */
  const anchorText = useMemo(() => {
    const idx = text.search(/【镜头/);
    return idx >= 0 ? text.slice(0, idx).trimEnd() : '';
  }, [text]);

  /** 逐镜头块：【镜头 N】开头到下一个镜头块之前 */
  const shots = useMemo(() => text.match(/【镜头[\s\S]*?(?=\n【镜头|$)/g) ?? [], [text]);

  const copyAll = async () => {
    try {
      await copyText(text);
      message.success('全部提示词已复制');
    } catch {
      message.error('复制失败，请手动选中文本复制');
    }
  };

  /** 复制单个镜头时连同锚点一起复制——粘贴到视频平台时本就需要完整上下文 */
  const copyShot = async (shot: string) => {
    try {
      await copyText(anchorText ? `${anchorText}\n\n${shot}` : shot);
      message.success('已复制该镜头（含锚点）');
    } catch {
      message.error('复制失败，请手动选中文本复制');
    }
  };

  const title = mode === 'storyboard' ? '本章视频分镜提示词' : '宣传片视频提示词';

  return (
    <Drawer
      title={
        <Space wrap>
          <VideoCameraOutlined style={{ color: '#6a5acd' }} />
          <span>{title}</span>
          <span style={{ fontSize: 12, color: '#9a9ab0', fontWeight: 400 }}>
            {mode === 'storyboard'
              ? `第 ${chapter?.order} 章《${chapter?.title}》`
              : `《${novelTitle}》· 15-30 秒`}
          </span>
          {saved && !generating && (
            <span style={{ fontSize: 12, color: '#9a9ab0', fontWeight: 400 }}>
              生成于 {dayjs(saved.updatedAt).format('MM-DD HH:mm')}
            </span>
          )}
        </Space>
      }
      open={open}
      onClose={onClose}
      width={640}
      destroyOnHidden
      footer={
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            {saved && (
              <Popconfirm
                title="删除后不可恢复，确定删除这份提示词？"
                okText="删除"
                okButtonProps={{ danger: true }}
                onConfirm={() => void remove()}
              >
                <Button danger icon={<DeleteOutlined />} disabled={generating}>
                  删除
                </Button>
              </Popconfirm>
            )}
            <Button icon={<ReloadOutlined />} onClick={() => void generate()} loading={generating}>
              {text ? '重新生成（覆盖旧稿）' : '生成'}
            </Button>
          </Space>
          <Tooltip title={mode === 'storyboard' ? '粘贴到即梦 / 可灵等平台，逐镜头生成后剪辑' : '粘贴到视频平台生成宣传片素材'}>
            <Button type="primary" icon={<CopyOutlined />} disabled={!text || generating} onClick={() => void copyAll()}>
              复制全部
            </Button>
          </Tooltip>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 14 }}
        message="每个镜头复制时会连同「风格锚点 + 角色锚点」一起复制：视频平台需要完整上下文，锚点逐字一致才能避免跨镜头人物变脸。"
      />
      {loadingExisting || (generating && !text) ? (
        <div style={{ color: '#9a9ab0', padding: '40px 0', textAlign: 'center' }}>
          {loadingExisting ? '正在查询已生成的提示词…' : 'AI 正在拆解镜头…'}
        </div>
      ) : !text ? (
        <div style={{ color: '#9a9ab0', padding: '40px 0', textAlign: 'center' }}>还没有生成提示词</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {anchorText && (
            <div className="video-shot-card">
              <div className="video-shot-head">
                <span className="video-shot-name">锚点（风格 / 角色）</span>
                <Button size="small" icon={<CopyOutlined />} onClick={() => void copyText2(anchorText, '锚点已复制')}>
                  复制锚点
                </Button>
              </div>
              <div className="video-shot-body">{anchorText}</div>
            </div>
          )}
          {shots.map((shot, i) => (
            <div key={i} className="video-shot-card">
              <div className="video-shot-head">
                <span className="video-shot-name">{(shot.match(/【镜头[^\]]*\]/)?.[0] ?? `镜头 ${i + 1}`).replace(/[[\]]/g, '')}</span>
                <Button size="small" icon={<CopyOutlined />} onClick={() => void copyShot(shot)}>
                  复制本镜头（含锚点）
                </Button>
              </div>
              <div className="video-shot-body">{shot}</div>
            </div>
          ))}
        </div>
      )}
    </Drawer>
  );
}

/** 小包装：统一复制成功/失败提示文案 */
async function copyText2(text: string, successTip: string) {
  try {
    await copyText(text);
    message.success(successTip);
  } catch {
    message.error('复制失败，请手动选中文本复制');
  }
}
