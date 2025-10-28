    package com.group6.Rental_Car.controllers;

    import com.group6.Rental_Car.services.storage.StorageService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.multipart.MultipartFile;

    import java.time.Duration;
    import java.util.Map;

    @RestController
    @RequestMapping("/api/upload")
    @RequiredArgsConstructor
    public class UploadController {

        private final StorageService storage;

        // Khách up CCCD → public-by-default (có URL public ngay)
        @PostMapping("/cccd")
        public ResponseEntity<Map<String, String>> uploadCCCD(@RequestPart("file") MultipartFile file) throws Exception {
            String url = storage.uploadPublic("cccd", file);
            return ResponseEntity.ok(Map.of("url", url));
        }

        // Khách up Bằng lái → private + presigned 3 phút cho admin xem
        @PostMapping(value = "/driver-license", consumes = "multipart/form-data")
        public ResponseEntity<Map<String, String>> uploadDriverLicense(
                @RequestParam("file") MultipartFile file) throws Exception {
            String url = storage.uploadPrivateAndPresign("driver-license", file, Duration.ofMinutes(3));
            return ResponseEntity.ok(Map.of("url", url));
        }
    }
