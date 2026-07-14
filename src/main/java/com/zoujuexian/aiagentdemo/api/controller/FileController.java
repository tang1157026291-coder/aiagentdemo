package com.zoujuexian.aiagentdemo.api.controller;

import com.zoujuexian.aiagentdemo.api.controller.dto.ChatResponse;
import com.zoujuexian.aiagentdemo.service.ocr.OcrService;
import com.zoujuexian.aiagentdemo.service.ocr.OcrService.OcrResult;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/file", produces = MediaType.APPLICATION_JSON_VALUE)
public class FileController {

    @Resource
    private OcrService ocrService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse uploadFile(@RequestParam("file") MultipartFile file) {
        OcrResult result = ocrService.parseFile(file);
        if (result.isSuccess()) {
            return ChatResponse.ok("文件解析成功", 
                    Map.of("filename", result.getFilename(),
                           "fileType", result.getFileType(),
                           "content", result.getContent()));
        }
        return ChatResponse.fail(result.getErrorMessage());
    }
}
