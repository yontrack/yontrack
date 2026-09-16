package net.nemerosa.ontrack.boot.support

import jakarta.servlet.DispatcherType
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val ontrackConfigProperties: OntrackConfigProperties,
) : WebMvcConfigurer {

    /**
     * Uses the HTTP header for content negociation.
     */
    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer.favorParameter(false)
    }

    /**
     * ETag support
     */
    @Bean
    fun shallowEtagHeaderFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> {
        val registration = FilterRegistrationBean<ShallowEtagHeaderFilter>()
        registration.filter = ShallowEtagHeaderFilter()
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC)
        registration.addUrlPatterns("/*")
        return registration
    }

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.clear()
        // Byte arrays
        converters.add(ByteArrayHttpMessageConverter())
        // Plain text
        converters.add(StringHttpMessageConverter())
        // Documents
        converters.add(DocumentHttpMessageConverter())
        // JSON
        converters.add(MappingJackson2HttpMessageConverter())
    }

    /**
     * CORS on the API, same-origin only unless origins are listed in
     * `ontrack.config.security.cors.allowed-origins` (#1771).
     *
     * With no origin listed, no mapping is registered at all rather than a mapping allowing no
     * origin: the browser then refuses any cross-origin call by itself, since no answer carries an
     * `Access-Control-Allow-Origin`, while a non-browser client which happens to send an `Origin`
     * (a scanner, a proxy) is still served instead of getting a `403 Invalid CORS request`.
     *
     * The hooks (`/hook/secured/...`) are never mapped: they are called server-to-server.
     *
     * The same settings apply to the preflights, which Spring Security answers before
     * authentication - see `WebSecurityConfig`.
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOrigins = ontrackConfigProperties.security.cors.allowedOrigins
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (allowedOrigins.isNotEmpty()) {
            CORS_API_PATHS.forEach {
                registry.addMapping(it)
                    .allowedOrigins(*allowedOrigins.toTypedArray())
                    .allowedMethods(*ALLOWED_API_METHODS.toTypedArray())
            }
        }
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/ui").setViewName("forward:/ui/index.html")
    }

    companion object {
        private val ALLOWED_API_METHODS = setOf("GET", "POST", "PUT", "DELETE", "HEAD")

        private val CORS_API_PATHS = listOf(
            "/graphql/**",
            "/rest/**",
            "/extension/**",
        )
    }

}
