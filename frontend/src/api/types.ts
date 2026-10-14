export interface UserVO {
  id: number;
  username: string;
  nickname: string;
}

export interface AuthResp {
  token: string;
  user: UserVO;
}

export interface Novel {
  id: number;
  title: string;
  description: string;
  genre: string;
  status: string;
  targetWords: number | null;
  wordCount: number;
  coverColor: string;
  coverUrl: string | null;
  languageStyle: string | null;
  chapterCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface NovelDetail extends Novel {
  outline: string;
  /** AI 生成的封面绘图提示词（可编辑） */
  coverPrompt: string | null;
  /** 作者名（封面署名；各发布平台笔名可能不同） */
  authorName: string | null;
}

export interface ChapterMeta {
  id: number;
  novelId: number;
  title: string;
  chapterOrder: number;
  wordCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Chapter extends ChapterMeta {
  content: string;
}

export interface CharacterVO {
  id: number;
  novelId: number;
  name: string;
  role: string;
  description: string | null;
  createdAt: string;
}

export interface WorldVO {
  id: number;
  novelId: number;
  title: string;
  content: string | null;
  createdAt: string;
}

export interface ChatMsg {
  role: 'user' | 'assistant';
  content: string;
}

/** 章节大纲 = 一条创作前的规划（不等同于章节；章节是规划的产物） */
export interface ChapterOutlineItem {
  outlineId: number;
  chapterOrder: number;
  /** 规划章节名，可能未填写 */
  chapterTitle: string | null;
  /** 已生成正文的章节 id；尚未生成正文时为 null */
  chapterId: number | null;
  /** 本条生效的目标字数（单章设定优先，否则全书均摊） */
  wordCount: number | null;
  /** 是否为本条单独设定（false 表示沿用全书均摊，可一键恢复默认） */
  wordCountCustomized: boolean;
  /** 该章是否已写正文（用于覆盖生成前的提示） */
  hasContent: boolean;
  hasOutline: boolean;
  outline: string | null;
  updatedAt: string | null;
}

/** 大纲批量生成任务（后台逐章执行，前端轮询进度） */
export interface OutlineTaskVO {
  id: number;
  novelId: number;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';
  total: number;
  currentIndex: number;
  currentTitle: string;
  succeeded: number;
  failed: number;
  error: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChapterVersion {
  id: number;
  versionNo: number;
  title: string;
  wordCount: number;
  /** 快照来源：save（手动保存）/ ai（AI 写入）/ restore（回滚前留档） */
  reason: string | null;
  createdAt: string;
  /** 快照正文，用于回滚前预览 */
  content: string | null;
}

export interface AiConfigVO {
  provider: string;
  baseUrl: string;
  model: string;
  apiKeyMask: string;
  hasKey: boolean;
  updatedAt: string | null;
}

export interface WritingTaskVO {
  id: number;
  novelId: number;
  mode: string;
  status: string;
  totalChapters: number;
  currentChapter: number;
  currentTitle: string;
  chapters: number;
  wordsPerChapter: number;
  genOutline: boolean;
  autoOutline: boolean;
  error: string | null;
  createdAt: string;
  updatedAt: string;
}
