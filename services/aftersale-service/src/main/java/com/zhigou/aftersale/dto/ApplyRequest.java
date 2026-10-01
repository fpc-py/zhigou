package com.zhigou.aftersale.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data
public class ApplyRequest {
    @NotBlank private String orderNo;
    @NotBlank private String type;
    private String reason;
    @NotNull private Long amount;
    private List<String> images;
}