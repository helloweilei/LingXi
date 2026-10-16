import http, { get, post, put, del } from './http';
import type {
  AiConfigVO,
  AuthResp,
  Chapter,
  ChapterMeta,
  ChapterOutlineItem,
  ChapterVersion,
  CharacterRelationVO,
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
  /** 版本快照列表（按版本号倒序，含正文供预览） */
  versions: (id: number) => get<ChapterVersion[]>(`/chapters/${id}/versions`),
  /** 回滚到指定快照（回滚前会自动为当前内容留档，可再回退） */
  restoreVersion: (id: number, versionId: number) =>
    post<Chapter>(`/chapters/${id}/versions/${versionId}/restore`, {}),
  /** 删除指定快照（不影响章节当前内容） */
  deleteVersion: (id: number, versionId: number) =>
    del<void>(`/chapters/${id}/versions/${versionId}`),
};

export const characterApi = {
  list: (novelId: number) => get<CharacterVO[]>(`/novels/${novelId}/characters`),
  create: (novelId: number, req: { name: string; role?: string; description?: string }) =>
    post<CharacterVO>(`/novels/${novelId}/characters`, req),
  update: (id: number, req: { name: string; role?: string; description?: string }) =>
    put<CharacterVO>(`/characters/${id}`, req),
  remove: (id: number) => del(`/characters/${id}`),
  relations: (novelId: number) => get<CharacterRelationVO[]>(`/novels/${novelId}/character-relations`),
  createRelation: (novelId: number, req: { sourceId: number; targetId: number; label: string }) =>
    post<CharacterRelationVO>(`/novels/${novelId}/character-relations`, req),
  updateRelation: (id: number, req: { sourceId: number; targetId: number; label: string }) =>
    put<CharacterRelationVO>(`/character-relations/${id}`, req),
  removeRelation: (id: number) => del(`/character-relations/${id}`),
  /** AI 推理人物关系并落库；replace 为 true 时覆盖已有关系。模型耗时较长，单独放宽超时 */
  inferRelations: (novelId: number, replace: boolean) =>
    http.post(`/novels/${novelId}/character-relations/infer`, null, {
      params: { replace },
      timeout: 200000,
    }) as unknown as Promise<CharacterRelationVO[]>,
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

/** 章节大纲：一行 = 一条创作前的规划（章节是该规划的产物，生成正文时才落地） */
export interface CreateOutlineReq {
  chapterTitle?: string | null;
  outline?: string | null;
  targetWords?: number | null;
}

export const outlineApi = {
  /** 大纲规划列表：一行一条规划 */
  list: (novelId: number) => get<ChapterOutlineItem[]>(`/novels/${novelId}/outlines`),
  /** 手动新增一行规划（只建大纲，不创建章节；提交后表格新增一行） */
  create: (novelId: number, req: CreateOutlineReq) =>
    post<ChapterOutlineItem[]>(`/novels/${novelId}/outlines`, req ?? {}),
  /** 同步批量生成大纲内容（仅适合少量）；批量请用 outlineTaskApi 异步执行 */
  generate: (novelId: number, outlineIds?: number[]) =>
    post<ChapterOutlineItem[]>(
      `/novels/${novelId}/outlines/generate`,
      outlineIds ? { outlineIds } : {}
    ),
  /**
   * 只改章节名（不动大纲内容与字数）。
   * 标题主场在章节大纲页；生成正文时会以这里的名字作为章节标题（单向同步）。
   */
  saveTitle: (novelId: number, outlineId: number, chapterTitle: string) =>
    put<void>(`/novels/${novelId}/outlines/${outlineId}`, { chapterTitle }),
  /** 编辑一条规划；字段传 null 表示不改该字段。targetWords 传 null 表示沿用全书均摊值 */
  save: (
    novelId: number,
    outlineId: number,
    outline: string,
    targetWords?: number | null,
    chapterTitle?: string | null
  ) => put<void>(`/novels/${novelId}/outlines/${outlineId}`, { outline, targetWords, chapterTitle }),
  /** 单独设置某条规划的目标字数；words 传 0 表示恢复默认（全书均摊） */
  saveWords: (novelId: number, outlineId: number, words: number | null) =>
    put<void>(`/novels/${novelId}/outlines/${outlineId}/words`, { words }),
  /** 删除一条规划 */
  remove: (novelId: number, outlineId: number) =>
    del(`/novels/${novelId}/outlines/${outlineId}`),
};

/** 大纲批量生成任务：后台逐条执行，前端轮询进度，避免长任务触发 HTTP 超时 */
export const outlineTaskApi = {
  /**
   * chapters：全书还没有规划行时，先按数量创建规划行再逐章生成（一键从零规划）；
   * 留空则只生成已有规划行中缺大纲内容的行。
   */
  start: (novelId: number, req: { outlineIds?: number[]; chapters?: number }) =>
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
