package com.zaze.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminControl {
    @RequestMapping("/showcase")
    public String showcase(Model model) {
        return "admin/showcase";
    }
}
