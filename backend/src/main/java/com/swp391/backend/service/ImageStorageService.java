package com.swp391.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final Set<String> SAFE_NAME = Set.of("jpg", "png", "webp", "gif");

    private final Path imageDirectory;

    public ImageStorageService(@Value("${app.upload.directory:./uploads}") String uploadDirectory) {
        this.imageDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize().resolve("images");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an image to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller");
        }
        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, WebP, and GIF images are supported");
        }
        String filename = UUID.randomUUID() + extension;
        Path target = imageDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(imageDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid image filename");
        }
        try {
            Files.createDirectories(imageDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/api/uploads/images/" + filename;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store image");
        }
    }

    public Resource load(String filename) {
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!filename.matches("[0-9a-fA-F-]+\\.[A-Za-z]+") || !SAFE_NAME.contains(extension)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        }
        Path target = imageDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(imageDirectory) || !Files.isRegularFile(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        }
    }
}
