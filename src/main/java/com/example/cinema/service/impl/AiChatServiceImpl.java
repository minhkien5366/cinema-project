package com.example.cinema.service.impl;

import com.example.cinema.entity.Movie;
import com.example.cinema.repository.MovieRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl {

    private final MovieRepository movieRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper(); // 🔥 ĐÃ THÊM: Xử lý JSON chuẩn doanh nghiệp

    @Value("${ai.api.key}")
    private String aiApiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    public String getAiResponse(String userMessage) {
        try {
            String systemPrompt = buildSystemPrompt();
            String combinedMessage = systemPrompt + "\n\nKhách hỏi: " + userMessage;
            
            // Ép chuỗi an toàn tuyệt đối
            String safeMessage = combinedMessage.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            
            String requestBody = "{\n  \"contents\": [{\n    \"parts\":[{\"text\": \"" + safeMessage + "\"}]\n  }]\n}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL + aiApiKey, request, String.class);
            return extractTextFromGeminiResponse(response.getBody());

        } catch (Exception e) {
            System.err.println("Lỗi AI: " + e.getMessage());
            return "Xin lỗi, hiện tại khối não A&K AI đang bận nâng cấp. Bạn vui lòng chọn tính năng 'Gặp Quản Lý' nhé!";
        }
    }

    private String buildSystemPrompt() {
        List<Movie> allMovies = movieRepository.findAll(); 
        
        // Phân loại phim
        String showingMovies = allMovies.stream()
                .filter(m -> "Đang chiếu".equalsIgnoreCase(m.getStatus()) || "NOW_SHOWING".equalsIgnoreCase(m.getStatus()))
                .map(m -> "- ID: " + m.getId() + " | Tên: " + m.getTitle() + " | Ảnh: " + m.getPosterUrl())
                .collect(Collectors.joining("\n"));

        String upcomingMovies = allMovies.stream()
                .filter(m -> "Sắp chiếu".equalsIgnoreCase(m.getStatus()) || "UPCOMING".equalsIgnoreCase(m.getStatus()))
                .map(m -> "- ID: " + m.getId() + " | Tên: " + m.getTitle() + " | Ảnh: " + m.getPosterUrl())
                .collect(Collectors.joining("\n"));

        return "Bạn là A&K AI, siêu trợ lý của hệ thống rạp A&K Cinema. Bạn biết mọi thứ trên thế giới, cực kỳ thông minh, nói chuyện tự nhiên, có thể giải toán, làm thơ, viết code. Nhưng nhiệm vụ chính của bạn vẫn là tư vấn rạp phim.\n" +
               "Thông tin A&K Cinema hôm nay (" + LocalDate.now() + "):\n" +
               "- Chi nhánh 1: A&K Center Point (59 Pasteur, Quận 1)\n" +
               "- Chi nhánh 2: A&K Thủ Đức (Vincom Thủ Đức)\n" +
               "\n[PHIM ĐANG CHIẾU]:\n" + showingMovies + "\n" +
               "\n[PHIM SẮP CHIẾU]:\n" + upcomingMovies + "\n\n" +
               "🚨 LUẬT BẮT BUỘC (NẾU VI PHẠM SẼ BỊ PHẠT): \n" +
               "1. Khi khách hỏi tìm phim, gợi ý phim, phim mới, BẠN BẮT BUỘC phải dùng định dạng sau để hệ thống hiện ảnh 3D:\n" +
               "   $$MOVIE|id_phim|tên_phim|url_ảnh$$\n" +
               "2. CHỈ ĐƯỢC HIỂN THỊ TỐI ĐA 2 PHIM (2 dòng $$MOVIE...$$).\n" +
               "3. Nếu danh sách có từ 3 phim trở lên, BẮT BUỘC phải thêm đúng 1 thẻ $$SEEMORE$$ ở cuối.\n" +
               "Ví dụ chuẩn: \n" +
               "Dạ đây là 2 bộ phim hot nhất:\n" +
               "$$MOVIE|1|Mai|http://anh.com/mai.jpg$$\n" +
               "$$MOVIE|2|Lật Mặt 7|http://anh.com/lm7.jpg$$\n" +
               "$$SEEMORE$$";
    }

    private String extractTextFromGeminiResponse(String json) {
        try {
            // 🔥 ĐÃ THÊM: Đọc JSON bằng thư viện Jackson chuẩn chỉ, không bao giờ lỗi
            JsonNode rootNode = objectMapper.readTree(json);
            return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            return "Dữ liệu AI trả về đang bị nhiễu sóng, bạn hỏi lại nhé!";
        }
    }
}