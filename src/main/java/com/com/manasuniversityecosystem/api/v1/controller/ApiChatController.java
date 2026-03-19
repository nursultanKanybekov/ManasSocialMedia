package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chat", description = "Direct messages, group rooms, file uploads and pinned messages")
public class ApiChatController {

    private final ChatService        chatService;
    private final ChatRoomRepository chatRoomRepo;
    private final UserService        userService;
    private final CloudinaryService  cloudinaryService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record RoomSummary(
            UUID   id, String name, String roomType,
            String lastMessage, String lastSenderName,
            LocalDateTime lastMessageAt, String otherUserAvatar
    ) {}

    public record MessageResponse(
            UUID   id, UUID senderId, String senderName, String senderAvatar,
            String content, String messageType,
            boolean isDeleted, boolean isEdited, boolean isPinned,
            UUID replyToId, String replyToContent, String replyToSenderName,
            String forwardedFrom, LocalDateTime createdAt, LocalDateTime editedAt
    ) {}

    public record SendMessageBody(
            @NotBlank @Size(max = 10_000) String content,
            String messageType,
            UUID   replyToId
    ) {}

    public record CreateGroupBody(
            @NotBlank @Size(max = 100) String name,
            List<UUID> memberIds
    ) {}

    public record UserSearchResult(
            UUID id, String fullName, String email, String role, String avatarUrl
    ) {}

    // ══ Rooms ════════════════════════════════════════════════════

    @GetMapping("/rooms")
    @Operation(summary = "List all chat rooms for the authenticated user")
    public ResponseEntity<ApiResponse<List<RoomSummary>>> getRooms(
            @RequestParam(required = false) String roomType,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<ChatRoom> rooms = chatService.getUserRoomsOrderedByActivity(principal.getId());
        var previews = chatService.buildLastMessagePreviews(rooms);

        List<RoomSummary> summaries = rooms.stream()
                .filter(r -> roomType == null || r.getRoomType().name().equalsIgnoreCase(roomType))
                .map(r -> {
                    String last = previews.get(r.getId());
                    String otherAvatar = "DIRECT".equals(r.getRoomType().name())
                            ? r.getParticipants().stream()
                            .filter(p -> !p.getUser().getId().equals(principal.getId()))
                            .findFirst()
                            .map(p -> p.getUser().getProfile() != null
                                    ? p.getUser().getProfile().getAvatarUrl() : null)
                            .orElse(null)
                            : null;
                    return new RoomSummary(r.getId(),
                            chatService.getRoomDisplayName(r, principal.getId()),
                            r.getRoomType().name(), last, null,
                            r.getCreatedAt(), otherAvatar);
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @PostMapping("/direct/{userId}")
    @Operation(summary = "Open or get a direct message room with another user")
    public ResponseEntity<ApiResponse<RoomSummary>> openDirect(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser me    = userService.getById(principal.getId());
        AppUser other = userService.getById(userId);
        ChatRoom room = chatService.getOrCreateDirectRoom(me, other);
        String name   = chatService.getRoomDisplayName(room, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                new RoomSummary(room.getId(), name, room.getRoomType().name(),
                        null, null, room.getCreatedAt(), null)));
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a new group chat room")
    public ResponseEntity<ApiResponse<RoomSummary>> createGroup(
            @Valid @RequestBody CreateGroupBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser creator = userService.getById(principal.getId());
        List<AppUser> members = body.memberIds() != null
                ? body.memberIds().stream().map(userService::getById).toList()
                : List.of();
        ChatRoom room = chatService.createGroupRoom(creator, body.name(), members);
        return ResponseEntity.status(201).body(ApiResponse.created(
                new RoomSummary(room.getId(), room.getName(),
                        room.getRoomType().name(), null, null, room.getCreatedAt(), null)));
    }

    @PostMapping("/global")
    @Operation(summary = "Get or join the university-wide global chat room")
    public ResponseEntity<ApiResponse<RoomSummary>> globalRoom(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatService.getGlobalRoom(userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.ok(
                new RoomSummary(room.getId(), room.getName(),
                        room.getRoomType().name(), null, null, room.getCreatedAt(), null)));
    }

    @PostMapping("/faculty")
    @Operation(summary = "Get or join the faculty chat room")
    public ResponseEntity<ApiResponse<RoomSummary>> facultyRoom(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatService.getOrCreateFacultyRoom(userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.ok(
                new RoomSummary(room.getId(), room.getName(),
                        room.getRoomType().name(), null, null, room.getCreatedAt(), null)));
    }

    // ══ Messages ═════════════════════════════════════════════════

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Get message history for a room (paginated, newest-first)")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getHistory(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size) {

        // getRoomHistory returns List — wrap in a Page manually
        List<ChatMessage> all = chatService.getRoomHistory(roomId, page);
        int start = 0, end = Math.min(size, all.size());
        List<ChatMessage> slice = all.subList(start, end);
        Page<ChatMessage> paged = new PageImpl<>(slice, PageRequest.of(page, size), all.size());

        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(paged.map(this::toMsg))));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Send a message to a room")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser sender  = userService.getById(principal.getId());
        String  msgType = body.messageType() != null ? body.messageType() : "TEXT";
        ChatMessage msg = chatService.sendMessage(sender, roomId, body.content(), msgType);
        return ResponseEntity.status(201).body(ApiResponse.created(toMsg(msg)));
    }

