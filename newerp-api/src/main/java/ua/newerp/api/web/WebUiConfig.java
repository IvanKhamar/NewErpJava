package ua.newerp.api.web;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the web UI under /app/, like MapStaticAssets + MapFallbackToFile("app/{*path:nonfile}", "app/index.html") in .NET.
 * The UI is the published Blazor WebAssembly app (NewErp.Web): its wwwroot folder is set in newerp.web-root.
 * Without the setting there is no UI, only the API. The files are open without login (see SecurityConfig),
 * otherwise the login page would not load; the data is protected by the API anyway.
 */
@Configuration
@ConditionalOnProperty("newerp.web-root")
class WebUiConfig implements WebMvcConfigurer {

    private final Path root;

    WebUiConfig(@Value("${newerp.web-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/");
        // The folder itself is not a file the resource handler can return, so /app/ is forwarded to index.html
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations(new FileSystemResource(root.toString() + "/"))
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /**
     * A page address of the UI (/app/orders/5) is not a file: index.html is returned and Blazor shows the page itself.
     * A missing address with a file extension (/app/missing.js) stays 404, as "nonfile" in .NET.
     */
    private final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource file = super.getResource(resourcePath, location);
            if (file != null) {
                return file;
            }
            boolean looksLikeFile = resourcePath.substring(resourcePath.lastIndexOf('/') + 1).contains(".");
            Resource index = new FileSystemResource(root.resolve("index.html"));
            return !looksLikeFile && index.isReadable() ? index : null;
        }
    }
}
