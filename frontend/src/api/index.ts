import { get, post, put, del } from './http';
import type {
  AiConfigVO,
  AuthResp,
  Chapter,
  ChapterMeta,
  CharacterVO,
  Novel,
  NovelDetail,
  WorldVO,
  WritingTaskVO,
} from './types';

export interface NovelSaveReq {
  title?: string;
  description?: string;
  genre?: string;
  status?: string;
  targetWords?: number | null;
  coverColor?: string;
  outline?: string;
}

export const authApi = {
  login: (username: string, password: string) => post<AuthResp>('/auth/login', { username, password }),
  register: (username: string, password: string, nickname: string) =>
    post<AuthResp>('/auth/register', { username, password, nickname }),
  me: () => get('/auth/me'),
};

export const novelApi = {
  list: () => get<Novel[]>('/novels'),
  create: (req: NovelSaveReq) => post<Novel>('/novels', req),
  detail: (id: number) => get<NovelDetail>(`/novels/${id}`),
  update: (id: number, req: NovelSaveReq) => put<NovelDetail>(`/novels/${id}`, req),
  remove: (id: number) => del(`/novels/${id}`),
};

export const chapterApi = {
  list: (novelId: number) => get<ChapterMeta[]>(`/novels/${novelId}/chapters`),
  create: (novelId: number, req: { title?: string; content?: string }) =>
    post<Chapter>(`/novels/${novelId}/chapters`, req),
  get: (id: number) => get<Chapter>(`/chapters/${id}`),
  save: (id: number, req: { title?: string; content?: string }) => put<Chapter>(`/chapters/${id}`, req),
  remove: (id: number) => del(`/chapters/${id}`),
};

export const characterApi = {
  list: (novelId: number) => get<CharacterVO[]>(`/novels/${novelId}/characters`),
  create: (novelId: number, req: { name: string; role?: string; description?: string }) =>
    post<CharacterVO>(`/novels/${novelId}/characters`, req),
  update: (id: number, req: { name: string; role?: string; description?: string }) =>
    put<CharacterVO>(`/characters/${id}`, req),
  remove: (id: number) => del(`/characters/${id}`),
};

export const worldApi = {
  list: (novelId: number) => get<WorldVO[]>(`/novels/${novelId}/worldsettings`),
  create: (novelId: number, req: { title: string; content?: string }) =>
    post<WorldVO>(`/novels/${novelId}/worldsettings`, req),
  update: (id: number, req: { title: string; content?: string }) =>
    put<WorldVO>(`/worldsettings/${id}`, req),
  remove: (id: number) => del(`/worldsettings/${id}`),
};

export const aiApi = {
  status: () => get<{ provider: string; model: string }>('/ai/status'),
  getConfig: () => get<AiConfigVO>('/ai/config'),
  saveConfig: (req: { provider: string; baseUrl?: string; apiKey?: string; model?: string }) =>
    put<AiConfigVO>('/ai/config', req),
  testConfig: () => get<{ ok: boolean; latencyMs: number; message: string }>('/ai/config/test'),
};

export interface AutoWriteReq {
  mode: string;
  chapters?: number;
  wordsPerChapter?: number;
  genOutline?: boolean;
  autoSummary?: boolean;
}

export const taskApi = {
  start: (novelId: number, req: AutoWriteReq) => post<WritingTaskVO>(`/novels/${novelId}/auto`, req),
  list: (novelId: number) => get<WritingTaskVO[]>(`/novels/${novelId}/auto/tasks`),
  get: (taskId: number) => get<WritingTaskVO>(`/tasks/${taskId}`),
  approve: (taskId: number) => post<WritingTaskVO>(`/tasks/${taskId}/approve`),
  cancel: (taskId: number) => post<WritingTaskVO>(`/tasks/${taskId}/cancel`),
};
