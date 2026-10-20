/**
 * File Controller — 文件（BFF 透传，A7）
 */
import {
  Controller, Get, Post, Param, Query, Req, Res, UseGuards, UseInterceptors,
  UploadedFile, HttpException, HttpStatus,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { FileService } from './file.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request, Response } from 'express';

@Controller('file')
@UseGuards(JwtAuthGuard)
export class FileController {
  constructor(private readonly fileService: FileService) {}

  @Post('upload')
  @UseInterceptors(FileInterceptor('file'))
  async upload(
    @UploadedFile() file: { buffer: Buffer; mimetype: string; originalname: string } | undefined,
    @Req() req: Request,
    @Query('compress') compress?: string,
    @Query('convertTo') convertTo?: string,
    @Query('bizType') bizType?: string,
    @Query('maxWidth') maxWidth?: string,
    @Query('maxHeight') maxHeight?: string,
  ) {
    if (!file) {
      throw new HttpException('缺少文件参数 file', HttpStatus.BAD_REQUEST);
    }
    const userId = (req as any).userId as string;
    const data = await this.fileService.upload(userId, file, {
      compress: compress === 'true',
      convertTo,
      bizType,
      maxWidth: maxWidth ? Number(maxWidth) : undefined,
      maxHeight: maxHeight ? Number(maxHeight) : undefined,
    });
    return ApiResponse.ok(data);
  }

  @Get(':fileId/url')
  async getUrl(@Param('fileId') fileId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.fileService.getUrl(userId, fileId);
    return ApiResponse.ok(data);
  }

  /** 私有读代理：JWT 鉴权（BFF）→ file-service 归属校验（403）→ 文件字节流 */
  @Get(':fileId/content')
  async content(@Param('fileId') fileId: string, @Req() req: Request, @Res() res: Response) {
    const userId = (req as any).userId as string;
    const data = await this.fileService.content(userId, fileId);
    res.setHeader('Content-Type', data.contentType);
    res.setHeader('Content-Length', String(data.buffer.length));
    res.setHeader('Content-Disposition', `inline; filename="${encodeURIComponent(data.fileName)}"`);
    res.send(data.buffer);
  }
}
