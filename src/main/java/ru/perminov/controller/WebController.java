package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WebController {
    
    /**
     * Главная страница веб-интерфейса
     */
    @GetMapping
    public String index() {
        return "redirect:/index.html";
    }
    
    /**
     * Страница дашборда
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/index.html#dashboard";
    }
    
    /**
     * Страница инструментов
     */
    @GetMapping("/instruments")
    public String instruments() {
        return "redirect:/index.html#instruments";
    }
    
    /**
     * Страница портфеля
     */
    @GetMapping("/portfolio")
    public String portfolio() {
        return "redirect:/index.html#portfolio";
    }
    
    /**
     * Страница ордеров
     */
    @GetMapping("/orders")
    public String orders() {
        return "redirect:/index.html#orders";
    }
    
    /**
     * Страница торговли
     */
    @GetMapping("/trading")
    public String trading() {
        return "redirect:/index.html#trading";
    }
    
    /**
     * Страница настроек
     */
    @GetMapping("/settings")
    public String settings() {
        return "redirect:/index.html#settings";
    }
} 