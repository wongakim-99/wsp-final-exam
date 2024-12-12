package com.example.smu_map.api.main;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Slf4j
@Controller
public class MainController {

    @GetMapping("/main")
    public String showMainPage(Principal principal, Model model) {
        log.info("Accessed main page");
        model.addAttribute("username", principal.getName()); // 로그인된 사용자 이름 전달
        return "main"; // main.html 뷰 반환
    }
}