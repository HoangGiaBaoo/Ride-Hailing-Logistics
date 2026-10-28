package org.example.ride_hailing_logistics.driver.service.impl;

import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.example.ride_hailing_logistics.driver.exception.InvalidDocumentException;
import org.example.ride_hailing_logistics.driver.service.DocumentStorageService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Luu tep tai lieu tai xe (DR-VAL-004, NFR-SEC-011):
 * - kiem MIME THAT bang magic bytes, khong tin Content-Type hay duoi file client gui
 * - <= 5 MB
 * - ten file do server sinh (UUID) -> khong path traversal; luu ngoai classpath -> khong bi phuc vu/thuc thi
 */
@Component
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    private final Path root;

    public LocalDocumentStorageService(AppProperties props) {
        this.root = Path.of(props.storage().documentsDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Vui lòng chọn tệp tài liệu.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("Tệp vượt quá 5 MB.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw invalid("Chỉ chấp nhận tệp JPG, PNG hoặc PDF.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String detected = detectContentType(bytes);
        if (detected == null || !matchesExtension(detected, extension)) {
            throw invalid("Nội dung tệp không đúng định dạng JPG, PNG hoặc PDF.");
        }
        String storageName = UUID.randomUUID() + "." + (detected.equals("application/pdf") ? "pdf" : detected.equals("image/png") ? "png" : "jpg");
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(storageName), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StoredFile(storageName, detected, bytes.length, Secrets.sha256Hex(bytes));
    }

    @Override
    public byte[] read(String storageName) {
        Path path = root.resolve(storageName).normalize();
        if (!path.startsWith(root)) {
            throw BusinessException.notFound("tài liệu");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw BusinessException.notFound("tệp tài liệu");
        }
    }

    public static String detectContentType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        return null;
    }

    private static boolean matchesExtension(String contentType, String extension) {
        return switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "application/pdf" -> extension.equals("pdf");
            default -> false;
        };
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid(String message) {
        return new InvalidDocumentException(message);
    }
}
