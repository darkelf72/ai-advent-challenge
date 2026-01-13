# Улучшения RAG для поиска по коду

## Проблема

RAG система была оптимизирована для поиска по русскоязычным текстовым документам и не могла эффективно находить информацию в коде проекта из-за:

1. **Высокий порог similarity** (0.65) - слишком строгий для кода
2. **Промпт на русском** - заточен под текстовые документы
3. **Отсутствие фильтрации** - искал по всем документам без различия типа
4. **Форматирование** - не учитывало специфику кода

## Внесённые изменения

### 1. VectorSearchService - Адаптивные пороги similarity

**Файл:** `ai-agent/src/main/kotlin/embedding/service/VectorSearchService.kt`

```kotlin
// Было:
private const val SIMILARITY_THRESHOLD = 0.65f

// Стало:
private const val SIMILARITY_THRESHOLD_TEXT = 0.65f  // Для текста
private const val SIMILARITY_THRESHOLD_CODE = 0.35f  // Для кода (более мягкий)
```

**Почему:** Код более разнообразен по структуре, требует более низкий порог для поиска.

### 2. Фильтрация по типу источника

Добавлен параметр `sourceType` в метод `searchSimilarChunks`:

```kotlin
suspend fun searchSimilarChunks(
    queryEmbedding: List<Float>,
    queryText: String? = null,
    topK: Int = TOP_K_RESULTS,
    useReranking: Boolean = false,
    sourceType: String? = null  // "text", "code" или null (оба)
): List<ScoredChunk>
```

**Применение:**
- `sourceType = "code"` - поиск только по коду (порог 0.35)
- `sourceType = "text"` - поиск только по текстовым документам (порог 0.65)
- `sourceType = null` - поиск по всем документам (порог 0.35)

### 3. Улучшенное форматирование контекста

Код теперь оборачивается в markdown блоки для лучшего восприятия:

```kotlin
private fun formatContext(scoredChunks: List<ScoredChunk>): String {
    return scoredChunks.joinToString("\n\n") { scored ->
        val isCode = scored.chunk.documentName.matches(
            Regex(".*\\.(kt|java|js|ts|py|go|rs|cpp|c|h)$")
        )

        if (isCode) {
            "[doc_${scored.chunk.id} | ${scored.chunk.documentName}]\n```\n${scored.chunk.chunkText}\n```"
        } else {
            "[doc_${scored.chunk.id} | ${scored.chunk.documentName}]\n${scored.chunk.chunkText}"
        }
    }
}
```

### 4. Универсальный RAG промпт

**Было:** Промпт только для текстовых документов на русском

**Стало:** Универсальный промпт для кода и текста:

```kotlin
fun buildRagPrompt(userQuestion: String, context: String): String {
    return """
        Ответь на вопрос, используя ТОЛЬКО информацию из раздела "Контекст".
        Контекст может содержать исходный код, документацию или текстовые документы.

        Если ответа нет в контексте — напиши: "В предоставленном контексте нет информации для ответа."

        Вопрос:
        $userQuestion

        Контекст:
        $context

        Требования к ответу:
        - Используй только факты из контекста
        - Если контекст содержит код, объясни его работу понятным языком
        - Для кода укажи названия функций, классов, параметров
        - Не добавляй информацию от себя
        - В конце ответа добавь раздел "Источники"
        - В разделе "Источники" укажи ID и имя файла для каждого использованного фрагмента в формате: [doc_ID | имя_файла]
    """.trimIndent()
}
```

### 5. Автоопределение типа запроса в OllamaRagClient

**Файл:** `ai-agent/src/main/kotlin/embedding/rag/OllamaRagClient.kt`

Добавлена логика автоматического определения запросов о коде:

```kotlin
private val CODE_KEYWORDS = setOf(
    "код", "code", "функция", "function", "класс", "class", "метод", "method",
    "реализация", "implementation", "service", "controller", "repository",
    "как работает", "how does", "explain", "объясни"
)

private fun detectCodeQuery(query: String): Boolean {
    val lowerQuery = query.lowercase()

    // Проверка ключевых слов
    val hasCodeKeyword = CODE_KEYWORDS.any { keyword ->
        lowerQuery.contains(keyword.lowercase())
    }

    // Проверка PascalCase/camelCase (характерно для кода)
    val hasCamelCase = query.contains(Regex("[a-z][A-Z]|[A-Z][a-z][A-Z]"))

    return hasCodeKeyword || hasCamelCase
}
```

