/**
 * RolesGuard — 商家后台 RBAC 角色守卫
 *
 * - 用法：在方法（或控制器）上加 @Roles('MERCHANT')，该端点只允许对应角色访问
 * - 无 @Roles 元数据的端点放行（默认登录即可，用户端不受影响）
 * - 依赖 JwtAuthGuard 先执行，把 JWT payload.role 挂到 request.role
 * - 校验失败抛 403 ForbiddenException（区别于 401 未登录）
 */
import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';

export const ROLES_KEY = 'roles';

/** 声明端点所需角色：@Roles('MERCHANT') */
export const Roles = (...roles: string[]) => {
  // eslint-disable-next-line @typescript-eslint/ban-types
  return (target: object, key?: string | symbol, descriptor?: PropertyDescriptor) => {
    if (descriptor) {
      // 方法装饰器：给 handler 挂角色元数据
      Reflect.defineMetadata(ROLES_KEY, roles, descriptor.value);
    } else {
      // 类装饰器：给类挂角色元数据（类装饰器不允许返回值）
      Reflect.defineMetadata(ROLES_KEY, roles, target);
    }
  };
};

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const roles = this.reflector.getAllAndOverride<string[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    // 没有角色要求的端点（用户端接口）直接放行
    if (!roles || roles.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest<Request>();
    const role = (request as any).role as string | undefined;

    if (!role || !roles.includes(role)) {
      throw new ForbiddenException('该账号无商家权限（角色不符）');
    }
    return true;
  }
}
