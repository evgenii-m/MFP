package ru.push.musicfeed.platform.test.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service


@Service
class JdbcExecutor {
    companion object {
        const val CLEAR_SCRIPT_PATH = "db/sql/clear.sql"
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    fun checkResult(scriptPathFile: String, expected: String, vararg args: Any) {
        val actual: String = selectByScriptFile(scriptPathFile, args)
        assertEquals(expected, actual)
    }

    private fun selectByScriptFile(scriptPathFile: String, vararg args: Any): String {
        val sql: String = readFileFromResources(scriptPathFile)
        return select(sql, *args)
    }

    fun select(sql: String, vararg args: Any): String {
        return jdbcTemplate.queryForObject(sql, String::class.java, args)
    }

    fun execute(scriptPathFile: String, vararg args: Any) {
        executeScriptFile(scriptPathFile, *args)
    }

    fun precondition(scriptPathFile: String) {
        executeScriptFile(scriptPathFile)
    }

    fun clear() {
        executeScriptFile(CLEAR_SCRIPT_PATH)
    }

    private fun executeScriptFile(scriptPathFile: String, vararg args: Any) {
        val init: String = readFileFromResources(scriptPathFile)
        jdbcTemplate.update(init, *args)
    }
}