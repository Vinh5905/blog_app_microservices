package com.pnimac.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pnimac.auth.dto.UserDTO;
import com.pnimac.auth.error.ApiException;
import com.pnimac.auth.model.User;
import com.pnimac.auth.model.repository.UserRepository;
import com.pnimac.auth.model.service.CustomUserDetailsService;
import com.pnimac.auth.request.LoginRequest;
import com.pnimac.auth.request.RegisterRequest;
import com.pnimac.auth.response.LoginResponse;
import com.pnimac.auth.util.JwtUtil;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

	@Autowired
	private final AuthenticationManager authenticationManager;

	@Autowired
	private final CustomUserDetailsService userDetailsService;

	@Autowired
	private final JwtUtil jwtUtil;

	@Autowired
	private final PasswordEncoder passwordEncoder;

	@Autowired
	private final UserRepository userRepository;

	@Autowired
	public AuthenticationController(AuthenticationManager authenticationManager,
			CustomUserDetailsService userDetailsService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
			UserRepository userRepository) {
		this.authenticationManager = authenticationManager;
		this.userDetailsService = userDetailsService;
		this.jwtUtil = jwtUtil;
		this.passwordEncoder = passwordEncoder;
		this.userRepository = userRepository;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
	    try {
	        authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));	 
	        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
			final String token = jwtUtil.generateToken(userDetails);
			return ResponseEntity.ok(new LoginResponse(token, userDetails.getUsername()));
	    } catch (AuthenticationException e) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Incorrect username or password");
		}
	}

	@PostMapping("/signup")
	public ResponseEntity<UserDTO> signup(@Valid @RequestBody RegisterRequest registerRequest) throws Exception {
		// Check if user already exists
		if (userRepository.findByUsername(registerRequest.getUsername()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "Username already exists");
		}
		if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already exists");
		}

		// Create new user
		User user = new User();
		user.setUsername(registerRequest.getUsername());
		user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
		user.setEmail(registerRequest.getEmail());
		User saved = userDetailsService.save(user);
		return ResponseEntity.status(HttpStatus.CREATED).body(UserDTO.fromEntity(saved));
	}

	@GetMapping("/user")
	public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
		User user = userRepository.findByUsername(authentication.getName())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "User not found"));
		return ResponseEntity.ok(UserDTO.fromEntity(user));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(Authentication authentication) {
		return ResponseEntity.noContent().build();
	}
}
