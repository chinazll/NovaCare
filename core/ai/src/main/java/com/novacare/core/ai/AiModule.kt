package com.novacare.core.ai

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSemanticAnalyzer(impl: RuleBasedSemanticAnalyzer): SemanticAnalyzer

    @Binds
    @Singleton
    abstract fun bindIntentParser(impl: RuleBasedIntentParser): IntentParser
}

@Module
@InstallIn(SingletonComponent::class)
object AiProviderModule {

    /** 云端配置可在运行时由设置页更新；默认空 = 不联网 */
    @Volatile
    var cloudConfig: CloudLlmConfig = CloudLlmConfig()
        private set

    fun updateCloudConfig(config: CloudLlmConfig) {
        cloudConfig = config
    }

    @Provides
    @Singleton
    fun provideCloudLlmProvider(): CloudLlmProvider = OpenAiCompatibleLlmProvider { cloudConfig }

    @Provides
    @Singleton
    fun provideLlmIntentParser(
        llm: CloudLlmProvider,
        ruleBased: RuleBasedIntentParser,
    ): LlmIntentParser = LlmIntentParser(llm, ruleBased)
}
