/**
 * Community Service — 内容社区（种草笔记/点赞收藏/评论）透传
 * 说明：community-service 全量 JWT 鉴权，BFF 出站统一注入 X-User-Id 头；
 * 后端业务失败约定 HTTP 200 + body {code!=200, message} → BFF 转 HttpException(400, message)。
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';

function unwrapOrThrow<T>(resp: { data: { code?: number; message?: string; data?: T } }, logger: Logger, label: string, fallback: T): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    throw new HttpException(body.message || `${label} 业务处理失败`, HttpStatus.BAD_REQUEST);
  }
  return (body.data as T) ?? fallback;
}

@Injectable()
export class CommunityService {
  private readonly logger = new Logger(CommunityService.name);
  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 发布笔记 */
  async publish(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityNotePublish}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/note 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/note', null);
  }

  /** 信息流分页 */
  async pageNotes(userId: string, pageNum: number, pageSize: number): Promise<any> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityNotePage}?pageNum=${pageNum}&pageSize=${pageSize}&userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/note/page 失败: ${err.message}`);
          return Promise.resolve({ data: { data: { records: [], total: 0 } } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/note/page', { records: [], total: 0 });
  }

  /** 笔记详情 */
  async detail(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityNoteDetail(id)}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/note/${id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `community/note/${id}`, null);
  }

  /** 点赞/取消 */
  async toggleLike(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityNoteLike(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/note/${id}/like 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `community/note/${id}/like`, null);
  }

  /** 收藏/取消 */
  async toggleFavorite(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityNoteFavorite(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/note/${id}/favorite 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `community/note/${id}/favorite`, null);
  }

  /** 发表评论 */
  async comment(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityComment}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/comment 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/comment', null);
  }

    /** AI 种草文案生成（透传 ai-orchestrator） */
  async aiWriter(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.communityAiWriter}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: this.authHeader(userId), timeout: 45_000 }).pipe(
        timeout(45_000),
        catchError((err) => {
          this.logger.warn(`ai-orchestrator /api/v1/community/writer 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    // AI 服务返回裸 JSON（{spuId, spuName, priceFen, avgRating, reviewTotal, style, draft}），无统一 code/data 包装，直接透传
    return (resp as any)?.data ?? null;
  }

/** 我的笔记 */
  async mine(userId: string): Promise<any[]> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityMine}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/mine', []);
  }

  /** 短视频信息流 */
  async videoPage(userId: string): Promise<any[] | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityVideoPage}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/video/page 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/video/page', []);
  }

  /** 短视频详情 */
  async videoDetail(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityVideoDetail(id)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/video/{id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/video/detail', null);
  }

  /** 发布短视频 */
  async videoPublish(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityVideoPublish}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, authorId: userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/video 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/video/publish', null);
  }

  /** 视频点赞/取消 */
  async videoLike(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityVideoLike(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/video/{id}/like 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/video/like', null);
  }

  /** 视频收藏/取消 */
  async videoFavorite(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityVideoFavorite(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/video/{id}/favorite 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/video/favorite', null);
  }

  /** 直播列表 */
  async liveList(userId: string): Promise<any[] | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityLiveList}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/live/list 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/live/list', []);
  }

  /** 直播详情 */
  async liveDetail(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.communitySvc.url}${SERVICE_PATHS.communityLiveDetail(id)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.communitySvc.timeout),
        catchError((err) => {
          this.logger.warn(`community-service /community/live/{id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'community/live/detail', null);
  }
}

