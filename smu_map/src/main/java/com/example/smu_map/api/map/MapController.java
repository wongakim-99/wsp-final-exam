package com.example.smu_map.api.map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.io.File;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/map")
public class MapController {
    // 정적 리소스 경로
    private final String UPLOAD_DIR = "uploaded_tiles/";

    // ZIP 파일 업로드 및 압축 해제
    @PostMapping("/upload")
    public ResponseEntity<String> uploadMapTiles(@RequestParam("file") MultipartFile file) {
        try {
            // 업로드 디렉토리 생성
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // ZIP 파일 확인
            if (!Objects.requireNonNull(file.getOriginalFilename()).endsWith(".zip")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ZIP 파일만 업로드 가능합니다.");
            }

            // ZIP 파일 압축 해제
            try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    File destFile = new File(UPLOAD_DIR, entry.getName());
                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        destFile.getParentFile().mkdirs();
                        Files.copy(zipInputStream, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            return ResponseEntity.ok("업로드 성공");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("업로드 실패: " + e.getMessage());
        }
    }

    // 저장된 타일 리스트 반환
    @GetMapping("/tiles")
    public ResponseEntity<List<String>> getMapTiles() {
        File folder = new File(UPLOAD_DIR + "map_tile_data");
        if (!folder.exists() || !folder.isDirectory()) {
            return ResponseEntity.ok(List.of());
        }

        List<String> tiles = Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                .filter(File::isFile)
                .map(file -> "/uploaded_tiles/map_tile_data/" + file.getName())
                .collect(Collectors.toList());

        return ResponseEntity.ok(tiles);
    }
}
