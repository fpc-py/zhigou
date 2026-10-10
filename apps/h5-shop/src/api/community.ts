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


// ===== 短视频（图文 MVP）=====

export interface CommunityVideo {
  id: string;
  authorId: string;
  authorName: string;
  spuId?: string | null;
  title: string;
  content?: string;
  videoUrl: string;
  coverUrl?: string;
  durationSec: number;
  likeCount: number;
  favoriteCount: number;
  commentCount: number;
  viewCount: number;
  liked?: boolean;
  favorited?: boolean;
  createdAt: string;
}

export interface LiveItem {
  id: string;
  authorId: string;
  authorName: string;
  spuId?: string | null;
  title: string;
  coverUrl?: string;
  status: number; // 0=预告 1=直播中 2=已结束
  viewCount: number;
  likeCount: number;
  scheduledStart?: string;
  startedAt?: string;
  endedAt?: string;
}

/** 短视频信息流 */
export async function getVideos(pageNum = 1, pageSize = 10): Promise<CommunityVideo[]> {
  const res = await http.get<{ code: number; data: CommunityVideo[] }>('/community/video/page', {
    params: { pageNum, pageSize },
  });
  return res.data.data ?? [];
}

/** 短视频详情 */
export async function getVideoDetail(id: string): Promise<CommunityVideo | null> {
  const res = await http.get<{ code: number; data: CommunityVideo | null }>(`/community/video/${id}`);
  return res.data.data;
}

/** 发布短视频（图文物料） */
export async function publishVideo(body: {
  title: string;
  videoUrl: string;
  content?: string;
  spuId?: string | null;
  authorName?: string;
}): Promise<CommunityVideo | null> {
  const res = await http.post<{ code: number; data: CommunityVideo | null }>('/community/video', body);
  return res.data.data;
}

/** 视频点赞/取消 */
export async function toggleVideoLike(id: string): Promise<{ liked: boolean; likeCount: number } | null> {
  const res = await http.post<{ code: number; data: { liked: boolean; likeCount: number } | null }>(
    `/community/video/${id}/like`,
    {},
  );
  return res.data.data;
}

/** 视频收藏/取消 */
export async function toggleVideoFavorite(id: string): Promise<{ favorited: boolean; favoriteCount: number } | null> {
  const res = await http.post<{ code: number; data: { favorited: boolean; favoriteCount: number } | null }>(
    `/community/video/${id}/favorite`,
    {},
  );
  return res.data.data;
}

/** 直播列表 */
export async function getLiveList(): Promise<LiveItem[]> {
  const res = await http.get<{ code: number; data: LiveItem[] }>('/community/live/list');
  return res.data.data ?? [];
}

/** 直播详情 */
export async function getLiveDetail(id: string): Promise<LiveItem | null> {
  const res = await http.get<{ code: number; data: LiveItem | null }>(`/community/live/${id}`);
  return res.data.data;
}
