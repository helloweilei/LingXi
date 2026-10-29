import { useCallback, useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Button, Tooltip, message } from 'antd';
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CopyOutlined,
  DeleteOutlined,
  FileTextOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { novelApi } from '../api';
import {
  ASSISTANT_STORAGE_KEY,
  CHAT_READY_MARKER,
  MAX_HISTORY_THREADS,
  assistantApi,
  loadHistory,
  saveHistory,
} from '../api/assistant';
import type { AssistantMsg, AssistantThread } from '../api/assistant';
import { copyText } from '../utils/clipboard';
import '../assistant.css';

/** 初次进入的启发式提问兜底模板：AI 开场问题接口失败时使用 */
const QUICK_ASKS = [
  '我想写玄幻修仙，帮我出出主意',
  '我有个灵感画面，想跟你聊聊怎么展开',
  '还没想好，先帮我头脑风暴几个脑洞',
];

/** 首次进入时助手的自我介绍 + 启发式提问（本地消息，不走 AI，秒出） */
const WELCOME_CONTENT = `你好呀，我是你的创作助手 小小灵。 不用打草稿、不用列提纲——一个画面、一句台词、甚至一个模糊的念头，都可以直接丢给我，我会帮你把它聊成一部能动笔的小说。

为了更快抓住你的想法，先问你三个小问题：
① 想写什么题材？（玄幻、都市、科幻、悬疑、言情……都行）
② 脑子里有没有已经冒出来的画面或脑洞？哪怕只有一句话
③ 想要什么基调？（热血爽文、轻松治愈、悬疑烧脑……）

不确定从哪开始？点下面任意一个，我们边聊边把你的故事聊出来 👇`;

