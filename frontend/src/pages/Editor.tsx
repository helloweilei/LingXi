import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Input, Space, Tooltip, message } from 'antd';
import {
  AlignLeftOutlined,
  ArrowLeftOutlined,
  ExpandOutlined,
  HighlightOutlined,
  HistoryOutlined,
  RobotOutlined,
  SaveOutlined,
  ScanOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { chapterApi, novelApi } from '../api';
import { streamSse } from '../api/ai';
import type { Chapter, NovelDetail } from '../api/types';
import AiDrawer from '../components/AiDrawer';
import ProofreadPanel from '../components/ProofreadPanel';
import VersionHistoryModal from '../components/VersionHistoryModal';

type DrawerMode = 'continue' | 'polish' | 'expand';

export default function Editor() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const cid = Number(chapterId);

  const [chapter, setChapter] = useState<Chapter | null>(null);
  const [novel, setNovel] = useState<NovelDetail | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [saveState, setSaveState] = useState('');
  const [drawer, setDrawer] = useState<DrawerMode | null>(null);
  const [selText, setSelText] = useState('');
  const [proofOpen, setProofOpen] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);

  const taRef = useRef<HTMLTextAreaElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dirtyRef = useRef(false);
  const selRef = useRef({ start: 0, end: 0 });
  const latestRef = useRef({ title: '', content: '' });
  latestRef.current = { title, content };

  const doSave = useCallback(async () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setSaveState('保存中…');
    try {
      const saved = await chapterApi.save(cid, {
        title: latestRef.current.title,
        content: latestRef.current.content,
      });
      setChapter(saved);
      dirtyRef.current = false;
      setSaveState(`已保存 ${dayjs().format('HH:mm:ss')}`);
    } catch (e: any) {
      setSaveState('保存失败');
    }
  }, [cid]);

  const scheduleSave = useCallback(() => {
    dirtyRef.current = true;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    timerRef.current = setTimeout(doSave, 2500);
  }, [doSave]);

  useEffect(() => {
    (async () => {
      try {
        const ch = await chapterApi.get(cid);
        setChapter(ch);
        setTitle(ch.title);
        setContent(ch.content ?? '');
        setSaveState(`已保存 ${dayjs(ch.updatedAt).format('HH:mm:ss')}`);
        const nv = await novelApi.detail(ch.novelId);
        setNovel(nv);
      } catch (e: any) {
        message.error(e?.message ?? '章节加载失败');
        navigate('/');
      }
    })();
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      if (dirtyRef.current) {
        chapterApi
          .save(cid, { title: latestRef.current.title, content: latestRef.current.content })
          .catch(() => undefined);
      }
    };
  }, [cid, navigate]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        doSave();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [doSave]);

  const wordCount = content.replace(/\s/g, '').length;

  const insertAtCursor = (text: string) => {
    const { start, end } = selRef.current;
    const base = latestRef.current.content;
    const anchor = Math.min(start, base.length);
    const anchorEnd = Math.min(end, base.length);
    const next = base.slice(0, anchor) + text + base.slice(anchorEnd);
    setContent(next);
    latestRef.current = { ...latestRef.current, content: next };
    dirtyRef.current = true;
    scheduleSave();
    const pos = anchor + text.length;
    selRef.current = { start: pos, end: pos };
    requestAnimationFrame(() => {
      taRef.current?.focus();
      taRef.current?.setSelectionRange(pos, pos);
    });
  };

  const replaceSelection = (text: string) => {
    const { start, end } = selRef.current;
    const base = latestRef.current.content;
    const anchor = Math.min(start, base.length);
    const anchorEnd = Math.min(end, base.length);
    const next = base.slice(0, anchor) + text + base.slice(anchorEnd);
    setContent(next);
    latestRef.current = { ...latestRef.current, content: next };
    dirtyRef.current = true;
    scheduleSave();
    const pos = anchor + text.length;
    selRef.current = { start: pos, end: pos };
  };

  const openAi = (mode: DrawerMode) => {
    if (mode !== 'continue') {
      const { start, end } = selRef.current;
      const selected = content.slice(start, end);
      if (!selected.trim()) {
        message.warning('请先在正文中选中一段文字');
        return;
      }
      setSelText(selected);
    }
    setDrawer(mode);
  };

  /** 应用错别字修正：整篇替换内容并触发保存，返回是否成功 */
  const applyProofreadFix = (next: string) => {
    setContent(next);
    latestRef.current = { ...latestRef.current, content: next };
    dirtyRef.current = true;
    scheduleSave();
    return true;
  };

  const drawerTitle = { continue: 'AI 续写', polish: 'AI 润色', expand: 'AI 扩写' }[drawer ?? 'continue'];
  const applyText = { continue: '插入到光标处', polish: '替换所选内容', expand: '替换所选内容' }[
    drawer ?? 'continue'
  ];

  return (
    <div className="editor-shell">
      <div className="editor-topbar">
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(`/novel/${chapter?.novelId ?? ''}`)}
        />
        <Input
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            scheduleSave();
          }}
          variant="borderless"
          style={{ fontSize: 17, fontWeight: 700, width: 360, color: '#33334a' }}
          placeholder="章节名"
          maxLength={120}
        />
        <span className="muted" style={{ fontSize: 13, minWidth: 120 }}>
          {saveState}
        </span>
        <div style={{ flex: 1 }} />
        <span className="muted" style={{ fontSize: 13 }}>
          {wordCount.toLocaleString()} 字
        </span>
        <Space size={8}>
        <Tooltip title="查看历史版本，可预览并回滚">
            <Button icon={<HistoryOutlined />} onClick={() => setVersionOpen(true)}>
              版本历史
            </Button>
          </Tooltip>
          <Tooltip title="基于全书设定与上下文，从光标处续写">
            <Button type="primary" ghost icon={<RobotOutlined />} onClick={() => openAi('continue')}>
              AI 续写
            </Button>
          </Tooltip>
          <Tooltip title="检查全文的错别字、词语误用与标点问题">
            <Button icon={<ScanOutlined />} onClick={() => setProofOpen(true)}>
              错别字检查
            </Button>
          </Tooltip>
          <Button icon={<HighlightOutlined />} onClick={() => openAi('polish')}>
            润色
          </Button>
          <Button icon={<ExpandOutlined />} onClick={() => openAi('expand')}>
            扩写
          </Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={doSave}>
            保存
          </Button>
        </Space>
      </div>

      <div className="editor-body">
        <textarea
          ref={taRef}
          className="editor-textarea"
          value={content}
          onChange={(e) => {
            setContent(e.target.value);
            scheduleSave();
          }}
          onSelect={(e: any) => {
            selRef.current = { start: e.target.selectionStart, end: e.target.selectionEnd };
          }}
          onClick={(e: any) => {
            selRef.current = { start: e.target.selectionStart, end: e.target.selectionEnd };
          }}
          placeholder={`开始写你的故事吧……\n\n· Ctrl+S 快速保存\n· 选中一段文字可使用“润色 / 扩写”\n· 卡文时点“AI 续写”，生成内容确认后才会插入正文`}
        />
      </div>

      {novel && drawer && (
        <AiDrawer
          open
          title={drawerTitle}
          applyText={applyText}
          onClose={() => setDrawer(null)}
          onApply={(t) => (drawer === 'continue' ? insertAtCursor(t) : replaceSelection(t))}
          run={(h) => {
            const url =
              drawer === 'continue'
                ? `/api/ai/novels/${novel.id}/chapters/${cid}/continue`
                : drawer === 'polish'
                  ? `/api/ai/novels/${novel.id}/polish`
                  : `/api/ai/novels/${novel.id}/expand`;
            const body = drawer === 'continue' ? null : { text: selText };
            return streamSse(url, body, h);
          }}
        />
      )}

      <ProofreadPanel
        open={proofOpen}
        content={content}
        onClose={() => setProofOpen(false)}
        onApplyFix={applyProofreadFix}
      />

      <VersionHistoryModal
        open={versionOpen}
        chapter={chapter}
        onClose={() => setVersionOpen(false)}
        onRestored={(c) => {
          // 回滚后同步编辑器内容与保存状态，避免界面与实际内容不一致
          setChapter(c);
          setTitle(c.title);
          setContent(c.content ?? '');
          latestRef.current = { title: c.title, content: c.content ?? '' };
          dirtyRef.current = false;
          setSaveState(`已回滚 ${dayjs().format('HH:mm:ss')}`);
        }}
      />
    </div>
  );
}
