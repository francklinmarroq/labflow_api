package marroquinsoftware.labflowapi.controller.v1;

import jakarta.validation.Valid;
import marroquinsoftware.labflowapi.exceptions.APIException;
import marroquinsoftware.labflowapi.payload.JwtResponse;
import marroquinsoftware.labflowapi.payload.LoginRequest;
import marroquinsoftware.labflowapi.payload.RegisterRequest;
import marroquinsoftware.labflowapi.security.AppUserDetails;
import marroquinsoftware.labflowapi.security.JwtUtils;
import marroquinsoftware.labflowapi.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private RegistrationService registrationService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Bad credentials");
            map.put("status", false);
            return new ResponseEntity<Object>(map, HttpStatus.UNAUTHORIZED);
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        try {
            String jwtToken = jwtUtils.generateToken(userDetails);
            JwtResponse jwtResponse = new JwtResponse(jwtToken, userDetails.getUsername());
            return new ResponseEntity<>(jwtResponse, HttpStatus.OK);
        } catch (Exception e) {
            LOGGER.error("Failed to generate JWT token", e);
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Could not generate token: " + e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        System.out.println("HITTING REGISTER");
        try {
            registrationService.register(request);
        } catch (APIException e) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.CONFLICT);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
            String jwtToken = jwtUtils.generateToken(userDetails);
            return new ResponseEntity<>(new JwtResponse(jwtToken, userDetails.getUsername()), HttpStatus.CREATED);
        } catch (Exception e) {
            LOGGER.error("Failed to authenticate/generate token after registration", e);
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Registration succeeded but token generation failed: " + e.getMessage());
            map.put("status", false);
            return new ResponseEntity<>(map, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
