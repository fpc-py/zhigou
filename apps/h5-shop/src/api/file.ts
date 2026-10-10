import http from './request';

export interface UploadResponse {
  fileId: string;
  url: string;
  size: number;
  mimeType: string;
  originalName?: string;
  /** 处理后尺寸（非图片/原样直存为 null） */
  width?: number | null;
  height?: number | null;
  /** 是否发生了压缩/缩放/格式转换 */
  processed?: boolean;
}

export interface UploadOptions {
  /** 是否压缩（JPEG 质量压缩；PNG 无损不重编码） */
  compress?: boolean;
  /** 目标格式 jpeg/png */
  convertTo?: 'jpeg' | 'png';
  /** 业务分类：product / aftersale / chat / other */
  bizType?: 'product' | 'aftersale' | 'chat' | 'other';
  /** 目标最大宽/高（只缩小不放大） */
  maxWidth?: number;
  maxHeight?: number;
}

/** 上传图片（file-service，支持压缩/格式转换），返回可访问 URL */
export async function uploadImage(file: File, options: UploadOptions = {}): Promise<UploadResponse> {
  const form = new FormData();
  form.append('file', file);

  const params = new URLSearchParams();
  if (options.compress) params.set('compress', 'true');
  if (options.convertTo) params.set('convertTo', options.convertTo);
  if (options.bizType) params.set('bizType', options.bizType);
  if (options.maxWidth) params.set('maxWidth', String(options.maxWidth));
  if (options.maxHeight) params.set('maxHeight', String(options.maxHeight));
  const qs = params.toString();

  const res = await http.post<{ code: number; data: UploadResponse }>(
    qs ? `/file/upload?${qs}` : '/file/upload',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return res.data.data;
}
