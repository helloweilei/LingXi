import { get, post } from './http';
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
}

export const guideApi = {
  start: (seed?: string) => post<StartResp>('/guide/start', seed ? { seed } : {}),
  answer: (id: number, answer: string) => post<AnswerResp>(`/guide/${id}/answer`, { answer }),
  get: (id: number) => get<GuideState>(`/guide/${id}`),
  select: (id: number, index: number) =>
    post<{ novelId: number }>(`/guide/${id}/select`, { proposalIndex: index }),
  reset: (id: number) => post<void>(`/guide/${id}/reset`, {}),
};

export function generateProposalStream(
  id: number,
  handlers: Parameters<typeof streamSse>[2],
  signal?: AbortSignal
) {
  return streamSse(`/api/guide/${id}/generate`, null, handlers, signal);
}
