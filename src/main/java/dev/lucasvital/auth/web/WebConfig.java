package dev.lucasvital.auth.web;

import dev.lucasvital.auth.user.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    // Libera CORS so para o frontend de validacao manual (auth-service-frontend,
    // Vite rodando em localhost:5173) em ambiente de desenvolvimento local.
    // Esta API foi desenhada para ser consumida por outro servico, nao por um
    // navegador direto -- esta liberacao NAO deveria valer em producao.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/auth/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization");

        registry
                .addMapping("/users/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
