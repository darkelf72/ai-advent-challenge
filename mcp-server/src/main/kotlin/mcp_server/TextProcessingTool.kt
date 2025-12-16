package mcp_server

class TextProcessingTool {
    fun process(input: TextInput): String {
        // Пример простой обработки: возвращаем длину текста и его в верхнем регистре
        return "Length: ${input.text.length}, Uppercase: ${input.text.uppercase()}"
    }
}