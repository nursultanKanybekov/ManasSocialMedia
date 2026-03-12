package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // GET /chat  — chat lobby with all rooms
    @GetMapping
    public String chatLobby(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user  = userService.getById(principal.getId());
        List<ChatRoom> rooms = chatService.getUserRooms(principal.getId());

        model.addAttribute("currentUser", user);
        model.addAttribute("rooms",       rooms);
        model.addAttribute("roomNames",   buildRoomNames(rooms, principal.getId()));
        model.addAttribute("unreadCounts", buildUnreadCounts(rooms, principal.getId()));
        model.addAttribute("includeChat", true);
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
        long unread = chatService.getUnreadCount(id, principal.getId());

        ChatRoom currentRoom = chatService.getRoomWithParticipants(id);

        model.addAttribute("currentUser",  user);
        model.addAttribute("currentRoom",  currentRoom);
        model.addAttribute("roomId",       id);
        model.addAttribute("history",      history);
        model.addAttribute("unread",       unread);
        List<ChatRoom> sidebarRooms = chatService.getUserRooms(principal.getId());
        model.addAttribute("rooms",        sidebarRooms);
        model.addAttribute("roomNames",    buildRoomNames(sidebarRooms, principal.getId()));
        model.addAttribute("unreadCounts", buildUnreadCounts(sidebarRooms, principal.getId()));
        model.addAttribute("members",      currentRoom != null ? currentRoom.getParticipants() : java.util.List.of());
        model.addAttribute("includeChat",  true);
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

    // GET /chat/faculty-group/{roomId}  — Telegram-style faculty group view
    @GetMapping("/faculty-group/{roomId}")
    public String facultyGroup(@PathVariable UUID roomId,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        ChatRoom room = chatService.getOrCreateFacultyRoom(currentUser);

        // Auto-join if not a member
        chatService.joinRoom(currentUser, room.getId());

        // All users in this faculty
        java.util.List<AppUser> facultyMembers = currentUser.getFaculty() != null
                ? userRepo.findByFacultyIdAndStatus(currentUser.getFaculty().getId(), UserStatus.ACTIVE)
                : java.util.List.of();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("room", room);
        model.addAttribute("messages", chatService.getRoomHistory(room.getId(), 0));
        model.addAttribute("members", facultyMembers);
        model.addAttribute("roomId", room.getId());
        model.addAttribute("includeChat", true);
        return "chat/faculty-group";
    }

    // DELETE /chat/message/{id}  (HTMX)
    @DeleteMapping("/message/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> deleteMessage(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        chatService.deleteMessage(id, user);
        return org.springframework.http.ResponseEntity.ok().build();
    }
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