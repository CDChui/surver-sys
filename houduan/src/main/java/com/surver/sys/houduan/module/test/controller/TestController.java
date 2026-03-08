package com.surver.sys.houduan.module.test.controller;

import com.surver.sys.houduan.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success(Map.of("ok", true));
    }
}
