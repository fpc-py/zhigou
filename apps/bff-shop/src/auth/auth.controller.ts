/**
 * Auth Controller — 不校验 JWT 的鉴权端点
 */
import { Controller, Post, Body } from '@nestjs/common';
import { AuthService } from './auth.service.js';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  /** 发送短信验证码 */
  @Post('send-sms-code')
  async sendSmsCode(@Body('phone') phone: string) {
    return this.authService.sendSmsCode(phone);
  }

  /** 手机号+验证码登录 */
  @Post('login')
  async login(@Body('phone') phone: string, @Body('code') code: string) {
    return this.authService.login(phone, code);
  }

  /** 刷新 token */
  @Post('refresh')
  async refresh(@Body('refreshToken') refreshToken: string) {
    return this.authService.refresh(refreshToken);
  }
}