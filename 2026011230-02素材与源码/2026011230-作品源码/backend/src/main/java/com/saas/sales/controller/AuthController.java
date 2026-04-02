package com.saas.sales.controller;

import com.saas.sales.dto.ApiResponse;
import com.saas.sales.dto.LoginRequest;
import com.saas.sales.dto.LoginResponse;
import com.saas.sales.dto.RegisterRequest;
import com.saas.sales.entity.User;
import com.saas.sales.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ApiResponse<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ApiResponse.success("注册成功", user);
    }

    @PostMapping("/login")
    //@Valid 校验请求体是否符合 LoginRequest 定义的规则
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ApiResponse.success("登录成功", response);
    }
}