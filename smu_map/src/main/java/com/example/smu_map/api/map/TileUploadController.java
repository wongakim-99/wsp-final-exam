package com.example.smu_map.api.map;

import com.example.smu_map.domain.Tile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
public class TileUploadController {
    private final TileRepository tileRepository;

    public TileUploadController(TileRepository tileRepository) {
        this.tileRepository = tileRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadTilesZip(@RequestParam("file") MultipartFile zipFile) {
        try {
            // 1. ZIP 파일 저장 경로
            String uploadDir = "uploaded_tiles/";
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) uploadDirFile.mkdirs();

            Path zipPath = Paths.get(uploadDir, zipFile.getOriginalFilename());
            Files.copy(zipFile.getInputStream(), zipPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. ZIP 파일 해제
            File unzippedDir = new File("tiles/");
            if (!unzippedDir.exists()) unzippedDir.mkdirs();
            unzip(zipPath.toFile(), unzippedDir);

            // 3. 타일 파일 데이터베이스 저장
            saveTilesToDatabase(unzippedDir);

            return ResponseEntity.ok("타일 업로드 및 처리 완료");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ZIP 파일 처리 중 오류 발생");
        }
    }

    // ZIP 파일 해제 유틸리티 메서드
    private void unzip(File zipFile, File outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    // 타일 데이터를 데이터베이스에 저장하는 로직
    public void saveTilesToDatabase(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".png")) {
                String[] parts = file.getName().replace(".png", "").split("_");
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                String path = file.getAbsolutePath();

                Tile tile = new Tile(x, y, path);
                tileRepository.save(tile);
            }
        }
    }
}
