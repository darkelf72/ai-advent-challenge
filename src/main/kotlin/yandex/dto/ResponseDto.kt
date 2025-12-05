package yandex.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResponseDto(
    val result: ResultDto
)

@Serializable
data class ResultDto(
    val alternatives: List<AlternativeDto>,
    val usage: UsageDto,
    val modelVersion: String
)

@Serializable
data class AlternativeDto(
    val message: MessageDto,
    val status: String
)

@Serializable
data class UsageDto(
    val inputTextTokens: String,
    val completionTokens: String,
    val totalTokens: String,
    val completionTokensDetails: CompletionTokensDetailsDto
)

@Serializable
data class CompletionTokensDetailsDto(
    val reasoningTokens: String
)