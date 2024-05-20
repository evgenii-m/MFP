package ru.push.musicfeed.platform.test.component

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.core.io.ClassPathResource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors


fun assertMatchActualResultAndExpectedFile(result: String, filePath: String) {
    val expectedJsonResponse = readFileFromResources(filePath)
    JSONAssert.assertNotEquals("{}", result, true)
    JSONAssert.assertEquals(expectedJsonResponse, result, false)
}

fun assertMatchActualResultAndExpectedFile(actualJsonResponse: String, filePath: String, vararg args: Any) {
    val expectedPattern = readFileFromResources(filePath)
    val expectedJsonResponse = String.format(expectedPattern, *args)
    JSONAssert.assertEquals(expectedJsonResponse, actualJsonResponse, false)
}

fun <T> getMockFromJson(path: String, typeRef: TypeReference<T>): T {
    return try {
        val jsonMockObjectMapper = ObjectMapper()
            .registerModule(SimpleModule())
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        jsonMockObjectMapper.readValue(readFileFromResources(path), typeRef)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
}

fun readFileFromResources(filePath: String): String {
    try {
        ClassPathResource(filePath).inputStream.use { resource ->
            BufferedReader(InputStreamReader(resource, StandardCharsets.UTF_8)).use { reader ->
                return reader.lines().collect(Collectors.joining("\n"))
            }
        }
    } catch (e: IOException) {
        throw IllegalArgumentException(String.format("Failed to read test resource %s !", filePath), e)
    }
}