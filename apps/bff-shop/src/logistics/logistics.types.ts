/** /freight 响应体类型（透传 logistics-service） */

export interface FreightCalculateBody {
  totalAmount: number;
  weightG?: number;
}

export interface FreightCalculateResult {
  freightFee: number;
  freeShipping: boolean;
  reason: string;
}
