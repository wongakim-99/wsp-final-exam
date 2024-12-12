package com.example.smu_map.api.user;

import com.example.smu_map.api.user.dto.AddUserRequest;
import com.example.smu_map.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

@RequiredArgsConstructor
@Controller
public class UserApiController {

    private final UserService userService;

    @PostMapping("/user")
    public String signup(AddUserRequest request, Model model) {
        try {
            userService.save(request); // 회원 가입 메소드 호출
            return "redirect:/login"; // 회원 가입이 완료된 후 로그인 페이지로 이동
        } catch (IllegalArgumentException e) {
            // 중복된 이메일로 회원가입 시 에러 처리
            model.addAttribute("signupError", "이미 존재하는 이메일입니다.");
            return "signup-error"; // 회원가입 에러 페이지로 이동
        }
    }
}