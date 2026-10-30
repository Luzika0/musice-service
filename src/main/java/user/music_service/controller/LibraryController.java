package user.music_service.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.PlaylistRequest;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.handler.AppException;
import user.music_service.service.LibraryService;

@RestController
@RequestMapping("/library")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraryService;
    private final RequestHeaderData requestHeaderData;

    private Integer userId() throws AppException {
        Integer id = requestHeaderData.getUserId();
        if (id == null) throw new AppException(user.music_service.enums.ErrorCode.UNAUTHURIZED, "Sign in to access your library.");
        return id;
    }

    @GetMapping("/favorites")
    public ResponseEntity<GeneralResponse> favorites() throws AppException {
        return ResponseEntity.ok(libraryService.listFavorites(userId()));
    }

    @PostMapping("/favorites/{songId}")
    public ResponseEntity<GeneralResponse> addFavorite(@PathVariable UUID songId) throws AppException {
        return ResponseEntity.ok(libraryService.addFavorite(userId(), songId));
    }

    @DeleteMapping("/favorites/{songId}")
    public ResponseEntity<GeneralResponse> removeFavorite(@PathVariable UUID songId) throws AppException {
        return ResponseEntity.ok(libraryService.removeFavorite(userId(), songId));
    }

    @GetMapping("/playlists")
    public ResponseEntity<GeneralResponse> playlists() throws AppException {
        return ResponseEntity.ok(libraryService.listPlaylists(userId()));
    }

    @PostMapping("/playlists")
    public ResponseEntity<GeneralResponse> createPlaylist(@Valid @RequestBody PlaylistRequest request) throws AppException {
        return ResponseEntity.ok(libraryService.createPlaylist(userId(), request));
    }

    @GetMapping("/playlists/{playlistId}")
    public ResponseEntity<GeneralResponse> playlist(@PathVariable Long playlistId) throws AppException {
        return ResponseEntity.ok(libraryService.getPlaylist(userId(), playlistId));
    }

    @PutMapping("/playlists/{playlistId}")
    public ResponseEntity<GeneralResponse> updatePlaylist(@PathVariable Long playlistId, @Valid @RequestBody PlaylistRequest request) throws AppException {
        return ResponseEntity.ok(libraryService.updatePlaylist(userId(), playlistId, request));
    }

    @DeleteMapping("/playlists/{playlistId}")
    public ResponseEntity<GeneralResponse> deletePlaylist(@PathVariable Long playlistId) throws AppException {
        return ResponseEntity.ok(libraryService.deletePlaylist(userId(), playlistId));
    }

    @PostMapping("/playlists/{playlistId}/songs/{songId}")
    public ResponseEntity<GeneralResponse> addSong(@PathVariable Long playlistId, @PathVariable UUID songId) throws AppException {
        return ResponseEntity.ok(libraryService.addSong(userId(), playlistId, songId));
    }

    @DeleteMapping("/playlists/{playlistId}/songs/{songId}")
    public ResponseEntity<GeneralResponse> removeSong(@PathVariable Long playlistId, @PathVariable UUID songId) throws AppException {
        return ResponseEntity.ok(libraryService.removeSong(userId(), playlistId, songId));
    }

    @PutMapping("/playlists/{playlistId}/songs")
    public ResponseEntity<GeneralResponse> reorderSongs(@PathVariable Long playlistId, @RequestBody List<UUID> songIds) throws AppException {
        return ResponseEntity.ok(libraryService.reorderSongs(userId(), playlistId, songIds));
    }
}
