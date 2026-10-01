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

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.requireUserId();
        UploadResponse resp = fileService.upload(userId, file);
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