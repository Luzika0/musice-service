package user.music_service.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import user.music_service.constant.Constant;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.repository.UserRepository;
import user.music_service.service.HlsPlaybackService;

@RestController
@RequestMapping("/song/{songId}/hls")
@RequiredArgsConstructor
public class HlsController {
    private final HlsPlaybackService hlsPlaybackService;
    private final RequestHeaderData requestHeaderData;
    private final UserRepository userRepository;

    @Value("${hls.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${hls.cookie.path:/}")
    private String cookiePath;

    @PostMapping("/session")
    public ResponseEntity<GeneralResponse> createSession(
            @PathVariable UUID songId,
            @RequestHeader(name = "playBack-token", required = false) String playbackToken) {
        var session = hlsPlaybackService.createBrowserSession(
                playbackToken, requestHeaderData.getUserId(), songId);
        ResponseCookie cookie = ResponseCookie.from("HLS_SESSION", session.cookieValue())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(cookiePath)
                .maxAge(session.maxAgeSeconds())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new GeneralResponse(Map.of("expiresInSeconds", session.maxAgeSeconds())));
    }

    @GetMapping(value = "/audio.m3u8", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<byte[]> playlist(
            @PathVariable UUID songId,
            @RequestHeader(name = "Cookie", required = false) String cookieHeader) throws IOException {
        String cookie = cookieValue(cookieHeader);
        byte[] bytes = hlsPlaybackService.readAsset(
                songId, cookie, requestHeaderData.getUserId(), "audio.m3u8");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(bytes);
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<byte[]> segment(
            @PathVariable UUID songId,
            @PathVariable String fileName,
            @RequestHeader(name = "Cookie", required = false) String cookieHeader) throws IOException {
        if (!(fileName.endsWith(".ts") || fileName.endsWith(".m4s"))) {
            throw new AppException(ErrorCode.NOT_FOUND, "HLS asset not found.");
        }
        byte[] bytes = hlsPlaybackService.readAsset(
                songId, cookieValue(cookieHeader), requestHeaderData.getUserId(), fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp2t"))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(bytes);
    }

    @PostMapping(value = "/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeneralResponse> uploadPackage(
            @PathVariable UUID songId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Integer userId = requestHeaderData.getUserId();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "Authentication is required.");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHURIZED, "User not found."));
        if (user.getPermission() == null
                || !Constant.PERMISSION_ADMIN.equals(user.getPermission().getPermission())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, Constant.NOT_ENOUGH_PERMISSION_PERMISSION);
        }
        if (file.isEmpty() || file.getSize() > 280L * 1024 * 1024) {
            throw new AppException(ErrorCode.INVALID_INPUT, "HLS ZIP must be non-empty and at most 280 MB.");
        }
        Map<String, byte[]> files = readZip(file.getInputStream());
        hlsPlaybackService.uploadPackage(songId, files);
        return ResponseEntity.ok().body(new GeneralResponse(Map.of(
                "songId", songId,
                "uploadedFiles", files.size(),
                "prefix", "hls/" + songId + "/")));
    }

    private Map<String, byte[]> readZip(InputStream input) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                    throw new AppException(ErrorCode.INVALID_INPUT, "ZIP must contain files at its root only.");
                }
                if (files.size() >= 1000 || files.containsKey(name)) {
                    throw new AppException(ErrorCode.INVALID_INPUT, "ZIP contains too many or duplicate files.");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    totalBytes += count;
                    if (totalBytes > 280L * 1024 * 1024) {
                        throw new AppException(ErrorCode.INVALID_INPUT, "Uncompressed HLS package exceeds 280 MB.");
                    }
                    out.write(buffer, 0, count);
                }
                files.put(name, out.toByteArray());
                zip.closeEntry();
            }
        }
        return files;
    }

    private String cookieValue(String header) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String value = part.trim();
            if (value.startsWith("HLS_SESSION=")) {
                return value.substring("HLS_SESSION=".length());
            }
        }
        return null;
    }
}
