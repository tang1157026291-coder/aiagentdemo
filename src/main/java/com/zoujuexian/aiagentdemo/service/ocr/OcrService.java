package com.zoujuexian.aiagentdemo.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class OcrService {

    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);

    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            ".txt", ".md", ".markdown", ".csv", ".json", ".xml", ".html", ".htm",
            ".log", ".ini", ".conf", ".cfg", ".yml", ".yaml", ".properties"
    );

    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".java", ".py", ".js", ".ts", ".go", ".rs", ".cpp", ".c", ".h",
            ".cs", ".rb", ".php", ".swift", ".kt", ".scala"
    );

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tiff", ".webp"
    );

    private static final List<String> PDF_EXTENSIONS = Arrays.asList(".pdf");

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public OcrResult parseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return OcrResult.fail("文件为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return OcrResult.fail("文件大小超过限制（最大10MB）");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return OcrResult.fail("无法获取文件名");
        }

        String extension = getExtension(originalFilename).toLowerCase();

        try {
            if (isTextFile(extension) || isCodeFile(extension)) {
                String content = readTextFile(file);
                return OcrResult.success(originalFilename, "text", content);
            }

            if (isImageFile(extension)) {
                String content = extractImageText(file, extension);
                return OcrResult.success(originalFilename, "image", content);
            }

            if (isPdfFile(extension)) {
                String content = extractPdfText(file);
                return OcrResult.success(originalFilename, "pdf", content);
            }

            return OcrResult.fail("不支持的文件类型: " + extension);

        } catch (Exception e) {
            logger.error("文件解析失败: {}", originalFilename, e);
            return OcrResult.fail("文件解析失败: " + e.getMessage());
        }
    }

    private String readTextFile(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String extractImageText(MultipartFile file, String extension) {
        return "[图片文件] " + file.getOriginalFilename() + "\n"
                + "大小: " + formatFileSize(file.getSize()) + "\n"
                + "格式: " + extension.substring(1).toUpperCase() + "\n\n"
                + "提示: 如需 OCR 识别图片中的文字，请安装 Tesseract OCR 并配置 tessdata 路径。\n"
                + "当前版本支持文本文件直接解析。";
    }

    private String extractPdfText(MultipartFile file) {
        return "[PDF文件] " + file.getOriginalFilename() + "\n"
                + "大小: " + formatFileSize(file.getSize()) + "\n\n"
                + "提示: 如需解析 PDF 内容，请添加 PDFBox 依赖 (org.apache.pdfbox:pdfbox)。\n"
                + "当前版本支持文本文件直接解析。";
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }

    private boolean isTextFile(String extension) {
        return TEXT_EXTENSIONS.contains(extension);
    }

    private boolean isCodeFile(String extension) {
        return CODE_EXTENSIONS.contains(extension);
    }

    private boolean isImageFile(String extension) {
        return IMAGE_EXTENSIONS.contains(extension);
    }

    private boolean isPdfFile(String extension) {
        return PDF_EXTENSIONS.contains(extension);
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    public static class OcrResult {
        private final boolean success;
        private final String filename;
        private final String fileType;
        private final String content;
        private final String errorMessage;

        private OcrResult(boolean success, String filename, String fileType, 
                          String content, String errorMessage) {
            this.success = success;
            this.filename = filename;
            this.fileType = fileType;
            this.content = content;
            this.errorMessage = errorMessage;
        }

        public static OcrResult success(String filename, String fileType, String content) {
            return new OcrResult(true, filename, fileType, content, null);
        }

        public static OcrResult fail(String errorMessage) {
            return new OcrResult(false, null, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getFilename() { return filename; }
        public String getFileType() { return fileType; }
        public String getContent() { return content; }
        public String getErrorMessage() { return errorMessage; }
    }
}
