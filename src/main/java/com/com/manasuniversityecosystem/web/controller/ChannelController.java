package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChatRoomRepository chatRoomRepo;
    private final ChatService chatService;
    private final ChatParticipantRepository participantRepo;
    private final SimpMessagingTemplate messaging;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        List<ChatRoom> channels = chatRoomRepo.findByRoomTypeOrderByCreatedAtDesc(RoomType.CHANNEL);
        // My channels (ones I created or joined)
        List<ChatRoom> myChannels = channels.stream()
                .filter(ch -> ch.getParticipants().stream()
                        .anyMatch(p -> p.getUser().getId().equals(principal.getId())))
                .toList();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("channels", channels);
        model.addAttribute("myChannels", myChannels);
        return "channels/list";
    }

    @GetMapping("/new")
    public String newChannelForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("currentUser", userService.getById(principal.getId()));
        return "channels/form";
    }

    @PostMapping("/new")
    public String createChannel(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser creator = userService.getById(principal.getId());
        ChatRoom channel = ChatRoom.builder()
                .name(name.trim())
                .roomType(RoomType.CHANNEL)
                .createdBy(creator)
                .build();
        channel.addParticipant(creator);
        chatRoomRepo.save(channel);
        return "redirect:/channels/" + channel.getId();
    }

    @GetMapping("/{roomId}")
    public String view(@PathVariable UUID roomId,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId()));
        boolean isOwner = room.getCreatedBy().getId().equals(principal.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("room", room);
        model.addAttribute("messages", chatService.getRoomHistory(roomId, 0));
        model.addAttribute("isMember", isMember);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("memberCount", room.getParticipants().size());
        model.addAttribute("members", room.getParticipants());
        return "channels/view";
    }

    @PostMapping("/{roomId}/join")
    public String joinChannel(@PathVariable UUID roomId,
                              @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        chatService.joinRoom(user, roomId);
        return "redirect:/channels/" + roomId;
    }

    @PostMapping("/{roomId}/leave")
    public String leaveChannel(@PathVariable UUID roomId,
                               @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        chatService.leaveRoom(user, roomId);
        return "redirect:/channels";
    }

    @PostMapping("/{roomId}/send")
    @ResponseBody
    public ResponseEntity<?> sendMessage(@PathVariable UUID roomId,
                                         @RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        ChatMessage msg = chatService.sendMessage(user, roomId, body.get("content"), "TEXT");
        // Push notifications after transaction commits
        try {
            String content = body.get("content");
            String preview = content != null && content.length() > 70 ? content.substring(0, 70) + "..." : content;
            String avatar  = user.getProfile() != null && user.getProfile().getAvatarUrl() != null
                    ? user.getProfile().getAvatarUrl() : "";
            Map<String, String> notif = new HashMap<>();
            notif.put("type",         "CHAT_MESSAGE");
            notif.put("senderName",   user.getFullName());
            notif.put("senderAvatar", avatar);
            notif.put("icon",         avatar.isBlank() ? "msg" : avatar);
            notif.put("roomName",     msg.getRoom().getName() != null ? msg.getRoom().getName() : "Channel");
            notif.put("message",      user.getFullName() + ": " + preview);
            notif.put("link",         "/channels/" + roomId);
            participantRepo.findParticipantsExcluding(roomId, user.getId())
                    .forEach(r -> messaging.convertAndSend("/topic/user." + r.getId(), notif));
        } catch (Exception e) { /* non-critical */ }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}