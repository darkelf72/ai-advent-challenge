fun main(args: Array<String>) {
    val apiClient = ApiClient()
    val question = args.joinToString(" ")
    println("Your question: $question")
    val result = apiClient.sendRequest(question)
    println("Answer: $result")
}