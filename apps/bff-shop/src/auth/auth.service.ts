/**
 * Auth Service — 透传 auth-center
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import { ApiResponse } from '../common/dto/api-response.js';

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(private readonly http: HttpService) {}

  async sendSmsCode(phone: string): Promise<ApiResponse> {
    const url = `${SERVICES.authCenter.url}${SERVICE_PATHS.authSendSms}`;
    try {
      const obs = this.http.post(url, { phone }).pipe(
        timeout(SERVICES.authCenter.timeout),
        catchError((err) => {
          this.logger.warn(`auth-center 发送验证码失败: ${err.message}`);
          return Promise.resolve({ data: ApiResponse.fail(500, '发送验证码失败') });
        }),
      );
      const resp = await firstValueFrom(obs);
      return resp.data;
    } catch {
      return ApiResponse.fail(500, '鉴权服务不可用');
    }
  }

  async login(phone: string, code: string): Promise<ApiResponse> {
    const url = `${SERVICES.authCenter.url}${SERVICE_PATHS.authLogin}`;
    try {
      const obs = this.http.post(url, { phone, code }).pipe(
        timeout(SERVICES.authCenter.timeout),
        catchError((err) => {
          this.logger.warn(`auth-center 登录失败: ${err.message}`);
          return Promise.resolve({ data: ApiResponse.fail(401, '登录失败') });
        }),
      );
      const resp = await firstValueFrom(obs);
      return resp.data;
    } catch {
      return ApiResponse.fail(500, '鉴权服务不可用');
    }
  }

  async refresh(refreshToken: string): Promise<ApiResponse> {
    const url = `${SERVICES.authCenter.url}${SERVICE_PATHS.authRefresh}`;
    try {
      const obs = this.http.post(url, { refreshToken }).pipe(
        timeout(SERVICES.authCenter.timeout),
        catchError((err) => {
          this.logger.warn(`auth-center 刷新失败: ${err.message}`);
          return Promise.resolve({ data: ApiResponse.fail(401, 'Token 刷新失败') });
        }),
      );
      const resp = await firstValueFrom(obs);
      return resp.data;
    } catch {
      return ApiResponse.fail(500, '鉴权服务不可用');
    }
  }
}