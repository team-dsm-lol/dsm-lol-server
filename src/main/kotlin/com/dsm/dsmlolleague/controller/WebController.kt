package com.dsm.dsmlolleague.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class WebController {
    
    @GetMapping("/")
    fun home(): String {
        return "index"
    }
    
    @GetMapping("/login")
    fun login(): String {
        return "login"
    }
    
    @GetMapping("/register-riot")
    fun registerRiot(): String {
        return "register-riot"
    }
    
    @GetMapping("/dashboard")
    fun dashboard(): String {
        return "dashboard"
    }
    
    @GetMapping("/teams")
    fun teams(): String {
        return "teams"
    }
    
    @GetMapping("/users")
    fun users(): String {
        return "users"
    }
    
    @GetMapping("/recruits")
    fun recruits(): String {
        return "recruits"
    }
} 