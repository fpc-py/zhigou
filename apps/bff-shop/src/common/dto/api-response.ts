/**
 * 智购 BFF — 统一 API 响应格式
 * 对齐后端 Result<T> 规范：{ code, message, data }
 */
export class ApiResponse<T = unknown> {
  constructor(
    public readonly code: number,
    public readonly message: string,
    public readonly data: T | null,
  ) {}

  static ok<T>(data: T): ApiResponse<T> {
    return new ApiResponse(200, 'success', data);
  }

  static fail(code: number, message: string): ApiResponse<null> {
    return new ApiResponse(code, message, null);
  }

  static error(message: string): ApiResponse<null> {
    return new ApiResponse(500, message, null);
  }
}