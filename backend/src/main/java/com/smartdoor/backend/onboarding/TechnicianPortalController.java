package com.smartdoor.backend.onboarding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TechnicianPortalController {

    @GetMapping({"/technician", "/technician/"})
    String portal() {
        return "forward:/technician/index.html";
    }
}
