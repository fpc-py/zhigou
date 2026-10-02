/**
 * JWT 鉴权守卫
 *
 * - 从 Authorization: Bearer xxx 提取 token
 * - 用 HS256 验签
 * - 解出 sub → request.userId
 * - 透传 x-user-id header 给下游服务
 * - 白名单路由不校验
 */
import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
  Logger,
} from '@nestjs/common';
import { Request } from 'express';
import jwt from 'jsonwebtoken';

// 不校验 JWT 的路由白名单
const WHITE_LIST = [
  '/auth/login',
  '/auth/send-sms-code',
  '/auth/refresh',
  '/health',
];

@Injectable()
export class JwtAuthGuard implements CanActivate {
  private readonly logger = new Logger(JwtAuthGuard.name);

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const path = request.path;

    // 白名单放行
    if (WHITE_LIST.some((p) => path.startsWith(p))) {
      return true;
    }

    const authHeader = request.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw new UnauthorizedException('缺少 Authorization header');
    }

    const token = authHeader.slice(7);
    const secret = process.env.JWT_SECRET ?? 'change-me-to-a-random-256-bit-string';

    try {
      const payload = jwt.verify(token, secret, { algorithms: ['HS256'] }) as jwt.JwtPayload;
      const userId = payload.sub;
      if (!userId) {
        throw new UnauthorizedException('Token 中缺少 sub');
      }

      // 挂 userId 到请求对象
      (request as any).userId = userId;

      // 透传给下游
      request.headers['x-user-id'] = String(userId);

      return true;
    } catch (err: any) {
      this.logger.warn(`JWT 验证失败: ${err.message}`);
      throw new UnauthorizedException('Token 无效或已过期');
    }
  }
}