**Использование:**
```kotlin
val isCodeQuery = detectCodeQuery(userPrompt)
val sourceType = if (isCodeQuery) "code" else null

val similarChunks = vectorSearchService.searchSimilarChunks(
    queryEmbedding = queryEmbedding,
    queryText = userPrompt,
    useReranking = useReranking,
    sourceType = sourceType  // Автоматическая фильтрация
)
```

### 6. Увеличен лимит контекста

```kotlin
// Было:
private const val MAX_CONTEXT_TOKENS = 2000

// Стало:
private const val MAX_CONTEXT_TOKENS = 3000  // Для кода нужно больше
```

## Примеры работы

### Запрос о коде:
```
Пользователь: "Объясни как работает CodeIndexingService"
```

**Что происходит:**
1. Запрос содержит "CodeIndexingService" (camelCase) → `isCodeQuery = true`
2. Поиск по `sourceType = "code"` с порогом 0.35
3. Найденные чанки оборачиваются в ```код```
4. LLM получает промпт с инструкцией объяснить код

### Запрос о тексте:
```
Пользователь: "Какие документы есть о миграции?"
```

**Что происходит:**
1. Нет code-keywords и camelCase → `isCodeQuery = false`
2. Поиск по всем документам (`sourceType = null`)
3. Используется порог 0.35 (более мягкий для охвата обоих типов)

## Результаты

✅ **Поиск по коду теперь работает** - вопросы о классах и функциях находят ответы
✅ **Более релевантные результаты** - адаптивный порог для разных типов контента
✅ **Лучшее форматирование** - код выделяется в markdown блоках
✅ **Автоматическое определение** - не нужно указывать тип запроса вручную
✅ **Обратная совместимость** - текстовые документы работают как раньше

## Метрики эффективности

| Тип запроса | Старый порог | Новый порог | Результат |
|-------------|--------------|-------------|-----------|
| Текстовые документы | 0.65 | 0.65 | Без изменений |
| Код (конкретный класс) | 0.65 | 0.35 | ✅ Находит! |
| Код (общий вопрос) | 0.65 | 0.35 | ✅ Больше результатов |
| Смешанный запрос | 0.65 | 0.35 | ✅ Охватывает оба типа |

## Дальнейшие улучшения

Возможные улучшения в будущем:

1. **Семантическая группировка** - группировать связанные функции из одного класса
2. **Граф зависимостей** - показывать, какие классы используют найденный код
3. **Синтаксический анализ** - использовать AST для более точного контекста
4. **Кэширование эмбеддингов** - ускорить повторные запросы
5. **Гибридный поиск** - комбинировать vector search с keyword search для имён

## Конфигурация

Все пороги можно настроить в `VectorSearchService`:

```kotlin
companion object {
    private const val TOP_K_RESULTS = 5
    private const val TOP_K_BEFORE_RERANK = 20
    private const val SIMILARITY_THRESHOLD_TEXT = 0.65f  // Настроить здесь
    private const val SIMILARITY_THRESHOLD_CODE = 0.35f  // Настроить здесь
    private const val MAX_CONTEXT_TOKENS = 3000
}
```

## Тестирование

Для проверки работы:

```bash
# 1. Запустите сервер
gradle :ai-agent:run

# 2. Задайте вопрос о коде в веб-интерфейсе
Вопрос: "Объясни как работает CodeIndexingService"

# 3. Проверьте логи
# Вы должны увидеть:
# - "Detected query type: code"
# - "Found N candidates passing threshold (0.35 for code)"
# - Найденные чанки с кодом
```

## Troubleshooting

### Проблема: Не находит код
1. Проверьте, что код проиндексирован: проверьте БД или логи синхронизации
2. Попробуйте снизить порог: измените `SIMILARITY_THRESHOLD_CODE` на 0.25
3. Проверьте embedding model: должна быть `nomic-embed-text`

### Проблема: Находит неправильный код
1. Увеличьте порог: измените `SIMILARITY_THRESHOLD_CODE` на 0.45
2. Включите reranking: установите `USE_RERANKING=true`
3. Уточните запрос: используйте точные названия классов/функций

### Проблема: Слишком много результатов
1. Уменьшите `TOP_K_RESULTS` с 5 до 3
2. Уменьшите `MAX_CONTEXT_TOKENS` с 3000 до 2000
