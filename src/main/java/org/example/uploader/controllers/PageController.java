package org.example.uploader.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    String home() {
        return "index";
    }

    @GetMapping("/videos/{videoId}")
    String video(@PathVariable String videoId) {
        return "video";
    }
}
