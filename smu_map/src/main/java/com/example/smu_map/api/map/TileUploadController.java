package com.example.smu_map.api.map;

import com.example.smu_map.domain.Tile;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@RestController
public class TileUploadController {
    private final TileRepository tileRepository;

    public TileUploadController(TileRepository tileRepository) {
        this.tileRepository = tileRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadTilesZip(@RequestParam("file") MultipartFile zipFile) {
        try {
            // 파일 정보 로그 출력
            log.info("Received file: {}", zipFile.getOriginalFilename());
            log.info("File size: {} bytes", zipFile.getSize());

            // 1. ZIP 파일 저장 경로
            String uploadDir = "uploaded_tiles/";
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) uploadDirFile.mkdirs();

            Path zipPath = Paths.get(uploadDir, zipFile.getOriginalFilename());
            Files.copy(zipFile.getInputStream(), zipPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. ZIP 파일 해제를 백그라운드에서 수행
            new Thread(() -> {
                try {
                    File unzippedDir = new File("tiles/");
                    if (!unzippedDir.exists()) unzippedDir.mkdirs();
                    unzip(zipPath.toFile(), unzippedDir); // 병렬화된 unzip 메서드 호출

                    log.info("Tiles directory created at: {}", unzippedDir.getAbsolutePath());

                    // 타일 파일 데이터베이스 저장
                    saveTilesToDatabase(unzippedDir);
                    log.info("Tile database update completed");
                } catch (IOException e) {
                    log.error("Failed to process ZIP file: {}", zipFile.getOriginalFilename(), e);
                }
            }).start();

            return ResponseEntity.ok("파일 업로드 성공: 백그라운드에서 처리 중");
        } catch (IOException e) {
            log.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ZIP 파일 처리 중 오류 발생");
        }
    }

    @GetMapping("/tiles")
    public ResponseEntity<List<String>> getTiles(@RequestParam int centerX, @RequestParam int centerY) {
        try {
            // 타일 디렉토리 경로
            File tilesDir = new File("tiles/");
            if (!tilesDir.exists() || !tilesDir.isDirectory()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // 중심 좌표를 기준으로 주변 타일 가져오기
            int range = 2; // 중심에서 ±2 범위
            List<String> tilePaths = new ArrayList<>();
            for (File file : tilesDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".png")) {
                    String[] parts = file.getName().replace(".png", "").split("_");
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    if (x >= centerX - range && x <= centerX + range && y >= centerY - range && y <= centerY + range) {
                        tilePaths.add("/tiles/" + file.getName()); // 클라이언트에서 접근 가능한 URL
                    }
                }
            }

            return ResponseEntity.ok(tilePaths);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // ZIP 파일 해제 유틸리티 메서드
    private void unzip(File zipFile, File outputDir) throws IOException {
        Logger logger = LoggerFactory.getLogger(TileUploadController.class);
        logger.info("Starting to unzip file: {}", zipFile.getAbsolutePath());

        List<ZipEntry> entries = new ArrayList<>(); // ZIP 엔트리 리스트
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(new ZipEntry(entry)); // 엔트리 복사
                zis.closeEntry();
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(8); // 병렬 스레드 풀
        for (ZipEntry entry : entries) {
            executor.submit(() -> {
                try {
                    File newFile = new File(outputDir, entry.getName());

                    if (entry.isDirectory()) {
                        newFile.mkdirs();
                        logger.info("Created directory: {}", newFile.getAbsolutePath());
                    } else {
                        newFile.getParentFile().mkdirs();
                        try (InputStream zipStream = new FileInputStream(zipFile)) {
                            Files.copy(zipStream, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            logger.info("Extracted file: {}", newFile.getAbsolutePath());
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to extract file: {}", entry.getName(), e);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS); // 최대 1시간 대기
        } catch (InterruptedException e) {
            logger.error("Executor interrupted", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Unzipping completed for file: {}", zipFile.getName());
    }

    // 타일 데이터를 데이터베이스에 저장하는 로직
    public void saveTilesToDatabase(File directory) {
        Logger logger = LoggerFactory.getLogger(TileUploadController.class);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        Queue<File> queue = new LinkedList<>();
        queue.add(directory);

        while (!queue.isEmpty()) {
            File current = queue.poll();
            for (File file : current.listFiles()) {
                if (file.isDirectory()) {
                    queue.add(file);
                } else if (file.getName().endsWith(".png")) {
                    executor.submit(() -> {
                        try {
                            String[] parts = file.getName().replace(".png", "").split("_");
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            tileRepository.save(new Tile(x, y, file.getAbsolutePath()));
                            logger.info("Saved tile: {}", file.getName());
                        } catch (Exception e) {
                            logger.error("Failed to save tile: {}", file.getName(), e);
                        }
                    });
                }
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            logger.error("Executor interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

}
