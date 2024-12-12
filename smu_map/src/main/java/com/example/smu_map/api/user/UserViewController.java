package com.example.smu_map.api.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserViewController {
    @GetMapping("/login")
    public String login(){
        return "login";
    }

    @GetMapping("/signup")
    public String signup(){
        return "signup";
    }

    @GetMapping("/login-error")
    public String loginError(Model model) {
        model.addAttribute("errorMessage", "유효하지 않은 아이디 또는 비밀번호 입니다");
        return "login-error"; // 에러 페이지로 이동
    }

    @GetMapping("/main")
    public String getMainPage() {
        // "main"은 templates 폴더에 있는 main.html (또는 main.jsp 등) 템플릿 파일을 반환
        return "main";
    }
}