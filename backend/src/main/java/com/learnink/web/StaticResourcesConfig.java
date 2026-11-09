package com.learnink.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Mise en cache du frontend. La directive s-maxage permet au CDN de l'hébergeur (Vercel) de servir ces fichiers
 * sans solliciter l'application ; elle est ignorée par les navigateurs.
 */
@Configuration
public class StaticResourcesConfig implements WebMvcConfigurer {

    /** Les fichiers de /assets portent un hash dans leur nom : ils peuvent être mis en cache indéfiniment. */
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365))
            .sMaxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();

    /**
     * index.html change à chaque déploiement : le navigateur revalide toujours, le CDN le garde une minute
     * et peut servir l'ancienne version pendant qu'il récupère la nouvelle.
     */
    private static final CacheControl ENTRY_POINT = CacheControl.maxAge(Duration.ZERO)
            .mustRevalidate()
            .cachePublic()
            .sMaxAge(Duration.ofMinutes(1))
            .staleWhileRevalidate(Duration.ofMinutes(5));

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(IMMUTABLE);
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(ENTRY_POINT);
    }
}
