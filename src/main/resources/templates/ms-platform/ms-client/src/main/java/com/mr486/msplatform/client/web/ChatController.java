package com.mr486.msplatform.client.web;

import com.mr486.msplatform.client.dto.ChatMessage;
import com.mr486.msplatform.client.service.ChatHistory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatHistory chatHistory;

    public ChatController(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("history", chatHistory.recent());
        return "chat";
    }

    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public ChatMessage send(ChatMessage in, Principal principal) {
        ChatMessage message = new ChatMessage(principal.getName(), in.text());
        chatHistory.add(message);
        return message;
    }
}
