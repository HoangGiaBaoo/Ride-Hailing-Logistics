package org.example.ride_hailing_logistics.driver.service;

import org.springframework.web.multipart.MultipartFile;

/** Kho tep tai lieu tai xe; ban hien tai luu o dia cuc bo ngoai classpath. */
public interface DocumentStorageService {

    StoredFile store(MultipartFile file);

    byte[] read(String storageName);

    record StoredFile(String storageName, String contentType, long size, String sha256) {}
}
