import { get, post, del } from './http';
import { streamSse } from './ai';

export interface Question {
  field: string;
  label: string;
  prompt: string;
  options?: string[];
}

export interface Proposal {
  title: string;
  premise?: string;
  world?: string;
  protagonist?: string;
  outline?: string;
  platform?: string;
  characters?: { name: string; role?: string; description?: string }[];
  worlds?: { title: string; content?: string }[];
}

export interface StartResp {
  intentId: number;
  mode: 'guided' | 'seed';
  firstQuestion?: Question;
}

export interface AnswerResp {
  question?: Question;
  done: boolean;
  step: number;
  total: number;
}

export interface GuideState {
  status: string;
  seed?: string;
  answers: Record<string, string>;
  proposals: Proposal[];
  selectedId?: number | null;
  /** 已完成题数 */
  step: number;
  /** 总题数 */
  total: number;
  /** 下一道待答题（全部答完时为 null/undefined） */
  nextQuestion?: Question | null;
}

/** 历史引导会话摘要（首页「历史方案」入口） */
export interface IntentSummary {
  id: number;
  status: string;
  seed: string | null;
  answers: Record<string, string>;
  proposalTitles: string[];
  proposalCount: number;
  selectedId: number | null;
  /** 采用该方案后生成的作品 id */
  novelId: number | null;
  /** 作品标题（作品可能已被删除，此时为 null） */
  novelTitle: string | null;
  /** 作品是否仍存在 */
  novelExists: boolean;
  createdAt: string;
  updatedAt: string;
}

export const guideApi = {
  start: (seed?: string) => post<StartResp>('/guide/start', seed ? { seed } : {}),
  answer: (id: number, answer: string) => post<AnswerResp>(`/guide/${id}/answer`, { answer }),
  get: (id: number) => get<GuideState>(`/guide/${id}`),
  /** 历史引导会话列表 */
  history: (limit = 20) => get<IntentSummary[]>(`/guide/intents?limit=${limit}`),
  select: (id: number, index: number) =>
    post<{ novelId: number }>(`/guide/${id}/select`, { proposalIndex: index }),
  /** 删除历史引导会话（不影响已生成的作品） */
  remove: (id: number) => del<void>(`/guide/${id}`),
  reset: (id: number) => post<void>(`/guide/${id}/reset`, {}),
};

export function generateProposalStream(
  id: number,
  handlers: Parameters<typeof streamSse>[2],
  signal?: AbortSignal
) {
  return streamSse(`/api/guide/${id}/generate`, null, handlers, signal);
}