    @PostMapping(value = "/rooms/{roomId}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file / image / voice to a chat room")
    public ResponseEntity<ApiResponse<MessageResponse>> uploadFile(
            @PathVariable UUID roomId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "type", required = false) String type,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser sender    = userService.getById(principal.getId());
        String  msgType   = (type != null && !type.isBlank()) ? type.toUpperCase() : "FILE";
        String  folder    = "FILE".equals(msgType) ? "manas/chat/files" : "manas/chat/images";

        String url;
        try { url = cloudinaryService.uploadImage(file, folder, null); }
        catch (Exception e) { throw new RuntimeException("Upload failed: " + e.getMessage()); }

        ChatMessage msg = chatService.sendMessage(sender, roomId, url, msgType);
        return ResponseEntity.status(201).body(ApiResponse.created(toMsg(msg)));
    }

    @DeleteMapping("/messages/{id}")
    @Operation(summary = "Delete a message")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        chatService.deleteMessage(id, userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/messages/{id}/pin")
    @Operation(summary = "Toggle pin on a message")
    public ResponseEntity<ApiResponse<Void>> togglePin(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        chatService.togglePin(id, userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @GetMapping("/rooms/{roomId}/pinned")
    @Operation(summary = "Get all pinned messages in a room")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getPinned(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getPinnedMessages(roomId).stream().map(this::toMsg).toList()));
    }

    @GetMapping("/users/search")
    @Operation(summary = "Search users by name or email")
    public ResponseEntity<ApiResponse<List<UserSearchResult>>> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "15") int limit) {

        return ResponseEntity.ok(ApiResponse.ok(
                userService.getAllByRole(com.com.manasuniversityecosystem.domain.enums.UserRole.STUDENT)
                        .stream()
                        .filter(u -> u.getFullName().toLowerCase().contains(q.toLowerCase())
                                || u.getEmail().toLowerCase().contains(q.toLowerCase()))
                        .limit(limit)
                        .map(u -> new UserSearchResult(u.getId(), u.getFullName(), u.getEmail(),
                                u.getRole().name(),
                                u.getProfile() != null ? u.getProfile().getAvatarUrl() : null))
                        .toList()
        ));
    }

    // ══ Mapper ═══════════════════════════════════════════════════

    private MessageResponse toMsg(ChatMessage m) {
        return new MessageResponse(
                m.getId(),
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null ? m.getSender().getFullName() : "Unknown",
                m.getSender() != null && m.getSender().getProfile() != null
                        ? m.getSender().getProfile().getAvatarUrl() : null,
                Boolean.TRUE.equals(m.getIsDeleted()) ? null : m.getContent(),
                m.getMessageType().name(),
                Boolean.TRUE.equals(m.getIsDeleted()),
                Boolean.TRUE.equals(m.getIsEdited()),
                Boolean.TRUE.equals(m.getIsPinned()),
                m.getReplyToId(), m.getReplyToContent(), m.getReplyToSenderName(),
                m.getForwardedFrom(), m.getCreatedAt(), m.getEditedAt()
        );
    }
}