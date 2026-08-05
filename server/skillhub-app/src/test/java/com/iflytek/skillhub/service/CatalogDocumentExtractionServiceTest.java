package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.catalog.domain.CatalogDomainException;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CatalogDocumentExtractionServiceTest {
    private final CatalogDocumentExtractionService service = new CatalogDocumentExtractionService();

    @Test
    void extractsTextAndMarkdownDocuments() {
        assertThat(service.extract(new MockMultipartFile("file", "guide.md", "text/markdown", "# Guide\nStart here".getBytes())))
                .isEqualTo("# Guide\nStart here");
        assertThat(service.extract(new MockMultipartFile("file", "guide.txt", "text/plain", "Start here".getBytes())))
                .isEqualTo("Start here");
    }

    @Test
    void extractsDocxDocuments() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Start here");
            document.write(output);
            assertThat(service.extract(new MockMultipartFile("file", "guide.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.toByteArray())))
                    .contains("Start here");
        }
    }

    @Test
    void rejectsUnsupportedOrEmptyDocuments() {
        assertThatThrownBy(() -> service.extract(new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] {1})))
                .isInstanceOf(CatalogDomainException.class);
        assertThatThrownBy(() -> service.extract(new MockMultipartFile("file", "guide.txt", "text/plain", new byte[0])))
                .isInstanceOf(CatalogDomainException.class);
    }
}
