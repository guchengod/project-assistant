package com.github.guchengod.projectassistant.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.github.guchengod.projectassistant.services.AIModelService

@State(
    name = "com.github.guchengod.projectassistant.settings.AISettingsState",
    storages = [Storage("ProjectAssistantSettings.xml")]
)
@Service(Service.Level.APP)
class AISettingsState : PersistentStateComponent<AISettingsState> {
    var apiKey: String = ""
    var selectedModel: String = ""
    var provider: String = "DeepSeek"  // DeepSeek 或 OpenAI
    var proxyType: String = "none"  // 默认为无代理
    var proxyHost: String? = null
    var proxyPort: Int? = null
    
    val baseUrl: String
        get() = when (provider) {
            "DeepSeek" -> AIModelService.DEEPSEEK_BASE_URL
            "OpenAI" -> AIModelService.OPENAI_BASE_URL
            else -> AIModelService.DEEPSEEK_BASE_URL
        }

    override fun getState(): AISettingsState = this

    override fun loadState(state: AISettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val PROVIDERS = arrayOf("DeepSeek", "OpenAI")
    }
}
