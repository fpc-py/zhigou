# 支付集成
智购支付服务集成。

沙箱: POST /payment/create 返回假URL, POST /payment/sandbox/mock-pay 模拟回调, payment_no唯一索引幂等
生产: 替换为微信/支付宝SDK, 保留接口签名不变
安全: 金额服务端计算, 密钥走环境变量