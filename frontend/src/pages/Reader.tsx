import { useCallback, useEffect, useState } from 'react';
import { Button, Spin, message } from 'antd';
import {
  ArrowLeftOutlined,
  EditOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { chapterApi, novelApi } from '../api';
import type { Chapter, ChapterMeta, NovelDetail } from '../api/types';

/**
 * 章节阅读页（只读）：参考番茄小说排版——章节标题、字数/更新时间、段落留白、舒适行距。
 * 不允许直接编辑；编辑通过顶栏“编辑本章”进入写作页。
 */
export default function Reader() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const cid = Number(chapterId);

  const [chapter, setChapter] = useState<Chapter | null>(null);
  const [novel, setNovel] = useState<NovelDetail | null>(null);
  const [list, setList] = useState<ChapterMeta[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const ch = await chapterApi.get(cid);
      setChapter(ch);
      const [nv, ls] = await Promise.all([novelApi.detail(ch.novelId), chapterApi.list(ch.novelId)]);
      setNovel(nv);
      setList(ls);
    } catch (e: any) {
      message.error(e?.message ?? '章节加载失败');
      navigate(-1);
    } finally {
      setLoading(false);
    }
  }, [cid, navigate]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading || !chapter) {
    return (
      <div className="page-container" style={{ textAlign: 'center', paddingTop: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  const idx = list.findIndex((c) => c.id === cid);
  const prev = idx > 0 ? list[idx - 1] : null;
  const next = idx >= 0 && idx < list.length - 1 ? list[idx + 1] : null;
  const paragraphs = (chapter.content ?? '')
    .split(/\n+/)
    .map((s) => s.trim())
    .filter(Boolean);

  return (
    <div style={{ minHeight: '100vh', background: '#fafafd', display: 'flex', flexDirection: 'column' }}>
      {/* 顶栏 */}
      <div
        style={{
          background: '#fff',
          borderBottom: '1px solid #ececf3',
          padding: '10px 28px',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          position: 'sticky',
          top: 0,
          zIndex: 90,
        }}
      >
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/novel/${chapter.novelId}`)} />
        <span style={{ fontSize: 15, fontWeight: 600, color: '#3a3a52' }}>{novel?.title ?? ''}</span>
        <span className="muted" style={{ fontSize: 12 }}>
          共 {list.length} 章
        </span>
        <div style={{ flex: 1 }} />
        <Button type="link" icon={<EditOutlined />} onClick={() => navigate(`/write/${cid}`)}>
          编辑本章
        </Button>
      </div>

      {/* 正文区 */}
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', padding: '36px 20px 80px' }}>
        <article
          style={{
            width: 780,
            maxWidth: '100%',
            background: '#fff',
            borderRadius: 12,
            boxShadow: '0 2px 16px rgba(60,60,90,0.06)',
            padding: '52px 64px',
          }}
        >
          <h1 style={{ margin: 0, fontSize: 24, fontWeight: 600, color: '#26262e', lineHeight: 1.4 }}>
            {chapter.title}
          </h1>
          <div className="muted" style={{ fontSize: 13, margin: '12px 0 0' }}>
            本章字数：{(chapter.wordCount ?? 0).toLocaleString()}字 · 更新时间：
            {dayjs(chapter.updatedAt).format('MM-DD')}
          </div>
          <div style={{ borderTop: '1px solid #f0f0f5', margin: '24px 0 28px' }} />
          <div
            style={{
              fontFamily:
                "-apple-system, 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', 'Segoe UI', sans-serif",
              fontSize: 17,
              fontWeight: 400,
              lineHeight: 1.8,
              color: '#33333d',
            }}
          >
            {paragraphs.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                （本章还没有正文，点击右上角「编辑本章」开始写作）
              </p>
            ) : (
              paragraphs.map((p, i) => (
                <p key={i} style={{ margin: '0 0 1.2em' }}>
                  {p}
                </p>
              ))
            )}
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              borderTop: '1px solid #f0f0f5',
              paddingTop: 18,
              marginTop: 8,
            }}
          >
            <Button icon={<LeftOutlined />} disabled={!prev} onClick={() => prev && navigate(`/read/${prev.id}`)}>
              上一章
            </Button>
            <Button icon={<RightOutlined />} disabled={!next} onClick={() => next && navigate(`/read/${next.id}`)}>
              下一章
            </Button>
          </div>
        </article>
      </div>
    </div>
  );
}
