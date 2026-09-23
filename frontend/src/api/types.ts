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
  chapterCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface NovelDetail extends Novel {
  outline: string;
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
