import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module.js';
import type { INestApplication } from '@nestjs/common';

function setupHealthRoute(app: INestApplication) {
  const expressApp = app.getHttpAdapter().getInstance();
  expressApp.get('/health', (_req: any, res: any) => {
    res.json({ status: 'UP', service: 'bff-shop' });
  });
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // CORS — 前端开发时允许跨域
  app.enableCors({
    origin: ['http://localhost:5173', 'http://localhost:3000'],
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'x-user-id'],
  });

  // 健康检查（不经过 JWT）
  setupHealthRoute(app);

  await app.listen(process.env.PORT ?? 3000);
  console.log(`BFF 已启动: http://localhost:${process.env.PORT ?? 3000}`);
}
await bootstrap();