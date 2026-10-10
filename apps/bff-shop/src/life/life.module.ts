import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { LifeController } from './life.controller.js';
import { LifeService } from './life.service.js';

@Module({
  imports: [HttpModule],
  controllers: [LifeController],
  providers: [LifeService],
})
export class LifeModule {}
