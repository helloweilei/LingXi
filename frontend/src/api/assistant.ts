import { get } from './http';
import { streamSse } from './ai';

/** 一条对话消息：role 为 user / assistant；kind='outline' 表示大纲文档卡片 */
export interface AssistantMsg {
  role: 'user' | 'assistant';
  content: string;
  kind?: 'outline';
  /** 本次回复是否生成失败（渲染为错误样式，不参与后续大纲生成的依据） */
  error?: boolean;
  /** 大纲生成后自动创建的作品 id（展示「前往作品详情」跳转） */
  novelId?: number;
  /** 自动创建的作品书名 */
  novelTitle?: string;
  /** 作品自动创建失败（展示重试入口） */
  createFailed?: boolean;
}

/** 一条归档的历史对话（开启新对话时当前对话转入此处，可随时切回） */
export interface AssistantThread {
  id: string;
  title: string;
  updatedAt: number;
  messages: AssistantMsg[];
}

/** 历史对话在 localStorage 中的持久化键 */
export const ASSISTANT_HISTORY_KEY = 'lingxi-assistant-history-v1';
/** 历史对话保留条数上限：超出时丢弃最旧的 */
export const MAX_HISTORY_THREADS = 30;

/** 读取历史对话（脏数据直接丢弃） */
export function loadHistory(): AssistantThread[] {
  try {
    const raw = localStorage.getItem(ASSISTANT_HISTORY_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr.filter((t: any) => t && t.id != null && Array.isArray(t.messages));
  } catch {
    return [];
  }
}

/** 持久化历史对话（超出上限静默截断） */
export function saveHistory(list: AssistantThread[]) {
  try {
    localStorage.setItem(ASSISTANT_HISTORY_KEY, JSON.stringify(list.slice(0, MAX_HISTORY_THREADS)));
  } catch {
    /* 存储不可用时忽略，仅影响下次恢复 */
  }
}

/**
 * 诉求收集完毕的信号标记：模型在用户表示没有更多要求时，于回复末尾输出该行。
 * 前端渲染时剥离该标记，并据此展示「生成小说大纲」入口。
 */
export const CHAT_READY_MARKER = '【诉求已收集完毕】';

/** 创作助手对话在 localStorage 中的持久化键（刷新/重进恢复上次对话） */
export const ASSISTANT_STORAGE_KEY = 'lingxi-assistant-thread-v1';

/** 去掉 role/kind 等前端字段，只传后端需要的字段 */
function toWire(messages: AssistantMsg[]) {
  return messages
    .filter((m) => m.kind !== 'outline')
    .map(({ role, content }) => ({ role, content }));
}

type Handlers = Parameters<typeof streamSse>[2];

/** 开场引导问题响应：AI 依据当前热门题材生成，失败时为服务端静态模板 */
export interface OpenersRes {
  openers: string[];
}

export const assistantApi = {
  /** 开场引导问题（初次进入时展示在欢迎气泡下方，点选即可发送） */
  openers: () => get<OpenersRes>('/assistant/openers'),

  /** 创作诉求收集聊天（流式）：messages 为按时间先前的全量对话 */
  chat: (messages: AssistantMsg[], handlers: Handlers, signal?: AbortSignal) =>
    streamSse('/api/assistant/chat', { messages: toWire(messages) }, handlers, signal),

  /** 根据收集到的诉求生成小说大纲（流式） */
  outline: (messages: AssistantMsg[], handlers: Handlers, signal?: AbortSignal) =>
    streamSse('/api/assistant/outline', { messages: toWire(messages) }, handlers, signal),
};
