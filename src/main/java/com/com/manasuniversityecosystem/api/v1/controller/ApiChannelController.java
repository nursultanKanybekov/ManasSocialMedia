package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatParticipant;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import com.com.manasuniversityecosystem.web.ws.GroupCallSignalController;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Channels", description = "Broadcast channels — list, create, members, messaging and video meetings")
public class ApiChannelController {

    private final ChatRoomRepository        chatRoomRepo;
    private final ChatService               chatService;
    private final CloudinaryService          cloudinaryService;
    private final UserService               userService;
    private final GroupCallSignalController groupCallSignalController;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record ChannelSummary(
            UUID   id,
            String name,
            String description,
            boolean isPrivate,
            int     memberCount,
            boolean isMember,
            String  ownerName,
            LocalDateTime createdAt
    ) {}

    public record ChannelDetail(
            UUID   id,
            String name,
            String description,
            boolean isPrivate,
            int     memberCount,
            boolean isMember,
            boolean isOwner,
            boolean isChannelAdmin,
            boolean canManage,
            boolean meetingActive,
            int     meetingCount,
            List<MemberSummary> members,
            LocalDateTime       createdAt
    ) {}

    public record MemberSummary(
            UUID    userId,
            String  fullName,
            String  role,
            String  avatarUrl,
            boolean isChannelAdmin,
            boolean isOwner
    ) {}

    public record MessageResponse(
            UUID          id,
            String        senderName,
            UUID          senderId,
            String        senderAvatar,
            String        content,
            String        messageType,   // TEXT | IMAGE | VOICE | FILE | POST_SHARE
            boolean       isDeleted,
            boolean       isPinned,
            UUID          replyToId,
            String        replyToContent,
            String        replyToSenderName,
            LocalDateTime createdAt
    ) {}

    public record SendMessageBody(
            @NotBlank @Size(max = 10_000) String content,
            String messageType    // default TEXT
    ) {}

