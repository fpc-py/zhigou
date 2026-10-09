import http from './request';

export interface UploadResponse {
  fileId: string;
  url: string;
  fileName?: string;
}

/** 上传图片（file-service），返回可访问 URL（供 AI 图片搜款） */
export async function uploadImage(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append('file', file);
  const res = await http.post<{ code: number; data: UploadResponse }>('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data.data;
}
