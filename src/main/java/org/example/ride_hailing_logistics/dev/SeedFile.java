package org.example.ride_hailing_logistics.dev;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Tep tai lieu gia cho du lieu mau: anh PNG nho de di qua dung duong kiem magic bytes cua DocumentStorage. */
final class SeedFile implements MultipartFile {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1};

    private final String name;

    SeedFile(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return name;
    }

    @Override
    public String getContentType() {
        return "image/png";
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public long getSize() {
        return PNG.length;
    }

    @Override
    public byte[] getBytes() {
        return PNG.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(PNG);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), PNG);
    }
}
