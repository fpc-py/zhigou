import { Module } from '@nestjs/common';
import * as http from 'http';
import * as https from 'https';
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
import { CartController } from './cart/cart.controller.js';
import { CartService } from './cart/cart.service.js';
import { OrderController } from './order/order.controller.js';
import { OrderService } from './order/order.service.js';
import { PaymentController } from './payment/payment.controller.js';
import { PaymentService } from './payment/payment.service.js';
import { MarketingController } from './marketing/marketing.controller.js';
import { MarketingService } from './marketing/marketing.service.js';
import { UserController } from './user/user.controller.js';
import { UserService } from './user/user.service.js';
import { LogisticsController } from './logistics/logistics.controller.js';
import { LogisticsService } from './logistics/logistics.service.js';
import { AftersaleController } from './aftersale/aftersale.controller.js';
import { AftersaleService } from './aftersale/aftersale.service.js';
import { PriceCompareController } from './price-compare/price-compare.controller.js';
import { PriceCompareService } from './price-compare/price-compare.service.js';
import { CommunityController } from './community/community.controller.js';
import { CommunityService } from './community/community.service.js';
import { LifeController } from './life/life.controller.js';
import { LifeService } from './life/life.service.js';
import { ClosetController } from './closet/closet.controller.js';
import { ClosetService } from './closet/closet.service.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    HttpModule.register({
      timeout: 5000,
      maxRedirects: 0,
      // P0-D4 压测瓶颈优化：下游连接复用（keep-alive），避免每次透传新建 TCP 连接
      httpAgent: new http.Agent({ keepAlive: true, maxSockets: 50, maxFreeSockets: 10 }),
      httpsAgent: new https.Agent({ keepAlive: true, maxSockets: 50, maxFreeSockets: 10 }),
    }),
  ],
  controllers: [
    HomeController,
    ProductController,
    ChatController,
    AuthController,
    CartController,
    OrderController,
    PaymentController,
    MarketingController,
    UserController,
    LogisticsController,
    AftersaleController,
    PriceCompareController,
    CommunityController,
    LifeController,
    ClosetController,
  ],
  providers: [
    HomeService,
    ProductService,
    ChatService,
    AuthService,
    CartService,
    OrderService,
    PaymentService,
    MarketingService,
    UserService,
    LogisticsService,
    AftersaleService,
    PriceCompareService,
    CommunityService,
    LifeService,
    ClosetService,
    { provide: APP_FILTER, useClass: GlobalExceptionFilter },
    { provide: APP_INTERCEPTOR, useClass: TimeoutInterceptor },
    { provide: APP_INTERCEPTOR, useClass: AnalyticsInterceptor },
  ],
})
export class AppModule {}