package com.playly.core.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GameFileApi {
    private final GameStorage storage;

    GameFileApi(GameStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/v1/game-files")
    ResponseEntity<InputStreamResource> download(@RequestParam(required = false) String token) {
        var object = storage.download(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(object.response().contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header("Referrer-Policy", "no-referrer")
                .body(new InputStreamResource(object));
    }
}
