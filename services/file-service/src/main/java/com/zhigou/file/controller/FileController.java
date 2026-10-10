package com.zhigou.file.controller;

import com.zhigou.common.Result;
import com.zhigou.file.dto.UploadResponse;
import com.zhigou.file.interceptor.UserContext;
import com.zhigou.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "文件")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件（支持压缩/格式转换/尺寸限制）")
    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "compress", defaultValue = "false") boolean compress,
            @RequestParam(value = "convertTo", required = false) String convertTo,
            @RequestParam(value = "bizType", defaultValue = "other") String bizType,
            @RequestParam(value = "maxWidth", required = false) Integer maxWidth,
            @RequestParam(value = "maxHeight", required = false) Integer maxHeight) {
        Long userId = UserContext.requireUserId();
        UploadResponse resp = fileService.upload(userId, file, compress, convertTo, bizType, maxWidth, maxHeight);
        return Result.ok(resp);
    }

    @Operation(summary = "获取签名URL")
    @GetMapping("/{fileId}/url")
    public Result<Map<String, String>> getUrl(@PathVariable("fileId") String fileId) {
        Long userId = UserContext.requireUserId();
        String url = fileService.getSignedUrl(userId, fileId);
        return Result.ok(Map.of("url", url));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable("fileId") String fileId) {
        Long userId = UserContext.requireUserId();
        fileService.delete(userId, fileId);
        return Result.ok();
    }
}