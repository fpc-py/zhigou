/**
 * Reminders Service — AI 主动提醒中心透传
 * 后端：ai-orchestrator 8095 /api/v1/reminders/*（块2）
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
export class RemindersService {
  private readonly logger = new Logger(RemindersService.name);
  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 我的提醒列表 */
  async mine(userId: string, unreadOnly: boolean): Promise<any[]> {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.remindersMine}?unreadOnly=${unreadOnly}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(5_000),
        catchError((err) => {
          this.logger.warn(`ai-orchestrator /reminders/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'reminders/mine', []);
  }

  /** 手动触发一次扫描 */
  async scan(userId: string): Promise<any | null> {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.remindersScan}`;
    const resp = await firstValueFrom(
      this.http.post(url, {}, { headers: this.authHeader(userId) }).pipe(
        timeout(10_000),
        catchError((err) => {
          this.logger.warn(`ai-orchestrator /reminders/scan 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'reminders/scan', null);
  }

  /** 标记已读 */
  async markRead(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.remindersRead(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, {}, { headers: this.authHeader(userId) }).pipe(
        timeout(5_000),
        catchError((err) => {
          this.logger.warn(`ai-orchestrator /reminders/read/${id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `reminders/read/${id}`, null);
  }
}
