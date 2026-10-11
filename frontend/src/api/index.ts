import { get, post, put, del } from './http';
import type {
  AiConfigVO,
  AuthResp,
  Chapter,
  ChapterMeta,
  ChapterOutlineItem,
  CharacterVO,
  Novel,
  NovelDetail,
  OutlineTaskVO,
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
  coverUrl?: string | null;
  /** 封面绘图提示词（可编辑，保存到 novel.coverPrompt） */
  coverPrompt?: string | null;
  /** 作者名（封面署名） */
  authorName?: string | null;
  outline?: string;
  languageStyle?: string | null;
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
  removeBatch: (ids: number[]) => post<void>(`/chapters/batch-delete`, { ids }),
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

export const uploadApi = {
  /** 上传图片（封面等），返回静态资源路径 */
  upload: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return post<{ url: string }>('/upload', fd);
  },
};

export const outlineApi = {
  /** 章节大纲列表：每个章节一行，含是否已生成 */
  list: (novelId: number) => get<ChapterOutlineItem[]>(`/novels/${novelId}/outlines`),
  /** 一键 / 批量生成大纲（同步，仅适合少量章节）；批量请用 outlineTaskApi 异步执行。不会创建章节 */
  generate: (novelId: number, chapterIds?: number[]) =>
    post<ChapterOutlineItem[]>(
      `/novels/${novelId}/outlines/generate`,
      chapterIds ? { chapterIds } : {}
    ),
  /** 手动新增 / 编辑单章大纲 */
  save: (novelId: number, chapterId: number, outline: string) =>
    put<void>(`/novels/${novelId}/outlines/${chapterId}`, { outline }),
  /** 删除单章大纲 */
  remove: (novelId: number, chapterId: number) =>
    del(`/novels/${novelId}/outlines/${chapterId}`),
};

/** 大纲批量生成任务：后台逐章执行，前端轮询进度，避免长任务触发 HTTP 超时 */
export const outlineTaskApi = {
  start: (novelId: number, req: { chapterIds?: number[] }) =>
    post<OutlineTaskVO>(`/novels/${novelId}/outline-tasks`, req),
  list: (novelId: number) => get<OutlineTaskVO[]>(`/novels/${novelId}/outline-tasks`),
  get: (taskId: number) => get<OutlineTaskVO>(`/outline-tasks/${taskId}`),
  cancel: (taskId: number) => post<OutlineTaskVO>(`/outline-tasks/${taskId}/cancel`, {}),
  remove: (taskId: number) => del(`/outline-tasks/${taskId}`),
};

export interface AutoWriteReq {
  mode: string;
  chapters?: number;
  genOutline?: boolean;
  autoOutline?: boolean;
  /** 从第 N 章续跑（失败任务断点续写用） */
  startChapter?: number;
}

export const taskApi = {
  start: (novelId: number, req: AutoWriteReq) => post<WritingTaskVO>(`/novels/${novelId}/auto`, req),
  list: (novelId: number) => get<WritingTaskVO[]>(`/novels/${novelId}/auto/tasks`),
  get: (taskId: number) => get<WritingTaskVO>(`/tasks/${taskId}`),
  approve: (taskId: number) => post<WritingTaskVO>(`/tasks/${taskId}/approve`),
  cancel: (taskId: number) => post<WritingTaskVO>(`/tasks/${taskId}/cancel`),
  remove: (taskId: number) => del(`/tasks/${taskId}`),
};
