package ru.kkd.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.kkd.api.ApiException;
import java.util.Set;

@Configuration
public class QueryValidationConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Set<String> allowed = switch (String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))) {
                    case "/api/kkd/summary" -> Set.of("run_id","unit_id");
                    case "/api/kkd/issues" -> Set.of("run_id","unit_id","rule_code","severity","delta_status","limit","offset");
                    default -> Set.of();
                };
                request.getParameterMap().forEach((key,values) -> {
                    if (!allowed.contains(key)) throw ApiException.validation("Неизвестный параметр: " + key);
                    if (values.length != 1 || values[0].isBlank()) throw ApiException.validation("Пустой или повторный параметр: " + key);
                    if ((key.equals("limit") || key.equals("offset")) && !values[0].matches("[0-9]+")) {
                        throw ApiException.validation("Параметр " + key + " должен быть целым неотрицательным числом");
                    }
                });
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
