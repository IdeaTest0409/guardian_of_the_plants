package com.example.smartphonapptest001.viewmodel

import com.example.smartphonapptest001.data.SettingsRepository
import com.example.smartphonapptest001.data.logging.AppLogEntry
import com.example.smartphonapptest001.data.logging.AppLogSeverity
import com.example.smartphonapptest001.data.logging.AppLogger
import com.example.smartphonapptest001.data.model.AppSettings
import com.example.smartphonapptest001.data.model.ChatMessage
import com.example.smartphonapptest001.data.model.ChatRole
import com.example.smartphonapptest001.data.model.EndpointConfig
import com.example.smartphonapptest001.data.repository.ChatRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_addsUserMessageAndAssistantReply() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val repository = FakeChatRepository("hello back")
        val viewModel = ChatViewModel(
            repository = repository,
            settingsRepository = FakeSettingsRepository(settingsFlow),
            logger = FakeLogger(),
        )

        viewModel.onMessageChange("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.messages.any { it.role == ChatRole.USER && it.content == "hello" }).isTrue()
        assertThat(state.messages.any { it.role == ChatRole.ASSISTANT && it.content == "hello back" }).isTrue()
        assertThat(state.isSending).isFalse()
    }
}

private class FakeSettingsRepository(
    private val flow: MutableStateFlow<AppSettings>,
) : SettingsRepository {
    override val settings = flow
    override suspend fun save(settings: AppSettings) {
        flow.value = settings
    }
}

private class FakeChatRepository(
    private val response: String,
) : ChatRepository {
    override suspend fun complete(messages: List<ChatMessage>, config: EndpointConfig): String = response
    override fun stream(messages: List<ChatMessage>, config: EndpointConfig): Flow<String> = flowOf(response)
}

private class FakeLogger : AppLogger {
    override val entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    override fun log(severity: AppLogSeverity, tag: String, message: String, details: String?) = Unit
    override fun clear() = Unit
    override fun exportText(): String = ""
}
