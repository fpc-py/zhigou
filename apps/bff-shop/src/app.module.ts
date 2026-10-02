import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { APP_FILTER, APP_GUARD, APP_INTERCEPTOR } from '@nestjs/core';
import { GlobalExceptionFilter } from './common/filters/http-exception.filter.js';
import { TimeoutInterceptor } from './common/interceptors/timeout.interceptor.js';
import { AnalyticsInterceptor } from './common/interceptors/analytics.interceptor.js';
import { HomeController } from './home/home.controller.js';
import { HomeService } from './home/home.service.js';
import { ProductController } from './product/product.controller.js';
import { ProductService } from './product/product.service.js';
import { ChatController } from './chat/chat.controller.js';
import { ChatService } from './chat/chat.service.js';
import { AuthController } from './auth/auth.controller.js';
import { AuthService } from './auth/auth.service.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    HttpModule.register({
      timeout: 5000,
      maxRedirects: 0,
    }),
  ],
  controllers: [
    HomeController,
    ProductController,
    ChatController,
    AuthController,
  ],
  providers: [
    HomeService,
    ProductService,
    ChatService,
    AuthService,
    { provide: APP_FILTER, useClass: GlobalExceptionFilter },
    { provide: APP_INTERCEPTOR, useClass: TimeoutInterceptor },
    { provide: APP_INTERCEPTOR, useClass: AnalyticsInterceptor },
  ],
})
export class AppModule {}