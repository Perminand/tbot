package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        return "forward:/index.html";
    }
    
    /**
     * Обработка POST запросов на главную страницу (перенаправление на GET)
     * Исправляет проблему с некорректными multipart запросами
     */
    @PostMapping
    public String indexPost() {
        log.debug("Получен POST запрос на главную страницу, перенаправляем на GET");
        return "redirect:/";
    }
    
    /**
     * Страница входа
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "logout", required = false) String logout,
                       Model model) {
        
        if (error != null) {
            model.addAttribute("error", "Неверный логин или пароль");
        }

        if (logout != null) {
            model.addAttribute("message", "Вы успешно вышли из системы");
        }

        return "login";
    }
    
    /**
     * Страница дашборда
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/index.html";
    }
    
    /**
     * Страница инструментов
     */
    @GetMapping("/instruments")
    public String instruments() {
        return "forward:/index.html";
    }
    
    /**
     * Страница портфеля
     */
    @GetMapping("/portfolio")
    public String portfolio() {
        return "forward:/index.html";
    }
    
    /**
     * Страница ордеров
     */
    @GetMapping("/orders")
    public String orders() {
        return "forward:/index.html";
    }
    
    /**
     * Страница торговли
     */
    @GetMapping("/trading")
    public String trading() {
        return "forward:/index.html";
    }
    
    /**
     * Страница настроек
     */
    @GetMapping("/settings")
    public String settings() {
        return "forward:/index.html";
    }
    
    /**
     * Страница аналитики
     */
    @GetMapping("/analysis")
    public String analysis() {
        return "forward:/index.html";
    }
    
    /**
     * Страница логов
     */
    @GetMapping("/logs")
    public String logs() {
        return "forward:/index.html";
    }
} 