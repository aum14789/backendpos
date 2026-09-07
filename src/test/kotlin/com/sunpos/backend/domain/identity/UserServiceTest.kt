package com.sunpos.backend.domain.identity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var roleService: RoleService

    @Test
    fun `test create user and authenticate via PIN`() {
        val dto = UserCreateDto(
            companyId = "comp-001",
            username = "cashier_john",
            password = "password123",
            fullName = "John Doe",
            pinCode = "5555"
        )

        val created = userService.createUser(dto)
        assertNotNull(created.id)
        assertEquals("cashier_john", created.username)
        assertTrue(created.hasPin)

        // 1. Authenticate via scoped PIN request
        val pinAuthUser = userService.authenticatePin(PinLoginRequest(pinCode = "5555", username = "cashier_john"))
        assertEquals(created.id, pinAuthUser.id)
        assertEquals("John Doe", pinAuthUser.fullName)

        // 2. Authenticate with PIN code only
        val pinOnlyAuth = userService.authenticatePin(PinLoginRequest(pinCode = "5555"))
        assertEquals(created.id, pinOnlyAuth.id)

        // 3. Invalid PIN throws exception
        assertThrows(IllegalArgumentException::class.java) {
            userService.authenticatePin(PinLoginRequest(pinCode = "9999", username = "cashier_john"))
        }
    }

    @Test
    fun `test duplicate username failure`() {
        val dto = UserCreateDto(
            companyId = "comp-001",
            username = "dup_user",
            password = "password123",
            fullName = "Duplicate User"
        )

        userService.createUser(dto)

        assertThrows(IllegalArgumentException::class.java) {
            userService.createUser(dto)
        }
    }
}
