package com.nextportal.user;

import org.springframework.web.bind.annotation.*;

@RestController
public class PublicController {
    @GetMapping("/api/public/info") Info info() { return new Info("Next Portal", "데이터 포털 사용자 API 준비 중"); }
    public record Info(String name, String description) { }
}
