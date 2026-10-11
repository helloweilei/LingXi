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

export interface ChapterOutlineItem {
  chapterId: number;
  chapterOrder: number;
  chapterTitle: string;
  /** 每章计划字数 = 总目标字数 / 章节数（后端动态计算） */
  wordCount: number | null;
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
