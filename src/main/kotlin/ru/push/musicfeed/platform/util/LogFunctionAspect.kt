package ru.push.musicfeed.platform.util

import mu.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LogFunction(
    val displayResult: Boolean = true
)

@Aspect
@Component
class LogFunctionAspect {

    private val logger = KotlinLogging.logger {}

    @Around("@annotation(LogFunction)")
    fun doBasicProfiling(pjp: ProceedingJoinPoint): Any? {
        val start = System.currentTimeMillis()
        val methodSignature = pjp.signature as MethodSignature
        val methodName = "${pjp.target.javaClass.name}.${methodSignature.name}"
        val annotation = methodSignature.method.annotations
            .find { it is LogFunction } as LogFunction
        val args = pjp.args.joinToString(", ")
        logger.debug { "Called: [$methodName], args: [$args]" }

        try {
            val result = pjp.proceed()
            logger.debug {
                "Successfully completed: [$methodName], time: [${System.currentTimeMillis() - start}ms]" +
                        if (annotation.displayResult) ", result: [$result]" else ""
            }
            return result
        } catch (ex: Throwable) {
            logger.debug { "Throws exception: [$methodName], time: [${System.currentTimeMillis() - start}ms], ex: [$ex]" }
            throw ex
        }
    }

}