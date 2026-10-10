import http from './request';

export interface NoteComment {
  id: string;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface CommunityNote {
  id: string;
  authorId: string;
  authorName: string;
  spuId?: string | null;
  spuName?: string;
  title: string;
  content: string;
  images: string[];
  likeCount: number;
  favoriteCount: number;
  commentCount: number;
  fakeFlag: number;
  liked: boolean;
  favorited: boolean;
  createdAt: string;
  comments?: NoteComment[];
}

export interface NotePage {
  records: CommunityNote[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** 信息流分页 */
export async function getNotes(pageNum = 1, pageSize = 10): Promise<NotePage> {
  const res = await http.get<{ code: number; data: NotePage }>('/community/note/page', {
    params: { pageNum, pageSize },
  });
  return res.data.data ?? { records: [], total: 0, pageNum, pageSize };
}

/** 笔记详情（含评论） */
export async function getNoteDetail(id: string): Promise<CommunityNote | null> {
  const res = await http.get<{ code: number; data: CommunityNote | null }>(`/community/note/${id}`);
  return res.data.data;
}

/** 发布笔记 */
export async function publishNote(body: {
  title: string;
  content: string;
  spuId?: string | null;
  images?: string[];
  authorName?: string;
}): Promise<CommunityNote | null> {
  const res = await http.post<{ code: number; data: CommunityNote | null }>('/community/note', body);
  return res.data.data;
}

/** 点赞/取消 */
export async function toggleLike(id: string): Promise<{ liked: boolean; likeCount: number } | null> {
  const res = await http.post<{ code: number; data: { liked: boolean; likeCount: number } | null }>(
    `/community/note/${id}/like`,
    {},
  );
  return res.data.data;
}

/** 收藏/取消 */
export async function toggleFavorite(id: string): Promise<{ favorited: boolean; favoriteCount: number } | null> {
  const res = await http.post<{ code: number; data: { favorited: boolean; favoriteCount: number } | null }>(
    `/community/note/${id}/favorite`,
    {},
  );
  return res.data.data;
}

/** 发表评论 */
export async function postComment(noteId: string, content: string): Promise<any | null> {
  const res = await http.post<{ code: number; data: any | null }>('/community/comment', { noteId, content });
  return res.data.data;
}

/** 我的笔记 */
export async function getMyNotes(): Promise<CommunityNote[]> {
  const res = await http.get<{ code: number; data: CommunityNote[] }>('/community/mine');
  return res.data.data ?? [];
}

/** AI 种草文案生成 */
export async function aiWriter(spuId: string, style = '日常'): Promise<{
  spuName: string;
  priceFen: number;
  avgRating: number;
  reviewTotal: number;
  style: string;
  draft: string;
} | null> {
  try {
    const res = await http.post<{ code: number; data: any }>('/community/ai-writer', { spuId, style }, { timeout: 45000 });
    return res.data.data;
  } catch (e) {
    return null;
  }
}
