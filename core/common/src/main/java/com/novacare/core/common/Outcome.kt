package com.novacare.core.common

/**
 * 统一结果类型
 *
 * 为什么不用标准库的 Result：Result 无法作为返回值类型携带「用户可读的错误原因」，
 * 而 NovaCare 的核心原则之一是「失败必须如实告知原因」，
 * 因此这里用一个显式携带 AppError 的密封类型。
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

data class AppError(
    val code: String,
    val message: String,
    /** 是否可以给用户一个「去处理」的入口（如去设置页授权） */
    val recoverable: Boolean = false,
)

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value

inline fun <T> runOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Ok(block())
} catch (t: Throwable) {
    Outcome.Err(AppError("UNKNOWN", t.message ?: t::class.java.simpleName))
}
