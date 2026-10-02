/**
 * 全局异常过滤器
 * 任何未捕获的异常 → { code: 500, message: '系统繁忙', data: null }
 * 不暴露栈信息、内部类名、SQL 语句
 */
import { ExceptionFilter, Catch, ArgumentsHost, HttpException, HttpStatus, Logger } from '@nestjs/common';
import { Response } from 'express';
import { ApiResponse } from '../dto/api-response.js';

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(GlobalExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest();

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let message = '系统繁忙';

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const res = exception.getResponse();
      message = typeof res === 'string' ? res : (res as any).message ?? message;
    }

    // 不记录 401/403 这类客户端错误
    if (status >= 500) {
      this.logger.error(
        `[${request.method}] ${request.url} → ${status}: ${exception instanceof Error ? exception.message : 'Unknown error'}`,
        exception instanceof Error ? exception.stack : undefined,
      );
    }

    response
      .status(status)
      .json(ApiResponse.fail(status, message));
  }
}