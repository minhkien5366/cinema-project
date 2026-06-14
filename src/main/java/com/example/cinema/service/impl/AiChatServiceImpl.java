package com.example.cinema.service.impl;

import com.example.cinema.entity.Cinema;
import com.example.cinema.entity.CinemaItem;
import com.example.cinema.entity.Movie;
import com.example.cinema.entity.Showtime;
import com.example.cinema.entity.Promotion;
import com.example.cinema.entity.Combo;     
import com.example.cinema.repository.CinemaRepository;
import com.example.cinema.repository.CinemaItemRepository;
import com.example.cinema.repository.MovieRepository;
import com.example.cinema.repository.ShowtimeRepository;
import com.example.cinema.repository.PromotionRepository; 
import com.example.cinema.repository.ComboRepository;   
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl {

    private final MovieRepository movieRepository;
    private final CinemaItemRepository cinemaItemRepository;
    private final CinemaRepository cinemaRepository;
    private final ShowtimeRepository showtimeRepository;
    
    // VÁ THÊM 2 REPOSITORY THEO YÊU CẦU
    private final PromotionRepository promotionRepository;
    private final ComboRepository comboRepository;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper(); 

    @Value("${ai.api.key}")
    private String aiApiKey;

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    public String getAiResponse(String userMessage) {
        String primaryModel = "gemini-2.5-flash";
        String backupModel = "gemini-1.5-flash";

        try {
            return callGeminiApi(primaryModel, userMessage);
        } catch (HttpServerErrorException.ServiceUnavailable | HttpServerErrorException.GatewayTimeout e) {
            try {
                return callGeminiApi(backupModel, userMessage);
            } catch (Exception ex) {
                return getFallbackErrorMessage();
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            try {
                return callGeminiApi(backupModel, userMessage);
            } catch (Exception ex) {
                return getFallbackErrorMessage();
            }
        } catch (Exception e) {
            return getFallbackErrorMessage();
        }
    }

    private String callGeminiApi(String modelName, String userMessage) throws Exception {
        String systemPrompt = buildSystemPrompt();
        
        ObjectNode rootRequestNode = objectMapper.createObjectNode();
        
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode siParts = systemInstruction.putArray("parts");
        siParts.addObject().put("text", systemPrompt);
        rootRequestNode.set("systemInstruction", systemInstruction);

        ArrayNode contentsArray = rootRequestNode.putArray("contents");
        ObjectNode userContent = contentsArray.addObject();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");
        userParts.addObject().put("text", userMessage);

        String requestBody = objectMapper.writeValueAsString(rootRequestNode);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        String fullUrl = GEMINI_BASE_URL + modelName + ":generateContent?key=" + aiApiKey;
        ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);
        return extractTextFromGeminiResponse(response.getBody());
    }

    private String getFallbackErrorMessage() {
        return "Xin lỗi bạn, hệ thống AI đang bận xử lý dữ liệu suất chiếu. Bạn vui lòng thử lại sau vài giây hoặc chọn 'Gặp Quản Lý' nhé!";
    }

    private String buildSystemPrompt() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // 1. LẤY CƠ CẤU PHÂN CẤP RẠP TO - RẠP CON
        List<Cinema> allCinemas = cinemaRepository.findAll();
        StringBuilder cinemaDataBuilder = new StringBuilder();
        for (Cinema cinema : allCinemas) {
            List<CinemaItem> subItems = cinemaItemRepository.findByCinemaId(cinema.getId());
            cinemaDataBuilder.append("- ").append(cinema.getName()).append("\n");
            for (CinemaItem item : subItems) {
                cinemaDataBuilder.append("  * Cụm: ").append(item.getName())
                        .append(" (Khu vực: ").append(item.getCity()).append(")\n");
            }
        }
        String cinemaBranches = cinemaDataBuilder.toString();

        // 2. LẤY TOÀN BỘ SUẤT CHIẾU TỪ HÔM NAY TRONG VÒNG 7 NGÀY TỚI
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<Showtime> futureShowtimes = showtimeRepository.findByStartTimeAfterOrderByStartTimeAsc(startOfToday);

        StringBuilder showtimeDataBuilder = new StringBuilder();
        if (futureShowtimes.isEmpty()) {
            showtimeDataBuilder.append("Hiện tại hệ thống chưa xếp lịch chiếu cho những ngày tới.\n");
        } else {
            // Sửa lỗi 'Cannot infer type': Thay 'var' bằng kiểu Map tường minh
            Map<String, List<Showtime>> showtimesByDate = futureShowtimes.stream()
                    .collect(Collectors.groupingBy(s -> s.getStartTime().format(dateFormatter)));

            showtimesByDate.forEach((dateStr, showtimeList) -> {
                showtimeDataBuilder.append("📅 NGÀY: ").append(dateStr).append("\n");
                
                Map<String, List<Showtime>> showtimesByMovie = showtimeList.stream()
                        .collect(Collectors.groupingBy(s -> s.getMovie().getTitle()));

                showtimesByMovie.forEach((movieTitle, movieShowtimes) -> {
                    String hours = movieShowtimes.stream()
                            .map(s -> s.getStartTime().format(timeFormatter) + " [" + s.getCinemaItem().getName() + "]")
                            .collect(Collectors.joining(", "));
                    showtimeDataBuilder.append("  + Phim: ").append(movieTitle).append(" -> Suất chiếu: ").append(hours).append("\n");
                });
                showtimeDataBuilder.append("\n");
            });
        }
        String allShowtimesCatalog = showtimeDataBuilder.toString();

        // 3. LẤY DANH SÁCH PHIM THÔ ĐỂ AI RENDER THẺ 3D (Đã sửa lỗi infer type bằng cách chỉ định rõ <String>)
        List<Movie> allMovies = movieRepository.findAll(); 
        String showingMovies = allMovies.stream()
                .filter(m -> "Đang chiếu".equalsIgnoreCase(m.getStatus()) || "NOW_SHOWING".equalsIgnoreCase(m.getStatus()))
                .<String>map(m -> "  + Phim (ID: " + m.getId() + "): " + m.getTitle() + " | Poster: " + m.getPosterUrl())
                .collect(Collectors.joining("\n"));

        String upcomingMovies = allMovies.stream()
                .filter(m -> "Sắp chiếu".equalsIgnoreCase(m.getStatus()) || "UPCOMING".equalsIgnoreCase(m.getStatus()))
                .<String>map(m -> "  + Phim (ID: " + m.getId() + "): " + m.getTitle() + " | Poster: " + m.getPosterUrl())
                .collect(Collectors.joining("\n"));

        // 4. LẤY DANH SÁCH KHUYẾN MÃI (PROMOTION) - ĐÃ THÊM VÀO PROMPT
        List<Promotion> allPromotions = promotionRepository.findAll();
        String promotionCatalog = allPromotions.stream()
                .<String>map(p -> "  + Ưu đãi: " + p.getTitle() + " (Nội dung: " + p.getContent() + (p.getCinemaItem() != null ? " - Áp dụng tại: " + p.getCinemaItem().getName() : " - Toàn hệ thống") + ")")
                .collect(Collectors.joining("\n"));
        if (promotionCatalog.isEmpty()) {
            promotionCatalog = "  Hiện tại rạp chưa có chương trình khuyến mãi nào.";
        }

        // 5. LẤY DANH SÁCH BẮP NƯỚC (COMBO) - ĐÃ THÊM VÀO PROMPT
        List<Combo> allCombos = comboRepository.findAll();
        String comboCatalog = allCombos.stream()
                .<String>map(c -> "  + Gói: " + c.getName() + " | Giá: " + c.getPrice() + " VNĐ (Gồm có: " + c.getDescription() + ")")
                .collect(Collectors.joining("\n"));
        if (comboCatalog.isEmpty()) {
            comboCatalog = "  Chưa có thông tin gói bắp nước.";
        }

        // 6. SYSTEM PROMPT SIÊU CẤP ĐIỀU HƯỚNG SẠCH SẼ (GIẤU KÍN MÃ NGUỒN SQL)
        return "# VAI TRÒ\n" +
                "Bạn là trợ lý ảo thông minh của hệ thống rạp phim A&K Cinema. Hãy trả lời bằng tiếng Việt lịch sự, ngắn gọn và tự nhiên.\n\n" +
                
                "# DANH SÁCH DỮ LIỆU THỜI GIAN THỰC (Hôm nay là ngày " + LocalDate.now().format(dateFormatter) + ")\n" +
                "## Danh sách cụm rạp:\n" + cinemaBranches + "\n" +
                "## Lịch chiếu chi tiết theo từng ngày:\n" + allShowtimesCatalog + "\n" +
                "## Phim đang chiếu:\n" + showingMovies + "\n" +
                "## Phim sắp chiếu:\n" + upcomingMovies + "\n" +
                "## Chương trình khuyến mãi đang áp dụng:\n" + promotionCatalog + "\n" +
                "## Bảng giá các gói Combo bắp nước:\n" + comboCatalog + "\n\n" +
                
                "# QUY ĐỊNH BẮT BUỘC KHI TƯ VẤN:\n" +
                "1. Tuyệt đối KHÔNG ĐƯỢC để lộ các từ ngữ kỹ thuật như: 'SQL', 'Database', 'Repository', 'Bảng dữ liệu', 'Trường status', 'Mã phim ID', 'CinemaItem', 'Null' khi nói chuyện với khách hàng.\n" +
                "2. Khi khách hỏi về lịch chiếu của một ngày bất kỳ, bạn bắt buộc phải quét mục [Lịch chiếu chi tiết theo từng ngày] để tìm đúng ngày tương ứng. Nếu ngày đó không xuất hiện, trả lời khéo léo là rạp chưa mở lịch chiếu chứ không tự bịa giờ.\n" +
                "3. Khi khách hỏi về bắp nước hoặc ưu đãi, hãy dùng chính xác dữ liệu bắp nước và khuyến mãi ở trên để tư vấn tên gói và giá tiền cho khách.\n" +
                "4. Định dạng chữ: Sử dụng dấu cặp sao `**chữ cần in đậm**` đối với tên phim, giờ chiếu, tên combo bắp nước hoặc thông tin giảm giá để tin nhắn hiển thị rõ ràng.\n\n" +
                
                "# QUY ĐỊNH PHẢN HỒI CHUNG\n" +
                "- Khi khách hỏi tìm phim, gợi ý phim: BẮT BUỘC dùng định dạng hiển thị ảnh giao diện: $$MOVIE|id|tên_phim|url_ảnh$$\n" +
                "- CHỈ ĐƯỢC hiện tối đa 2 thẻ $$MOVIE$$. Nếu từ 3 phim trở lên, in 2 phim đầu kèm một thẻ $$SEEMORE$$ cuối dòng.\n" +
                "- Khi khách chat tự do hoặc hỏi về combo/khuyến mãi: Trả lời linh hoạt bằng văn bản thông thường và dấu in đậm, CẤM chèn thẻ giao diện $$MOVIE$$.";
    }

    private String extractTextFromGeminiResponse(String json) {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            return "Kết nối dữ liệu AI đang bị nhiễu sóng, bạn vui lòng thử lại câu hỏi nhé!";
        }
    }
}