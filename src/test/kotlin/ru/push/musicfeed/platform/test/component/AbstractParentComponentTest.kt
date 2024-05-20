package ru.push.musicfeed.platform.test.component

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.reflect.Method
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer


@ActiveProfiles("component-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractParentComponentTest() {
    @Autowired
    lateinit var jdbcExecutor: JdbcExecutor

    internal val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .apply {
            findAndRegisterModules()
        }

    protected var testName: String? = null

    companion object {
        val container = PostgreSQLContainer<Nothing>("postgres:12").apply {
            withDatabaseName("test")
            withUsername("test")
            withPassword("test")
            withInitScript(JdbcExecutor.CLEAR_SCRIPT_PATH)
            withReuse(true)
        }

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            container.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl)
            registry.add("spring.datasource.password", container::getPassword)
            registry.add("spring.datasource.username", container::getUsername)
        }
    }

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        this.testName = testInfo.testMethod.map(Method::getName).orElse("")
    }

    @AfterEach
    fun tearDown() {
        jdbcExecutor.clear()
    }
}