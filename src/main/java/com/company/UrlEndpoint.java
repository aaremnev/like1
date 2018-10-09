package com.company;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class UrlEndpoint {

    @RequestMapping("/")
    @ResponseBody
    String hello() {
        return "Hello World!";
    }

}