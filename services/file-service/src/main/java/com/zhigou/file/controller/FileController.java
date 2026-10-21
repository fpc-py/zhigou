package com.zhigou.file.controller;

import com.zhigou.common.Result;
import com.zhigou.file.dto.FileDownload;
import com.zhigou.file.dto.UploadResponse;
import com.zhigou.file.interceptor.UserContext;
import com.zhigou.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "私有读（鉴权 + 归属校验，服务端流式）")
    @GetMapping("/{date}/{userId}/{fileHash}/content")
    public ResponseEntity<byte[]> content(@PathVariable String date, @PathVariable Long userId,
                                          @PathVariable String fileHash) {
        String fileId = date + "/" + userId + "/" + fileHash;
        Long uid = UserContext.requireUserId();
        FileDownload d = fileService.download(uid, fileId);
        String fileName = java.net.URLEncoder.encode(d.fileName() == null ? fileId : d.fileName(),
                java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        d.contentType() == null ? "application/octet-stream" : d.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"")
                .contentLength(d.size() == null ? d.bytes().length : d.size())
                .body(d.bytes());
    }

    @Operation(summary = "获取签名URL")
    @GetMapping("/{date}/{userId}/{fileHash}/url")
    public Result<Map<String, String>> getUrl(@PathVariable String date, @PathVariable Long userId,
                                              @PathVariable String fileHash) {
        String fileId = date + "/" + userId + "/" + fileHash;
        Long uid = UserContext.requireUserId();
        String url = fileService.getSignedUrl(uid, fileId);
        return Result.ok(Map.of("url", url));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{date}/{userId}/{fileHash}")
    public Result<Void> delete(@PathVariable String date, @PathVariable Long userId,
                               @PathVariable String fileHash) {
        String fileId = date + "/" + userId + "/" + fileHash;
        Long uid = UserContext.requireUserId();
        fileService.delete(uid, fileId);
        return Result.ok();
    }
}