package com.medstore.util;

import jakarta.servlet.ServletContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UploadStorage {

    private UploadStorage() {
    }

    /** Persistent uploads directory (shared by upload + media servlets). */
    public static Path root(ServletContext ctx) throws IOException {
        String configured = Config.get("uploads.directory", "").trim();
        Path base;
        if (!configured.isEmpty()) {
            base = Path.of(configured);
        } else {
            base = Path.of(System.getProperty("user.home"), ".medstore", "uploads");
        }
        Files.createDirectories(base);
        return base.toAbsolutePath().normalize();
    }

    public static Path resolve(ServletContext ctx, String relativePath) throws IOException {
        Path root = root(ctx);
        Path file = root.resolve(relativePath.replace('\\', '/')).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("invalid path");
        }
        return file;
    }
}
