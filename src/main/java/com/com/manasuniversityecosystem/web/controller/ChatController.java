package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    // GET /chat  — chat lobby with all rooms
    @GetMapping
    public String chatLobby(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user  = userService.getById(principal.getId());
        List<ChatRoom> rooms = chatService.getUserRooms(principal.getId());

        model.addAttribute("currentUser",   user);
        model.addAttribute("rooms",         rooms);
        model.addAttribute("roomNames",     buildRoomNames(rooms, principal.getId()));
        model.addAttribute("unreadCounts",  buildUnreadCounts(rooms, principal.getId()));
        model.addAttribute("includeChat",   true);
        return "chat/chat";
    }

    // GET /chat/room/{id}  — open a specific room
    @GetMapping("/room/{id}")
    public String openRoom(@PathVariable UUID id,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        AppUser user = userService.getById(principal.getId());
        List<ChatMessage> history = chatService.getRoomHistory(id, 0);
        chatService.markRoomAsRead(user, id);

        ChatRoom currentRoom = chatService.getRoomWithParticipants(id);
        List<ChatMessage> pinnedMessages = chatService.getPinnedMessages(id);

        model.addAttribute("currentUser",    user);
        model.addAttribute("currentRoom",    currentRoom);
        model.addAttribute("roomId",         id);
        model.addAttribute("history",        history);
        model.addAttribute("pinnedMessages", pinnedMessages);
        model.addAttribute("unread",         chatService.getUnreadCount(id, principal.getId()));

        List<ChatRoom> sidebarRooms = chatService.getUserRooms(principal.getId());
        model.addAttribute("rooms",          sidebarRooms);
        model.addAttribute("roomNames",      buildRoomNames(sidebarRooms, principal.getId()));
        model.addAttribute("unreadCounts",   buildUnreadCounts(sidebarRooms, principal.getId()));
        model.addAttribute("members",        currentRoom != null ? currentRoom.getParticipants() : List.of());
        model.addAttribute("includeChat",    true);
        return "chat/chat";
    }

    // GET /chat/room/{id}/history?page=1  (HTMX load more)
    @GetMapping("/room/{id}/history")
    public String loadMoreHistory(@PathVariable UUID id,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model) {
        model.addAttribute("messages", chatService.getRoomHistory(id, page));
        model.addAttribute("roomId",   id);
        return "chat/fragments/message-list :: messageList";
    }

    // POST /chat/direct/{userId}  — open or create DM with user
    @PostMapping("/direct/{userId}")
    public String openDirectChat(@PathVariable UUID userId,
                                 @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser me    = userService.getById(principal.getId());
        AppUser other = userService.getById(userId);
        ChatRoom room = chatService.getOrCreateDirectRoom(me, other);
        return "redirect:/chat/room/" + room.getId();
    }

    // POST /chat/global  — open global room
    @PostMapping("/global")
    public String openGlobalChat(@AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        ChatRoom room = chatService.getGlobalRoom(user);
        return "redirect:/chat/room/" + room.getId();
    }

    // POST /chat/faculty  — open faculty channel
    @PostMapping("/faculty")
    public String openFacultyChat(@AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        ChatRoom room = chatService.getOrCreateFacultyRoom(user);
        return "redirect:/chat/faculty-group/" + room.getId();
    }

    // GET /chat/faculty-group/{roomId}
    @GetMapping("/faculty-group/{roomId}")
    public String facultyGroup(@PathVariable UUID roomId,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        ChatRoom room = chatService.getOrCreateFacultyRoom(currentUser);
        chatService.joinRoom(currentUser, room.getId());

        List<AppUser> facultyMembers = currentUser.getFaculty() != null
                ? userRepo.findByFacultyIdAndStatus(currentUser.getFaculty().getId(), UserStatus.ACTIVE)
                : List.of();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("room",        room);
        model.addAttribute("messages",    chatService.getRoomHistory(room.getId(), 0));
        model.addAttribute("members",     facultyMembers);
        model.addAttribute("roomId",      room.getId());
        model.addAttribute("includeChat", true);
        return "chat/faculty-group";
    }

    // POST /chat/upload — upload image/file/voice, return URL
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<java.util.Map<String,String>> uploadChatFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "type", defaultValue = "FILE") String type,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            String url;
            if ("IMAGE".equalsIgnoreCase(type)) {
                url = cloudinaryService.uploadImage(file, "chat/images", null);
            } else if ("VOICE".equalsIgnoreCase(type)) {
                url = cloudinaryService.uploadAudio(file, "chat/voice");
            } else {
                url = cloudinaryService.uploadDocument(file, "chat/files");
            }
            return ResponseEntity.ok(java.util.Map.of("url", url));
        } catch (Exception e) {
            log.error("Chat file upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // DELETE /chat/message/{id}
    @DeleteMapping("/message/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        chatService.deleteMessage(id, user);
        return ResponseEntity.ok().build();
    }

    // POST /chat/message/{id}/pin  — toggle pin
    @PostMapping("/message/{id}/pin")
    @ResponseBody
    public ResponseEntity<Void> pinMessage(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        chatService.togglePin(id, user);
        return ResponseEntity.ok().build();
    }

    // GET /chat/rooms  — returns user's rooms as JSON (for share modal)
    @GetMapping("/rooms")
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String,Object>>> getRoomsJson(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        List<ChatRoom> rooms = chatService.getUserRooms(principal.getId());
        List<java.util.Map<String,Object>> result = rooms.stream().map(r -> {
            java.util.Map<String,Object> m = new HashMap<>();
            m.put("id",       r.getId());
            m.put("name",     chatService.getRoomDisplayName(r, principal.getId()));
            m.put("roomType", r.getRoomType().name());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // POST /chat/share  — send a post share message to a room
    @PostMapping("/share")
    @ResponseBody
    public ResponseEntity<Void> shareToRoom(
            @RequestBody java.util.Map<String,String> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            UUID roomId = UUID.fromString(body.get("roomId"));
            String content = body.get("content");
            AppUser user = userService.getById(principal.getId());
            chatService.sendMessage(user, roomId, content, "TEXT", null, null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Share failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── helpers ───────────────────────────────────────────────

    private Map<UUID, String> buildRoomNames(List<ChatRoom> rooms, UUID userId) {
        Map<UUID, String> names = new HashMap<>();
        rooms.forEach(r -> names.put(r.getId(), chatService.getRoomDisplayName(r, userId)));
        return names;
    }

    private Map<UUID, Long> buildUnreadCounts(List<ChatRoom> rooms, UUID userId) {
        Map<UUID, Long> counts = new HashMap<>();
        rooms.forEach(r -> counts.put(r.getId(), chatService.getUnreadCount(r.getId(), userId)));
        return counts;
    }
}