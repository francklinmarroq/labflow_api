package marroquinsoftware.labflowapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import marroquinsoftware.labflowapi.repositories.LabOrderRepository;
import marroquinsoftware.labflowapi.repositories.UserRepository;
import marroquinsoftware.labflowapi.tenant.TenantContext;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthTokenFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthTokenFilter.class);

    // Endpoints públicos de invitación: /api/v1/auth/invitation/{token}[/accept]
    private static final Pattern INVITATION_PATH = Pattern.compile("/api/v1/auth/invitation/([^/]+)");
    // Reporte público de resultados: /api/v1/public/orders/{token}
    private static final Pattern PUBLIC_REPORT_PATH = Pattern.compile("/api/v1/public/orders/([^/]+)");

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LabOrderRepository labOrderRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        LOGGER.debug("AuthTokenFilter called for URI: {}", request.getRequestURI());
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUsernameFromJwtToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (userDetails instanceof AppUserDetails appUser) {
                    TenantContext.setLaboratoryId(appUser.getLaboratoryId());
                }
                LOGGER.debug("Roles from JWT: {}", userDetails.getAuthorities());
            } else {
                // Petición pública de invitación: no hay JWT ni tenant. Se resuelve
                // el laboratorio del invitado desde el token y se fija el
                // TenantContext ANTES de que se abra la sesión de la petición
                // (OSIV), para que el AppRole del invitado (que es @TenantId) se
                // pueda cargar sin fallar.
                resolveInvitationTenant(request);
                // Mismo caso con el reporte público: el laboratorio se resuelve del
                // token de la orden para que las consultas filtradas por @TenantId
                // devuelvan sus datos.
                resolvePublicReportTenant(request);
            }
        } catch (Exception e) {
            LOGGER.error("Cannot set user authentication: {}", e.getMessage());
        } finally {
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void resolveInvitationTenant(HttpServletRequest request) {
        Matcher matcher = INVITATION_PATH.matcher(request.getRequestURI());
        if (!matcher.find()) {
            return;
        }
        String rawToken = matcher.group(1);
        userRepository.findLaboratoryIdByInvitationTokenHash(InvitationTokens.hash(rawToken))
                .ifPresent(TenantContext::setLaboratoryId);
    }

    private void resolvePublicReportTenant(HttpServletRequest request) {
        Matcher matcher = PUBLIC_REPORT_PATH.matcher(request.getRequestURI());
        if (!matcher.find()) {
            return;
        }
        String token = matcher.group(1);
        labOrderRepository.findLaboratoryIdByPublicToken(token)
                .ifPresent(TenantContext::setLaboratoryId);
    }

    private String parseJwt(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromHeader(request);
        LOGGER.debug("AuthTokenFilter.java: {}", jwt);
        return jwt;
    }
}
