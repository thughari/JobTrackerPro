package com.thughari.jobtrackerpro.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.Map;

@Component
public class GoogleNotificationDecoder {
    private final ObjectMapper mapper = new ObjectMapper();

    public String extractEmail(Map<String, Object> body) {
        try {
            Map<String, Object> message = (Map<String, Object>) body.get("message");
            String dataBase64 = (String) message.get("data");
            String decodedJson = new String(Base64.getDecoder().decode(dataBase64));
            JsonNode node = mapper.readTree(decodedJson);
            return node.get("emailAddress").asText().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}