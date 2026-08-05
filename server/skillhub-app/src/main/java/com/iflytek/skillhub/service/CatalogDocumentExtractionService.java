package com.iflytek.skillhub.service;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CatalogDocumentExtractionService {
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CatalogDomainException.badRequest("error.catalog.document.required");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw CatalogDomainException.badRequest("error.catalog.document.tooLarge", MAX_DOCUMENT_SIZE);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(file.getInputStream()); XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText().trim();
                }
            }
            if (name.endsWith(".md") || name.endsWith(".txt")) return new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw CatalogDomainException.badRequest("error.catalog.document.readFailed");
        }
        throw CatalogDomainException.badRequest("error.catalog.document.unsupported");
    }
}
