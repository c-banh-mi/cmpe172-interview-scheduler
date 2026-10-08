package edu.sjsu.cmpe172.scheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sjsu.cmpe172.scheduler.dto.ErrorResponse;
import edu.sjsu.cmpe172.scheduler.service.DtoMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Session-based login and role-based access control.
 *
 * <ul>
 *   <li>POST /api/auth/login (form fields username, password) checks the BCrypt hash and
 *       stores the user in the server-side HTTP session (cookie JSESSIONID).</li>
 *   <li>/api/customer/** needs ROLE_CUSTOMER, /api/provider/** needs ROLE_PROVIDER.
 *       Not logged in: 401. Logged in with the wrong role: 403.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final ObjectMapper json;

    public SecurityConfig(ObjectMapper json) {
        this.json = json;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/provider/**").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.GET, "/api/home", "/api/slots", "/api/slots/*", "/api/services", "/api/providers")
                                .permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/app.css", "/favicon.ico")
                                .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> {
                            loadCsrfToken(req);
                            UserPrincipal p = (UserPrincipal) auth.getPrincipal();
                            writeJson(res, HttpStatus.OK, DtoMapper.toDto(p.user()));
                        })
                        .failureHandler((req, res, ex) ->
                                writeError(req, res, HttpStatus.UNAUTHORIZED, "Invalid username or password")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(req, res, HttpStatus.UNAUTHORIZED, "Please log in"))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(req, res, HttpStatus.FORBIDDEN, "Your role is not allowed to do this")))
                // CSRF for a JavaScript frontend: the token is sent as a readable cookie XSRF-TOKEN,
                // and the page echoes it back in the X-XSRF-TOKEN header on POST/DELETE.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        writeJson(res, status, new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }

    private void writeJson(HttpServletResponse res, HttpStatus status, Object body) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), body);
    }

    /** Reading the token makes Spring write the XSRF-TOKEN cookie on this response. */
    private static void loadCsrfToken(HttpServletRequest req) {
        CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
    }

    /** Ensures every response carries a current XSRF-TOKEN cookie (the token is lazy by default). */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            loadCsrfToken(req);
            chain.doFilter(req, res);
        }
    }
}