    public record CreateChannelBody(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500)           String description,
            boolean                    isPrivate,
            List<UUID>                 memberIds
    ) {}

    public record AddMemberBody(@NotBlank UUID userId) {}

    public record MeetStatusResponse(boolean active, int participantCount) {}

    public record ToggleAdminResponse(boolean isChannelAdmin) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(
            summary     = "List all channels",
            description = "Returns all public channels plus channels the user is a member of. " +
                    "`is_member` flag is set on each so the app can separate My Channels."
    )
    public ResponseEntity<ApiResponse<List<ChannelSummary>>> listChannels(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<ChatRoom> rooms = chatRoomRepo
                .findByRoomTypeOrderByCreatedAtDesc(RoomType.CHANNEL);

        List<ChannelSummary> summaries = rooms.stream()
                .map(ch -> toSummary(ch, principal.getId()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get channel detail — members list, meeting status, permissions")
    public ResponseEntity<ApiResponse<ChannelDetail>> getChannel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean isMember = isParticipant(room, principal.getId());
        boolean isOwner  = room.getCreatedBy() != null
                && room.getCreatedBy().getId().equals(principal.getId());
        boolean isAdmin  = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean isChanAdmin = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId())
                        && Boolean.TRUE.equals(p.getIsChannelAdmin()));
        boolean canManage = isOwner || isAdmin || isChanAdmin;
        boolean meetActive = groupCallSignalController.isActive(id.toString());
        int     meetCount  = groupCallSignalController.getParticipantCount(id.toString());

        List<MemberSummary> members = room.getParticipants().stream()
                .map(p -> new MemberSummary(
                        p.getUser().getId(),
                        p.getUser().getFullName(),
                        p.getUser().getRole().name(),
                        p.getUser().getProfile() != null
                                ? p.getUser().getProfile().getAvatarUrl() : null,
                        Boolean.TRUE.equals(p.getIsChannelAdmin()),
                        room.getCreatedBy() != null
                                && p.getUser().getId().equals(room.getCreatedBy().getId())
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(new ChannelDetail(
                room.getId(), room.getName(), null,
                Boolean.TRUE.equals(room.getIsPrivate()),
                members.size(), isMember, isOwner, isChanAdmin, canManage,
                meetActive, meetCount, members, room.getCreatedAt()
        )));
    }

    @PostMapping
    @Operation(summary = "Create a new channel")
    public ResponseEntity<ApiResponse<ChannelSummary>> createChannel(
            @Valid @RequestBody CreateChannelBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser creator = userService.getById(principal.getId());
        List<AppUser> members = body.memberIds() != null
                ? body.memberIds().stream().map(userService::getById).toList()
                : List.of();

        ChatRoom room = ChatRoom.builder()
                .name(body.name())
                .roomType(RoomType.CHANNEL)
                .isPrivate(body.isPrivate())
                .createdBy(creator)
                .build();

        // Add creator as first participant then extra members
        ChatParticipant creatorP = ChatParticipant.builder()
                .user(creator).room(room)
                .isChannelAdmin(true).build();
        room.getParticipants().add(creatorP);
        members.stream()
                .filter(m -> !m.getId().equals(creator.getId()))
                .map(m -> ChatParticipant.builder().user(m).room(room).build())
                .forEach(room.getParticipants()::add);

        ChatRoom saved = chatRoomRepo.save(room);
        return ResponseEntity.status(201)
                .body(ApiResponse.created(toSummary(saved, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a channel (owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteChannel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean isOwner = room.getCreatedBy() != null
                && room.getCreatedBy().getId().equals(principal.getId());
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!isOwner && !isAdmin)
            throw new SecurityException("Only the channel owner can delete this channel.");

        chatRoomRepo.delete(room);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join a public channel")
    public ResponseEntity<ApiResponse<Void>> join(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        if (Boolean.TRUE.equals(room.getIsPrivate()))
            throw new SecurityException("Cannot join a private channel without an invitation.");
        if (isParticipant(room, principal.getId()))
            throw new IllegalStateException("Already a member.");

        AppUser user = userService.getById(principal.getId());
        room.getParticipants().add(
                ChatParticipant.builder().user(user).room(room).build());
        chatRoomRepo.save(room);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a channel (non-owners only)")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        if (room.getCreatedBy() != null
                && room.getCreatedBy().getId().equals(principal.getId()))
            throw new SecurityException("Owner cannot leave — delete the channel instead.");

        room.getParticipants().removeIf(
                p -> p.getUser().getId().equals(principal.getId()));
        chatRoomRepo.save(room);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member to a channel (admin/owner only)")
    public ResponseEntity<ApiResponse<MemberSummary>> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        requireManagePermission(room, principal.getId());

        if (isParticipant(room, body.userId()))
            throw new IllegalStateException("User is already a member.");

        AppUser newUser = userService.getById(body.userId());
        ChatParticipant cp = ChatParticipant.builder()
                .user(newUser).room(room).build();
        room.getParticipants().add(cp);
        chatRoomRepo.save(room);

        return ResponseEntity.status(201).body(ApiResponse.created(new MemberSummary(
                newUser.getId(), newUser.getFullName(), newUser.getRole().name(),
                newUser.getProfile() != null ? newUser.getProfile().getAvatarUrl() : null,
                false,
                room.getCreatedBy() != null
                        && newUser.getId().equals(room.getCreatedBy().getId())
        )));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member from a channel (admin/owner only)")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        requireManagePermission(room, principal.getId());
        room.getParticipants().removeIf(p -> p.getUser().getId().equals(userId));
        chatRoomRepo.save(room);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/members/{userId}/toggle-admin")
    @Operation(summary = "Grant or revoke channel-admin role for a member (owner only)")
    public ResponseEntity<ApiResponse<ToggleAdminResponse>> toggleAdmin(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean isOwner = room.getCreatedBy() != null
                && room.getCreatedBy().getId().equals(principal.getId());
        boolean isSysAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!isOwner && !isSysAdmin)
            throw new SecurityException("Only the owner can manage channel admins.");

        ChatParticipant target = room.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not a member."));

        boolean newValue = !Boolean.TRUE.equals(target.getIsChannelAdmin());
        target.setIsChannelAdmin(newValue);
        chatRoomRepo.save(room);
        return ResponseEntity.ok(ApiResponse.ok(new ToggleAdminResponse(newValue)));
    }

    // ── Messages ─────────────────────────────────────────────────

    @GetMapping("/{id}/messages")
    @Operation(
            summary     = "Get channel message history (paginated, newest-first)",
            description = "Page 0 returns the most recent messages. Increment page for older history."
    )
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean isMember = isParticipant(room, principal.getId());
        boolean isAdmin  = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!isMember && !isAdmin && Boolean.TRUE.equals(room.getIsPrivate()))
            throw new SecurityException("You are not a member of this channel.");

        List<ChatMessage> allMsgs = chatService.getRoomHistory(id, page);
        int toIdx = Math.min(size, allMsgs.size());
        Page<ChatMessage> messages = new PageImpl<>(allMsgs.subList(0, toIdx),
                PageRequest.of(page, size), allMsgs.size());
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                messages.map(this::toMessageResponse)
        )));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a text message to a channel")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser sender = userService.getById(principal.getId());
        ChatMessage msg = chatService.sendMessage(
                sender, id, body.content(),
                body.messageType() != null ? body.messageType() : "TEXT");
        return ResponseEntity.status(201)
                .body(ApiResponse.created(toMessageResponse(msg)));
    }

    @PostMapping(value = "/{id}/messages/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary     = "Upload a file/image/voice to a channel",
            description = "Accepted types: IMAGE, FILE, VOICE. Max 20 MB. " +
                    "Returns the uploaded message with a Cloudinary URL as content."
    )
    public ResponseEntity<ApiResponse<MessageResponse>> uploadAndSend(
            @PathVariable UUID id,
            @RequestPart("file")  MultipartFile file,
            @RequestPart(value = "type", required = false) String type,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser sender = userService.getById(principal.getId());
        // Reuse existing chat upload logic
        String messageType = (type != null && !type.isBlank()) ? type.toUpperCase() : "FILE";
        // ChatService handles Cloudinary upload internally via sendMessage overload
        String folder2 = "IMAGE".equals(messageType) ? "manas/chat/images" : "manas/chat/files";
        String url2;
        try { url2 = cloudinaryService.uploadImage(file, folder2, null); }
        catch (Exception e2) { throw new RuntimeException("Upload failed: " + e2.getMessage()); }
        ChatMessage msg = chatService.sendMessage(sender, id, url2, messageType);
        return ResponseEntity.status(201)
                .body(ApiResponse.created(toMessageResponse(msg)));
    }

    // ── Meeting status ────────────────────────────────────────────

    @GetMapping("/{id}/meet/status")
    @Operation(
            summary     = "Get live video meeting status for a channel",
            description = "Poll this endpoint to check whether a meeting is active before joining. " +
                    "For real-time updates subscribe to WebSocket topic `/topic/meet.{id}`."
    )
    public ResponseEntity<ApiResponse<MeetStatusResponse>> meetStatus(@PathVariable UUID id) {
        boolean active = groupCallSignalController.isActive(id.toString());
        int     count  = groupCallSignalController.getParticipantCount(id.toString());
        return ResponseEntity.ok(ApiResponse.ok(new MeetStatusResponse(active, count)));
    }

    // ── User search (add member autocomplete) ─────────────────────

    @GetMapping("/{id}/users/search")
    @Operation(summary = "Search users to add to a channel (name/email autocomplete)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchUsers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "") String q,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        ChatRoom room = chatRoomRepo.findByIdWithParticipants(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        requireManagePermission(room, principal.getId());

        String qLower = q.toLowerCase();
        List<AppUser> users = userService.getAllByRole(com.com.manasuniversityecosystem.domain.enums.UserRole.STUDENT)
                .stream()
                .filter(u -> u.getFullName().toLowerCase().contains(qLower)
                        || u.getEmail().toLowerCase().contains(qLower))
                .limit(20)
                .collect(java.util.stream.Collectors.toList());
        userService.getAllByRole(com.com.manasuniversityecosystem.domain.enums.UserRole.MEZUN)
                .stream()
                .filter(u -> u.getFullName().toLowerCase().contains(qLower)
                        || u.getEmail().toLowerCase().contains(qLower))
                .limit(20)
                .forEach(users::add);
        List<UUID> existingIds = room.getParticipants().stream()
                .map(p -> p.getUser().getId()).toList();

        List<Map<String, Object>> result = users.stream()
                .filter(u -> !existingIds.contains(u.getId()))
                .map(u -> Map.<String, Object>of(
                        "id",        u.getId(),
                        "full_name", u.getFullName(),
                        "email",     u.getEmail(),
                        "role",      u.getRole().name(),
                        "avatar_url", u.getProfile() != null && u.getProfile().getAvatarUrl() != null
                                ? u.getProfile().getAvatarUrl() : ""
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ══ Helpers ═══════════════════════════════════════════════════

    private ChannelSummary toSummary(ChatRoom room, UUID currentUserId) {
        return new ChannelSummary(
                room.getId(), room.getName(), null,
                Boolean.TRUE.equals(room.getIsPrivate()),
                room.getParticipants().size(),
                isParticipant(room, currentUserId),
                room.getCreatedBy() != null ? room.getCreatedBy().getFullName() : null,
                room.getCreatedAt()
        );
    }

    private MessageResponse toMessageResponse(ChatMessage m) {
        return new MessageResponse(
                m.getId(),
                m.getSender() != null ? m.getSender().getFullName() : "Unknown",
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null && m.getSender().getProfile() != null
                        ? m.getSender().getProfile().getAvatarUrl() : null,
                m.getContent(),
                m.getMessageType().name(),
                Boolean.TRUE.equals(m.getIsDeleted()),
                Boolean.TRUE.equals(m.getIsPinned()),
                m.getReplyToId(),
                m.getReplyToContent(),
                m.getReplyToSenderName(),
                m.getCreatedAt()
        );
    }

    private boolean isParticipant(ChatRoom room, UUID userId) {
        return room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(userId));
    }

    private void requireManagePermission(ChatRoom room, UUID callerId) {
        boolean isOwner = room.getCreatedBy() != null
                && room.getCreatedBy().getId().equals(callerId);
        boolean isChanAdmin = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(callerId)
                        && Boolean.TRUE.equals(p.getIsChannelAdmin()));
        if (!isOwner && !isChanAdmin)
            throw new SecurityException("You do not have permission to manage this channel.");
    }
}