package com.github.guchengod.projectassistant.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.guchengod.projectassistant.settings.AISettingsState",
    storages = [Storage("AISettingsPlugin.xml")]
)
@Service(Service.Level.PROJECT)
class AISettingsState : PersistentStateComponent<AISettingsState> {
    var apiKey: String = ""
    var selectedModel: String = "DeepSeek-7B"

    override fun getState(): AISettingsState = this

    override fun loadState(state: AISettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val DEFAULT_MODELS = arrayOf("DeepSeek-7B", "DeepSeek-67B")
    }
}
