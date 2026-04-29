package com.example.smartphonapptest001.data.model

sealed interface AIProvider {
    val type: ProviderType
    val endpointConfig: EndpointConfig
    val modelConfig: ModelConfig

    data class Local(
        override val endpointConfig: EndpointConfig,
        override val modelConfig: ModelConfig,
    ) : AIProvider {
        override val type: ProviderType = ProviderType.LOCAL
    }

    data class Cloud(
        override val type: ProviderType = ProviderType.CLOUD,
        override val endpointConfig: EndpointConfig,
        override val modelConfig: ModelConfig,
    ) : AIProvider
}
