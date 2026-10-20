/**
 * File Service — 文件透传（A7：上传 + 签名URL + 私有读代理）
 * 私有读：BFF 带 X-User-Id → file-service JWT/X-User-Id 鉴权 + 归属校验（403）
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES } from '../config/service.config.js';

export interface FileUploadResult {
  fileId: string;
  url: string;
  size: number;
  mimeType: string;
  originalName?: string;
  width?: number | null;
  height?: number | null;
  processed?: boolean;
  originalSize?: number;
  originalWidth?: number;
  originalHeight?: number;
}

export interface FileContent {
  buffer: Buffer;
  contentType: string;
  fileName: string;
}

@Injectable()
export class FileService {
  private readonly logger = new Logger(FileService.name);

  constructor(private readonly http: HttpService) {}

  async upload(
    userId: string,
    file: { buffer: Buffer; mimetype: string; originalname: string },
    opts: { compress?: boolean; convertTo?: string; bizType?: string; maxWidth?: number; maxHeight?: number },
  ): Promise<FileUploadResult | null> {
    const url = `${SERVICES.fileService.url}/file/upload`;
    const params = new URLSearchParams();
    if (opts.compress) params.set('compress', 'true');
    if (opts.convertTo) params.set('convertTo', opts.convertTo);
    if (opts.bizType) params.set('bizType', opts.bizType);
    if (opts.maxWidth) params.set('maxWidth', String(opts.maxWidth));
    if (opts.maxHeight) params.set('maxHeight', String(opts.maxHeight));
    const qs = params.toString();

    const form = new FormData();
    form.append('file', new Blob([new Uint8Array(file.buffer)], { type: file.mimetype }), file.originalname);

    const resp = await firstValueFrom(
      this.http.post(qs ? `${url}?${qs}` : url, form, {
        headers: { 'x-user-id': userId },
      }).pipe(
        timeout(SERVICES.fileService.timeout),
        catchError((err) => {
          this.logger.warn(`file-service /file/upload 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    const body = resp.data as { code?: number; message?: string; data?: FileUploadResult };
    if (body.code !== undefined && body.code !== 200) {
      this.logger.warn(`file-service 上传业务失败: code=${body.code} message=${body.message}`);
      throw new HttpException(body.message || '文件上传失败', HttpStatus.BAD_REQUEST);
    }
    return body.data ?? null;
  }

  async getUrl(userId: string, fileId: string): Promise<{ url: string } | null> {
    const url = `${SERVICES.fileService.url}/file/${fileId}/url`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.fileService.timeout),
        catchError((err) => {
          this.logger.warn(`file-service /file/${fileId}/url 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    const body = resp.data as { code?: number; message?: string; data?: { url: string } };
    if (body.code !== undefined && body.code !== 200) {
      this.logger.warn(`file-service 签名URL业务失败: code=${body.code} message=${body.message}`);
      throw new HttpException(body.message || '获取下载链接失败', HttpStatus.BAD_REQUEST);
    }
    return body.data ?? null;
  }

  /** 私有读代理：file-service 流式返回文件字节（鉴权 + 归属校验在服务端） */
  async content(userId: string, fileId: string): Promise<FileContent> {
    const url = `${SERVICES.fileService.url}/file/${fileId}/content`;
    const resp = await firstValueFrom(
      this.http.get(url, {
        headers: { 'x-user-id': userId },
        responseType: 'arraybuffer',
        maxRedirects: 0,
      }).pipe(
        timeout(SERVICES.fileService.timeout),
        catchError((err) => {
          this.logger.warn(`file-service /file/${fileId}/content 失败: ${err.message}`);
          throw new HttpException('文件读取失败', HttpStatus.BAD_GATEWAY);
        }),
      ),
    );
    const contentType = String(resp.headers?.['content-type'] || 'application/octet-stream');
    const disposition = String(resp.headers?.['content-disposition'] || '');
    const fileName = decodeFileName(disposition) || fileId;
    return {
      buffer: Buffer.from(resp.data as ArrayBuffer),
      contentType,
      fileName,
    };
  }
}

function decodeFileName(disposition: string): string {
  const m = /filename="([^"]+)"/.exec(disposition);
  if (!m) return '';
  try {
    return decodeURIComponent(m[1]);
  } catch {
    return m[1];
  }
}
