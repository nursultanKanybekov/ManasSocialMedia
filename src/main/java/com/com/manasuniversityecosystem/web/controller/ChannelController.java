package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatParticipant;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import com.com.manasuniversityecosystem.web.ws.GroupCallSignalController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChatRoomRepository chatRoomRepo;
    private final ChatService chatService;
    private final ChatParticipantRepository participantRepo;
    private final SimpMessagingTemplate messaging;
    private final UserService userService;
    private final UserRepository userRepo;
    private final GroupCallSignalController groupCallSignalController;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        boolean isAdmin = isAdmin(principal);
        List<ChatRoom> channels = isAdmin
                ? chatRoomRepo.findByRoomTypeOrderByCreatedAtDesc(RoomType.CHANNEL)
                : chatRoomRepo.findVisibleChannels(principal.getId());
        List<ChatRoom> myChannels = channels.stream()
                .filter(ch -> ch.getParticipants().stream()
                        .anyMatch(p -> p.getUser().getId().equals(principal.getId())))
                .toList();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("channels",    channels);
        model.addAttribute("myChannels",  myChannels);
        model.addAttribute("isAdmin",     isAdmin);
        return "channels/list";
    }

    @GetMapping("/new")
    public String newChannelForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser me = userService.getById(principal.getId());
        model.addAttribute("currentUser", me);
        List<AppUser> suggested = me.getFaculty() != null
                ? userRepo.findByFacultyIdAndStatus(me.getFaculty().getId(), UserStatus.ACTIVE)
                .stream().filter(u -> !u.getId().equals(me.getId())).toList()
                : List.of();
        model.addAttribute("suggestedUsers", suggested);
        return "channels/form";
    }

    @PostMapping("/new")
    public String createChannel(@RequestParam String name,
                                @RequestParam(required = false) String description,
                                @RequestParam(defaultValue = "false") boolean isPrivate,
                                @RequestParam(required = false) List<UUID> memberIds,
                                @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser creator = userService.getById(principal.getId());
        ChatRoom channel = ChatRoom.builder()
                .name(name.trim())
                .roomType(RoomType.CHANNEL)
                .createdBy(creator)
                .isPrivate(isPrivate)
                .build();
        channel.addParticipant(creator);
        channel.getParticipants().get(0).setIsChannelAdmin(true);
        if (memberIds != null) {
            for (UUID uid : memberIds) {
                if (!uid.equals(creator.getId())) {
                    try { channel.addParticipant(userService.getById(uid)); }
                    catch (Exception ignored) {}
                }
            }
        }
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
        boolean isSystemAdmin = isAdmin(principal);
        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId()));
        if (room.getIsPrivate() && !isMember && !isSystemAdmin) return "redirect:/channels";
        boolean isOwner = room.getCreatedBy().getId().equals(principal.getId());
        boolean isChannelAdmin = isOwner || room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId())
                        && Boolean.TRUE.equals(p.getIsChannelAdmin()));
        boolean canManage = isChannelAdmin || isSystemAdmin;
        model.addAttribute("currentUser",    currentUser);
        model.addAttribute("room",           room);
        model.addAttribute("messages",       chatService.getRoomHistory(roomId, 0));
        model.addAttribute("isMember",       isMember);
        model.addAttribute("isOwner",        isOwner);
        model.addAttribute("isChannelAdmin", isChannelAdmin);
        model.addAttribute("canManage",      canManage);
        model.addAttribute("memberCount",    room.getParticipants().size());
        model.addAttribute("members",        room.getParticipants());
        model.addAttribute("meetingActive",  groupCallSignalController.isActive(roomId.toString()));
        model.addAttribute("meetingCount",   groupCallSignalController.getParticipantCount(roomId.toString()));
        return "channels/view";
    }

    /** Zoom-like group video conference for a channel */
    @GetMapping("/{roomId}/meet")
    public String meet(@PathVariable UUID roomId,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId()));
        boolean isSystemAdmin = isAdmin(principal);
        if (room.getIsPrivate() && !isMember && !isSystemAdmin) return "redirect:/channels/" + roomId;

        boolean isOwner = room.getCreatedBy().getId().equals(principal.getId());

        String avatarUrl = (currentUser.getProfile() != null && currentUser.getProfile().getAvatarUrl() != null)
                ? currentUser.getProfile().getAvatarUrl() : "";

        model.addAttribute("currentUser",  currentUser);
        model.addAttribute("currentAvatar", avatarUrl);
        model.addAttribute("room",          room);
        model.addAttribute("isMember",      isMember);
        model.addAttribute("isOwner",       isOwner);
        model.addAttribute("members",       room.getParticipants());
        model.addAttribute("memberCount",   room.getParticipants().size());
        return "channels/meet";
    }

    /** Live meeting status — polled by channel view page via fetch */
    @GetMapping("/{roomId}/meet/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> meetStatus(@PathVariable UUID roomId) {
        boolean active = groupCallSignalController.isActive(roomId.toString());
        int count      = groupCallSignalController.getParticipantCount(roomId.toString());
        return ResponseEntity.ok(Map.of("active", active, "count", count));
    }

    @GetMapping("/{roomId}/users/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchUsersForChannel(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "") String q,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();
        Set<UUID> existing = room.getParticipants().stream()
                .map(p -> p.getUser().getId()).collect(Collectors.toSet());
        List<AppUser> candidates;
        if (q.trim().length() >= 2) {
            candidates = userRepo.searchByNameOrEmail("%" + q.trim().toLowerCase() + "%", principal.getId());
        } else {
            AppUser me = userService.getById(principal.getId());
            candidates = me.getFaculty() != null
                    ? userRepo.findByFacultyIdAndStatus(me.getFaculty().getId(), UserStatus.ACTIVE)
                    : List.of();
        }
        List<Map<String, Object>> result = candidates.stream()
                .filter(u -> !existing.contains(u.getId()) && !u.getId().equals(principal.getId()))
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",        u.getId());
                    m.put("fullName",  u.getFullName());
                    m.put("role",      u.getRole().name());
                    m.put("email",     u.getEmail());
                    m.put("avatarUrl", u.getProfile() != null ? u.getProfile().getAvatarUrl() : null);
                    return m;
                }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{roomId}/add-member")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable UUID roomId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();
        if (!canManageRoom(room, principal)) return ResponseEntity.status(403).build();
        UUID userId = UUID.fromString(body.get("userId"));
        if (room.getParticipants().stream().anyMatch(p -> p.getUser().getId().equals(userId)))
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Already a member"));
        AppUser user = userService.getById(userId);
        room.addParticipant(user);
        chatRoomRepo.save(room);
        Map<String, Object> res = new HashMap<>();
        res.put("ok",        true);
        res.put("id",        user.getId().toString());
        res.put("fullName",  user.getFullName());
        res.put("role",      user.getRole().name());
        res.put("avatarUrl", user.getProfile() != null ? user.getProfile().getAvatarUrl() : null);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{roomId}/remove-member/{userId}")
    @ResponseBody
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();
        if (!canManageRoom(room, principal)) return ResponseEntity.status(403).build();
        if (room.getCreatedBy().getId().equals(userId)) return ResponseEntity.status(403).build();
        room.getParticipants().removeIf(p -> p.getUser().getId().equals(userId));
        chatRoomRepo.save(room);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/toggle-admin/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleChannelAdmin(
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId).orElse(null);
        if (room == null) return ResponseEntity.notFound().build();
        if (!room.getCreatedBy().getId().equals(principal.getId()) && !isAdmin(principal))
            return ResponseEntity.status(403).build();
        ChatParticipant target = room.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(userId)).findFirst().orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        boolean nowAdmin = !Boolean.TRUE.equals(target.getIsChannelAdmin());
        target.setIsChannelAdmin(nowAdmin);
        chatRoomRepo.save(room);
        return ResponseEntity.ok(Map.of("isChannelAdmin", nowAdmin));
    }

    @PostMapping("/{roomId}/join")
    public String joinChannel(@PathVariable UUID roomId,
                              @AuthenticationPrincipal UserDetailsImpl principal) {
        ChatRoom room = chatRoomRepo.findById(roomId).orElse(null);
        if (room != null && !Boolean.TRUE.equals(room.getIsPrivate())) {
            chatService.joinRoom(userService.getById(principal.getId()), roomId);
        }
        return "redirect:/channels/" + roomId;
    }

    @PostMapping("/{roomId}/leave")
    public String leaveChannel(@PathVariable UUID roomId,
                               @AuthenticationPrincipal UserDetailsImpl principal) {
        chatService.leaveRoom(userService.getById(principal.getId()), roomId);
        return "redirect:/channels";
    }

    /** DELETE channel — only owner or system admin */
    @PostMapping("/{roomId}/delete")
    public String deleteChannel(@PathVariable UUID roomId,
                                @AuthenticationPrincipal UserDetailsImpl principal,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        ChatRoom room = chatRoomRepo.findByIdWithParticipants(roomId).orElse(null);
        if (room == null) {
            ra.addFlashAttribute("error", "Channel not found");
            return "redirect:/channels";
        }
        boolean isOwner    = room.getCreatedBy().getId().equals(principal.getId());
        boolean isSysAdmin = isAdmin(principal);
        if (!isOwner && !isSysAdmin) {
            ra.addFlashAttribute("error", "Only the channel owner can delete this channel.");
            return "redirect:/channels/" + roomId;
        }
        String name = room.getName();
        try {
            chatService.deleteChannel(roomId);
            ra.addFlashAttribute("success", "Channel \"" + name + "\" has been deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to delete channel: " + e.getMessage());
            return "redirect:/channels/" + roomId;
        }
        return "redirect:/channels";
    }

    @PostMapping("/{roomId}/send")
    @ResponseBody
    public ResponseEntity<?> sendMessage(@PathVariable UUID roomId,
                                         @RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        String msgType = body.getOrDefault("messageType", "TEXT");
        ChatMessage msg = chatService.sendMessage(user, roomId, body.get("content"), msgType);
        try {
            String content = body.get("content");
            String preview  = content != null && content.length() > 70 ? content.substring(0, 70) + "..." : content;
            String avatar   = user.getProfile() != null && user.getProfile().getAvatarUrl() != null
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
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private boolean isAdmin(UserDetailsImpl p) {
        return p.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private boolean canManageRoom(ChatRoom room, UserDetailsImpl principal) {
        if (isAdmin(principal)) return true;
        if (room.getCreatedBy().getId().equals(principal.getId())) return true;
        return room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(principal.getId())
                        && Boolean.TRUE.equals(p.getIsChannelAdmin()));
    }
}