package com.example.smartphonapptest001.data.network

import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenAiRequestMapperTest {
    @Test
    fun requestSerializesExpectedModel() {
        val request = listOf(
            ChatMessage(role = ChatRole.USER, content = "hello"),
        ).toOpenAiRequest()

        assertThat(request.messages.single().content).isEqualTo("hello")
        assertThat(request.stream).isFalse()
    }

    @Test
    fun requestCanEnableStreaming() {
        val request = listOf(
            ChatMessage(role = ChatRole.USER, content = "hello"),
        ).toOpenAiRequest(stream = true)

        assertThat(request.stream).isTrue()
    }
}