/** 从 localStorage 恢复上次对话（脏数据直接丢弃，从新对话开始） */
function loadThread(): AssistantMsg[] {
  try {
    const raw = localStorage.getItem(ASSISTANT_STORAGE_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr.filter(
      (m: any) =>
        m &&
        typeof m.content === 'string' &&
        (m.role === 'user' || m.role === 'assistant'),
    );
  } catch {
    return [];
  }
}

/** 渲染用：剥离“诉求已收集完毕”信号标记（这是给前端看的协议，不展示给用户） */
function displayText(t: string) {
  return t.split(CHAT_READY_MARKER).join('').trimEnd();
}

/** 全新的初次对话：以助手自我介绍开场 */
function freshThread(): AssistantMsg[] {
  return [{ role: 'assistant', content: WELCOME_CONTENT }];
}

function isWelcome(m: AssistantMsg) {
  return m.role === 'assistant' && m.content === WELCOME_CONTENT;
}

/** 大纲文本解析出的作品元信息：书名 / 题材 / 简介 */
interface NovelMeta {
  title: string;
  genre: string;
  description: string;
}

/** 从大纲文本取“关键词：xxx”行的内容（兼容“1. 书名：”“**题材与基调：**”等格式） */
function lineAfterLabel(text: string, label: string): string | null {
  const re = new RegExp(`${label}[^：:\\n]*[：:]\\s*([^\\n]+)`);
  const m = text.match(re);
  return m ? m[1].trim() : null;
}

/** 从大纲文本解析书名：优先“书名：”行（去掉备选括注），其次第一个《书名》，兜底通用名 */
function extractTitle(outline: string): string {
  const line = lineAfterLabel(outline, '书名');
  if (line) {
    let t = line
      .replace(/[（(].*$/, '') // 去掉“（备选：…）”
      .replace(/[，,。；;、].*$/, '') // 去掉同行的后续说明
      .replace(/[《》「」『』"“”]/g, '')
      .trim();
    if (t) return t.slice(0, 80);
  }
  const quoted = outline.match(/《([^《》\n]{1,50})》/);
  if (quoted) return quoted[1].trim();
  return 'AI 大纲作品';
}

/** 从大纲文本解析题材：取“题材…：”行第一个分段，落库字段最长 30 字 */
function extractGenre(outline: string): string {
  const line = lineAfterLabel(outline, '题材');
  if (!line) return '';
  const seg = line.split(/[，,。；;、（(]/)[0].trim();
  return seg ? seg.slice(0, 30) : '';
}

/** 简介来源：优先“核心设定”行，否则用大纲正文压平后的前 200 字 */
function extractDescription(outline: string): string {
  const core = lineAfterLabel(outline, '核心设定');
  if (core) return core.slice(0, 500);
  return outline.replace(/\s+/g, ' ').trim().slice(0, 200);
}

function extractNovelMeta(outline: string): NovelMeta {
  return {
    title: extractTitle(outline),
    genre: extractGenre(outline),
    description: extractDescription(outline),
  };
}

export default function Assistant() {
  const navigate = useNavigate();
  const [messages, setMessages] = useState<AssistantMsg[]>(() => {
    const saved = loadThread();
    // 初次进入（无历史对话）：以助手自我介绍 + 启发式提问开场
    return saved.length ? saved : freshThread();
  });
  const [input, setInput] = useState('');
  const [chatBusy, setChatBusy] = useState(false);
  const [outlineBusy, setOutlineBusy] = useState(false);
  // 历史对话（开启新对话时归档当前对话，可随时切回）
  const [history, setHistory] = useState<AssistantThread[]>(loadHistory);
  // MainLayout 顶部 header 的实际高度：固定壳的 top 需要精确贴在它下方
  const [topOffset, setTopOffset] = useState(57);

  const scrollRef = useRef<HTMLDivElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  // messages 的实时镜像：流式回调 / await 之后的续段逻辑里读取最新值（闭包里的 state 是旧的）
  const messagesRef = useRef(messages);
  messagesRef.current = messages;
  const busy = chatBusy || outlineBusy;

  useEffect(() => {
    const h = document.querySelector('.app-header');
    if (h) setTopOffset(h.getBoundingClientRect().height);
  }, []);

  // 对话持久化：刷新 / 下次进入自动恢复
  useEffect(() => {
    try {
      localStorage.setItem(ASSISTANT_STORAGE_KEY, JSON.stringify(messages));
    } catch {
      /* 存储不可用时忽略，仅影响恢复 */
    }
  }, [messages]);

  // 新消息 / 流式输出时贴底
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, chatBusy, outlineBusy]);

  // 离开页面时中断进行中的流
  useEffect(() => () => abortRef.current?.abort(), []);

  /** 流式请求一个助手回复（kind='outline' 时生成大纲文档卡片） */
  const streamReply = useCallback(
    async (history: AssistantMsg[], kind?: 'outline') => {
      const base = [...history];
      const live: AssistantMsg = { role: 'assistant', content: '', ...(kind ? { kind } : {}) };
      setMessages([...base, live]);
      if (kind) {
        setOutlineBusy(true);
      } else {
        setChatBusy(true);
      }
      const ctrl = new AbortController();
      abortRef.current = ctrl;
      let acc = '';
      let failed = false;
      try {
        const api = kind ? assistantApi.outline : assistantApi.chat;
        await api(
          base,
          {
            onDelta: (d) => {
              acc += d;
              setMessages([...base, { ...live, content: acc }]);
            },
          },
          ctrl.signal,
        );
      } catch (e: any) {
        if (e?.name === 'AbortError') {
          // 用户主动停止：保留已生成的部分
        } else {
          failed = true;
          if (!acc) {
            setMessages([...base, { ...live, content: `生成失败：${e?.message ?? '请稍后重试'}`, error: true }]);
          }
          message.error(e?.message ?? '生成失败');
        }
      } finally {
        abortRef.current = null;
        setChatBusy(false);
        setOutlineBusy(false);
        // 流没有任何输出且未失败（如被中止）：移除空气泡，避免留下一块空白
        if (!acc && !failed) {
          setMessages((prev) => prev.filter((m) => m !== live));
        }
      }
    },
    [],
  );

  const send = useCallback(
    async (raw?: string) => {
      const content = (raw ?? input).trim();
      if (!content || busy) return;
      setInput('');
      if (taRef.current) taRef.current.style.height = 'auto';
      const next = [...messages, { role: 'user', content } as AssistantMsg];
      await streamReply(next);
    },
    [input, busy, messages, streamReply],
  );

  /** 取当前对话是否有实际内容（只有欢迎语不值得归档） */
  const hasRealContent = (msgs: AssistantMsg[]) => msgs.some((m) => m.role === 'user' && m.content.trim());

  /** 当前对话的标题：取第一条用户消息，截断展示 */
  const threadTitle = (msgs: AssistantMsg[]) => {
    const firstUser = msgs.find((m) => m.role === 'user' && m.content.trim());
    const t = firstUser ? firstUser.content.replace(/\s+/g, ' ').trim() : '';
    return t ? (t.length > 22 ? `${t.slice(0, 22)}…` : t) : '未命名对话';
  };

  /** 把当前对话归档进历史列表（置顶，超限丢最旧） */
  const archiveCurrent = useCallback(() => {
    const cur = messagesRef.current;
    if (!hasRealContent(cur)) return;
    const item: AssistantThread = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title: threadTitle(cur),
      updatedAt: Date.now(),
      messages: cur,
    };
    setHistory((prev) => {
      const next = [item, ...prev].slice(0, MAX_HISTORY_THREADS);
      saveHistory(next);
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 切回某条历史对话：当前对话先归档，选中的从历史里取出加载 */
  const openThread = (id: string) => {
    if (busy) {
      message.info('请先等待当前回复完成');
      return;
    }
    const item = history.find((h) => h.id === id);
    if (!item) return;
    abortRef.current?.abort();
    archiveCurrent();
    setHistory((prev) => {
      const next = prev.filter((h) => h.id !== id);
      saveHistory(next);
      return next;
    });
    setMessages(item.messages);
    setInput('');
  };

  /** 删除一条历史对话 */
  const removeThread = (id: string) => {
    setHistory((prev) => {
      const next = prev.filter((h) => h.id !== id);
      saveHistory(next);
      return next;
    });
  };

  /** 为大纲卡片确保已创建对应作品：解析书名/题材/简介 → 创建作品 → 写入全书大纲 */
  const ensureNovelForOutline = useCallback(async (msg: AssistantMsg) => {
    if (msg.novelId) return;
    const text = displayText(msg.content);
    if (!text.trim()) return;
    const meta = extractNovelMeta(text);
    try {
      const novel = await novelApi.create({
        title: meta.title,
        genre: meta.genre,
        description: meta.description,
      });
      // 创建接口不收大纲字段，落库走部分更新
      await novelApi.update(novel.id, { outline: text });
      setMessages((prev) =>
        prev.map((x) => (x === msg ? { ...x, novelId: novel.id, novelTitle: novel.title || meta.title, createFailed: false } : x)),
      );
      message.success(`已自动创建作品《${novel.title || meta.title}》`);
    } catch (e: any) {
      setMessages((prev) => prev.map((x) => (x === msg ? { ...x, createFailed: true } : x)));
      message.error(e?.message ?? '自动创建作品失败，可稍后重试');
    }
  }, []);

  const generateOutline = useCallback(async () => {
    if (busy) return;
    // 大纲依据：诉求对话本身（排除历史大纲卡片与本地欢迎语，避免自我引用）
    const context = messages.filter((m) => m.kind !== 'outline' && !isWelcome(m) && m.content.trim());
    if (context.length === 0) {
      message.warning('先和助手聊聊你的想法');
      return;
    }
    await streamReply(context, 'outline');
    // 生成完成后自动创建作品并提供跳转（读取 ref 里的最新消息，闭包 state 已过期）
    const last = [...messagesRef.current].reverse().find((m) => m.kind === 'outline');
    if (last && displayText(last.content).trim()) {
      await ensureNovelForOutline(last);
    }
  }, [busy, messages, streamReply, ensureNovelForOutline]);

  const newChat = () => {
    abortRef.current?.abort();
    // 当前对话先归档进历史（有实际内容时），再以自我介绍开场
    archiveCurrent();
    setMessages(freshThread());
    setInput('');
    try {
      localStorage.removeItem(ASSISTANT_STORAGE_KEY);
    } catch {
      /* ignore */
    }
  };

  const lastBot = [...messages].reverse().find((m) => m.role === 'assistant' && m.kind !== 'outline');
  const ready = !busy && !!lastBot && lastBot.content.includes(CHAT_READY_MARKER);
  const lastIsOutline = messages[messages.length - 1]?.kind === 'outline';
  /** 初次进入 / 新对话：只有欢迎语时展示 hero + 引导问题 */
  const firstVisit = messages.length === 1 && isWelcome(messages[0]);

  // 启发式提问：先渲染静态模板，AI 依据当前热门题材生成的开场问题返回后替换
  const [asks, setAsks] = useState<string[]>(QUICK_ASKS);
  useEffect(() => {
    if (!firstVisit) return;
    let cancelled = false;
    assistantApi
      .openers()
      .then((res) => {
        const list = (res?.openers ?? []).filter((s) => typeof s === 'string' && s.trim());
        if (!cancelled && list.length >= 2) {
          setAsks(list.slice(0, 3));
        }
      })
      .catch(() => {
        /* 拉取失败：保留静态模板 */
      });
    return () => {
      cancelled = true;
    };
  }, [firstVisit]);

  const onTaChange = (v: string) => {
    setInput(v);
    const el = taRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
    }
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter 发送，Shift+Enter 换行（与主流聊天应用一致）；中文输入法选词回车不发送
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void send();
    }
  };

  return (
    <div className="assistant-shell" style={{ top: topOffset }}>
      {/* 左侧栏 */}
      <aside className="assistant-side">
        <div className="side-title">创作助手</div>
        <div className="side-sub">聊聊你的想法，我来帮你变成小说</div>
        <Button
          className="assistant-new-chat"
          icon={<PlusOutlined />}
          onClick={newChat}
          block
        >
          开启新对话
        </Button>
        <div className="assistant-steps">
          <div className="step">
            <span className="step-no">1</span>
            <span>像聊天一样说出你的想法：题材、主角、想要的爽点……</span>
          </div>
          <div className="step">
            <span className="step-no">2</span>
            <span>助手会帮你补齐缺失的关键设定，随时可以补充新要求</span>
          </div>
          <div className="step">
            <span className="step-no">3</span>
            <span>没有更多要求时，一键生成完整的小说大纲</span>
          </div>
        </div>
        <div className="assistant-history">
          <div className="history-head">历史对话</div>
          {history.length === 0 ? (
            <div className="history-empty">开启新对话后，之前的对话会保存在这里</div>
          ) : (
            <div className="history-list">
              {history.map((h) => (
                <div key={h.id} className="history-item" title={h.title} onClick={() => openThread(h.id)}>
                  <span className="history-title">{h.title}</span>
                  <Tooltip title="删除这条记录">
                    <Button
                      type="text"
                      size="small"
                      className="history-del"
                      icon={<DeleteOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        removeThread(h.id);
                      }}
                    />
                  </Tooltip>
                </div>
              ))}
            </div>
          )}
        </div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')} block style={{ marginTop: 'auto' }}>
          返回作品台
        </Button>
        <div className="side-foot">对话会自动保存在本机，刷新不丢失</div>
      </aside>

      {/* 主区 */}
      <div className="assistant-main">
        <div className="assistant-scroll" ref={scrollRef}>
          {firstVisit && (
            <div className="assistant-hero with-thread">
              <div className="hero-orb">
                <RobotOutlined />
              </div>
              <h1 className="hero-title">你好，我是创作助手</h1>
              <p className="hero-sub">把想法聊成一部能动笔的小说大纲</p>
            </div>
          )}
          {messages.length > 0 && (
            <div className="assistant-thread">
              {messages.map((m, i) => {
                if (m.kind === 'outline') {
                  const text = displayText(m.content);
                  return (
                    <div key={i} className="assistant-outline-card">
                      <div className="outline-head">
                        <span className="outline-title">
                          <FileTextOutlined /> 小说大纲
                        </span>
                        <Tooltip title="复制全文">
                          <Button
                            size="small"
                            icon={<CopyOutlined />}
                            disabled={!text}
                            onClick={async () => {
                              try {
                                await copyText(text);
                                message.success('大纲已复制到剪贴板');
                              } catch {
                                message.error('复制失败，请手动选中文本复制');
                              }
                            }}
                          >
                            复制大纲
                          </Button>
                        </Tooltip>
                      </div>
                      <div className="outline-body">
                        {outlineBusy && !text ? (
                          <span className="assistant-typing">
                            <i />
                            <i />
                            <i />
                          </span>
                        ) : (
                          <>
                            {text}
                            {outlineBusy && i === messages.length - 1 && <span className="assistant-caret" />}
                          </>
                        )}
                      </div>
                      {!outlineBusy && (m.novelId || m.createFailed) && (
                        <div className="outline-created">
                          {m.novelId ? (
                            <>
                              <span className="created-text">已自动创建作品《{m.novelTitle}》</span>
                              <Button
                                type="primary"
                                size="small"
                                icon={<ArrowRightOutlined />}
                                onClick={() => navigate(`/novel/${m.novelId}`)}
                              >
                                前往作品详情
                              </Button>
                            </>
                          ) : (
                            <>
                              <span className="created-text">作品自动创建失败</span>
                              <Button size="small" onClick={() => void ensureNovelForOutline(m)}>
                                重试创建
                              </Button>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  );
                }
                const fromUser = m.role === 'user';
                const isLast = i === messages.length - 1;
                const streamingThis = !fromUser && isLast && chatBusy;
                const waiting = streamingThis && !m.content;
                return (
                  <div key={i} className={`assistant-msg ${fromUser ? 'from-user' : 'from-bot'}`}>
                    <div className={`assistant-avatar ${fromUser ? 'me' : 'bot'}`}>
                      {fromUser ? <UserOutlined /> : <RobotOutlined />}
                    </div>
                    <div className={`assistant-bubble${m.error ? ' error' : ''}`}>
                      {waiting ? (
                        <span className="assistant-typing">
                          <i />
                          <i />
                          <i />
                        </span>
                      ) : (
                        <>
                          {displayText(m.content)}
                          {streamingThis && <span className="assistant-caret" />}
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
              {firstVisit && (
                <div className="assistant-quick-asks" key={asks[0]}>
                  {asks.map((q, i) => (
                    <button
                      key={i}
                      className="assistant-chip"
                      style={{ animationDelay: `${0.4 + i * 0.12}s` }}
                      onClick={() => void send(q)}
                    >
                      {q}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* 诉求齐备提示 + 生成入口 */}
        {ready && !lastIsOutline && (
          <div className="assistant-ready-card">
            <div className="ready-text">
              <b>诉求已收集完毕 🎉</b>
              <br />
              没有更多要求的话，现在就可以把咱们聊到的内容整理成一份完整的小说大纲。
            </div>
            <Button
              className="assistant-gen-btn"
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={() => void generateOutline()}
            >
              生成小说大纲
            </Button>
          </div>
        )}

        {/* 输入区 */}
        <div className="assistant-composer">
          <div className="composer-box">
            <textarea
              ref={taRef}
              rows={1}
              value={input}
              onChange={(e) => onTaChange(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="说出你的灵感，Enter 发送，Shift+Enter 换行"
              disabled={busy}
            />
            <div className="composer-foot">
              <span className="composer-hint">
                {busy
                  ? outlineBusy
                    ? '大纲生成中，可随时停止…'
                    : '助手思考中…'
                  : 'Enter 发送 · Shift+Enter 换行'}
              </span>
              {busy ? (
                <Button
                  className="assistant-send-btn"
                  icon={<SendOutlined />}
                  onClick={() => abortRef.current?.abort()}
                >
                  停止
                </Button>
              ) : (
                <Button
                  className="assistant-send-btn"
                  icon={<SendOutlined />}
                  disabled={!input.trim()}
                  onClick={() => void send()}
                >
                  发送
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
