# h5-shop · 移动端 H5

智购移动端 H5 商城，基于 **Vue 3 + Vite + TypeScript + Pinia**，端口 **5173**（dev 代理 `/api` → BFF :3000）。已对齐 [../../docs/智购AI超级商城-企业级可交互原型.html](../../docs/智购AI超级商城-企业级可交互原型.html) 的 8 屏交互。

## 功能范围

- **8 屏对齐原型**：首页（问候头部/快捷宫格/真实商品流）、AI 对话（气泡 + 工具理由 + 商品卡）、商品详情（AI 摘要/规格网格/比价入口）、AI 比价（最优方案 + 渠道演示表）、AR 试穿/智能衣橱/社区（视觉占位，标注"功能规划中"）、我的（真实资料 + 订单栏 + AI 模型卡）
- **交易闭环**：购物车（服务端数据/勾选/数量/删除/清空）、结算（地址/优惠券/运费/优惠试算/提交幂等/沙箱支付）、订单列表（状态筛选/取消/去支付）、订单详情、地址管理（CRUD + 弹层）、优惠券页
- **TabBar 5 tab**：首页 / AI / 衣橱 / 社区 / 我的（二级页 `meta.tabbar: false` 自动隐藏）

## 路由

| 路由 | 页面 | TabBar |
|---|---|---|
| `/` | 首页 | ✅ |
| `/chat` | AI 对话 | ✅ |
| `/closet` | 智能衣橱（占位） | ✅ |
| `/community` | 社区（占位） | ✅ |
| `/profile` | 我的 | ✅ |
| `/login` | 登录 | - |
| `/products` | 商品列表 | - |
| `/product/:spuId` | 商品详情 | - |
| `/compare` | AI 比价 | - |
| `/tryon` | AR 试穿（占位） | - |
| `/cart` | 购物车 | - |
| `/checkout` | 结算 | - |
| `/orders` | 订单列表 | - |
| `/order/:orderId` | 订单详情 | - |
| `/address` | 地址管理 | - |
| `/coupons` | 优惠券 | - |

## 目录要点

```
src/
├── api/               # 请求层（request.ts 统一封装 + 各业务模块）
├── components/        # 全局组件（Icon SVG 库 / Pill / TabBar / ProductCard）
├── router/            # 16 条路由
├── stores/            # Pinia（cart.ts 已废弃，购物车转服务端）
├── utils/             # showToast / formatPrice
└── views/             # 页面
```

## 启动与验证

```bash
npm install
npm run dev             # 开发（:5173）
npm run build           # 生产构建（type-check + build-only）
```

> 依赖 BFF（:3000）与后端服务已启动。接口对接清单见 [../../docs/前端改造说明-原型对齐与后端对接.md](../../docs/前端改造说明-原型对齐与后端对接.md)。
