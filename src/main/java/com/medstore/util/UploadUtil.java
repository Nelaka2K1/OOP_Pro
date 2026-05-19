package com.medstore.util;

import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

public final class UploadUtil {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> PRESCRIPTION_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_IMAGE_BYTES = 4_000_000;
    private static final long MAX_PRESCRIPTION_BYTES = 8_000_000;

    private UploadUtil() {
    }

    public static String saveImage(Part filePart, Path targetDir, String baseName) throws IOException {
        return saveFile(filePart, targetDir, baseName, IMAGE_TYPES, MAX_IMAGE_BYTES, true);
    }

    public static String savePrescription(Part filePart, Path targetDir, String baseName) throws IOException {
        return saveFile(filePart, targetDir, baseName, PRESCRIPTION_TYPES, MAX_PRESCRIPTION_BYTES, false);
    }

    private static String saveFile(Part filePart, Path targetDir, String baseName,
                                   Set<String> allowedTypes, long maxBytes, boolean imageOnly)
            throws IOException {
        if (filePart == null || filePart.getSize() == 0) {
            throw new IOException("no file uploaded");
        }
        if (filePart.getSize() > maxBytes) {
            throw new IOException("file too large");
        }
        String contentType = filePart.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IOException(imageOnly
                    ? "only JPEG, PNG, WebP or GIF images allowed"
                    : "only images or PDF allowed");
        }
        String ext = extensionFor(contentType);
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(baseName + ext);
        try (InputStream in = filePart.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.getFileName().toString();
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }
}
