package com.wafflestudio.seminar.domain.user.api

import com.wafflestudio.seminar.domain.user.model.User
import com.wafflestudio.seminar.domain.user.repository.UserRepository
import com.wafflestudio.seminar.domain.user.service.UserService
import com.wafflestudio.seminar.domain.user.dto.UserDto
import com.wafflestudio.seminar.domain.user.exception.UserNotFoundException
import com.wafflestudio.seminar.global.auth.CurrentUser
import com.wafflestudio.seminar.global.auth.JwtTokenProvider
import com.wafflestudio.seminar.global.auth.dto.LoginRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import javax.validation.Valid

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {
    @PostMapping("/")
    fun signup(@Valid @RequestBody signupRequest: UserDto.SignupRequest): ResponseEntity<UserDto.Response> {
        val user = userService.signup(signupRequest)
        return ResponseEntity.created(URI.create("/"))
            .header("Authentication", jwtTokenProvider.generateToken(user.email))
            .body(UserDto.Response(user))
    }

    @PostMapping("/signin/")
    fun login(@RequestBody loginRequest: LoginRequest): Unit {}

    @GetMapping("/{user_id}/")
    fun getUserById(@PathVariable("user_id") user_id: Long): UserDto.Response {
        val user = userRepository.findByIdOrNull(user_id) ?: throw UserNotFoundException("NO USER")
        return UserDto.Response(user)
    }

    @GetMapping("/me/")
    fun getCurrentUser(@CurrentUser user: User): UserDto.Response {
        return UserDto.Response(user)
    }
}