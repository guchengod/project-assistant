package com.github.guchengod.projectassistant.toolWindow

import com.github.guchengod.projectassistant.services.AIModelService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.*
import java.awt.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.components.JBTextArea
import javax.swing.border.EmptyBorder
import com.intellij.icons.AllIcons
import com.intellij.ui.components.ActionLink
import javax.swing.Box
import com.intellij.util.ui.JBUI
import com.github.guchengod.projectassistant.settings.AISettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import java.awt.event.KeyEvent
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import kotlinx.coroutines.*
import com.intellij.openapi.application.ApplicationManager
import java.awt.event.InputEvent

class MyToolWindowFactory : ToolWindowFactory {
    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {
        private val modelLabel = JBLabel()
        private val settingsState = toolWindow.project.service<AISettingsState>()
        private val modelService = AIModelService()
        private lateinit var messageArea: JBTextArea
        private lateinit var inputField: JBTextArea
        private lateinit var sendButton: ActionLink
        private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

        private fun updateModelLabel() {
            modelLabel.text = "当前模型: ${settingsState.selectedModel}"
        }

        private fun appendMessage(role: String, content: String) {
            ApplicationManager.getApplication().invokeLater {
                messageArea.append("$role: $content\n\n")
                // 滚动到底部
                messageArea.caretPosition = messageArea.document.length
            }
        }

        private fun setInputEnabled(enabled: Boolean) {
            inputField.isEnabled = enabled
            sendButton.isEnabled = enabled
        }

        private fun sendMessage(message: String) {
            if (message.isBlank()) return
            
            setInputEnabled(false)
            appendMessage("用户", message)

            coroutineScope.launch {
                try {
                    val response = modelService.sendMessage(
                        settingsState.baseUrl,
                        settingsState.apiKey,
                        settingsState.selectedModel,
                        message
                    )
                    
                    withContext(Dispatchers.Main) {
                        appendMessage("助手", response)
                        setInputEnabled(true)
                        inputField.requestFocus()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Messages.showErrorDialog(
                            toolWindow.component,
                            "发送消息失败: ${e.message}",
                            "错误"
                        )
                        setInputEnabled(true)
                    }
                }
            }
        }

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout(10, 10)
            border = JBUI.Borders.empty(10)

            // 顶部工具栏面板
            val topPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout(5, 5)
                border = JBUI.Borders.emptyBottom(10)

                // 左侧配置信息
                updateModelLabel()
                add(modelLabel, BorderLayout.WEST)

                // 右侧按钮面板
                val buttonsPanel = JBPanel<JBPanel<*>>().apply {
                    layout = FlowLayout(FlowLayout.RIGHT, 5, 0)

                    // 刷新按钮
                    val actionLinkRefresh = ActionLink("刷新") {
                        updateModelLabel()
                    }
                    actionLinkRefresh.setIcon(AllIcons.Actions.Refresh)
                    add(actionLinkRefresh)

                    // 设置按钮
                    val actionLinkSetting = ActionLink("设置") {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            toolWindow.project,
                            AISettingsConfigurable::class.java
                        )
                    }
                    actionLinkSetting.setIcon(AllIcons.General.Settings)
                    add(actionLinkSetting)
                }
                add(buttonsPanel, BorderLayout.EAST)
            }
            add(topPanel, BorderLayout.NORTH)

            // 聊天面板
            val chatPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout(10, 10)
                
                // 消息显示区域
                messageArea = JBTextArea().apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                }
                add(JBScrollPane(messageArea), BorderLayout.CENTER)
                
                // 输入区域
                val inputPanel = JBPanel<JBPanel<*>>().apply {
                    layout = BorderLayout(5, 5)
                    
                    // 创建包含输入框和发送按钮的面板
                    val inputWithButtonPanel = JBPanel<JBPanel<*>>().apply {
                        layout = BorderLayout(5, 0)
                        
                        inputField = JBTextArea(3, 20).apply {
                            lineWrap = true
                            wrapStyleWord = true
                            
                            // 添加快捷键支持
                            getInputMap(JComponent.WHEN_FOCUSED).put(
                                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                                "send"
                            )
                            actionMap.put("send", object : AbstractAction() {
                                override fun actionPerformed(e: ActionEvent) {
                                    val message = text.trim()
                                    if (message.isNotEmpty()) {
                                        sendMessage(message)
                                        text = ""
                                    }
                                }
                            })
                        }
                        add(JBScrollPane(inputField), BorderLayout.CENTER)
                        
                        // 发送按钮
                        sendButton = ActionLink("") {
                            val message = inputField.text.trim()
                            if (message.isNotEmpty()) {
                                sendMessage(message)
                                inputField.text = ""
                            }
                        }.apply {
                            setIcon(AllIcons.Actions.Execute)
                            toolTipText = "发送 (Ctrl+Enter)"
                        }
                        
                        // 创建一个面板来容纳发送按钮，并设置合适的边距
                        val sendButtonPanel = JBPanel<JBPanel<*>>().apply {
                            layout = GridBagLayout()  // 使用 GridBagLayout 实现完全居中
                            border = JBUI.Borders.empty(5)
                            add(sendButton)
                        }
                        add(sendButtonPanel, BorderLayout.EAST)
                    }
                    
                    add(inputWithButtonPanel, BorderLayout.CENTER)
                }
                add(inputPanel, BorderLayout.SOUTH)
            }
            add(chatPanel, BorderLayout.CENTER)
        }
    }
}

class AISettingsConfigurable(project: Project) : Configurable {
    private var mainPanel: JPanel? = null
    private var apiKeyField: JTextField? = null
    private var modelComboBox: JComboBox<String>? = null
    private var providerComboBox: JComboBox<String>? = null
    private val settingsState = project.service<AISettingsState>()
    private val modelService = AIModelService()

    @NlsContexts.ConfigurableName
    override fun getDisplayName() = "AI Settings"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(10)
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(5)
            }

            // Provider Selection
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.weightx = 0.0
            add(JLabel("Provider:"), gbc)

            providerComboBox = JComboBox(AISettingsState.PROVIDERS).apply {
                selectedItem = settingsState.provider
                addActionListener {
                    // 当提供商改变时清空模型列表
                    modelComboBox?.removeAllItems()
                }
            }
            gbc.gridx = 1
            gbc.gridy = 0
            gbc.weightx = 1.0
            add(providerComboBox, gbc)

            // API Key
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.weightx = 0.0
            add(JLabel("API Key:"), gbc)

            apiKeyField = JTextField(30).apply {
                text = settingsState.apiKey
            }
            gbc.gridx = 1
            gbc.gridy = 1
            gbc.weightx = 1.0
            add(apiKeyField, gbc)

            // Model Selection with Refresh Button
            gbc.gridx = 0
            gbc.gridy = 2
            gbc.weightx = 0.0
            add(JLabel("Model:"), gbc)

            val modelPanel = JPanel(BorderLayout(5, 0))
            modelComboBox = JComboBox<String>().apply {
                selectedItem = settingsState.selectedModel
            }
            modelPanel.add(modelComboBox, BorderLayout.CENTER)

            // 获取模型按钮
            val refreshButton = JButton("获取模型列表").apply {
                addActionListener {
                    try {
                        val apiKey = apiKeyField?.text ?: ""
                        val baseUrl = when (providerComboBox?.selectedItem) {
                            "DeepSeek" -> AIModelService.DEEPSEEK_BASE_URL
                            "OpenAI" -> AIModelService.OPENAI_BASE_URL
                            else -> AIModelService.DEEPSEEK_BASE_URL
                        }
                        
                        val models = modelService.fetchModels(baseUrl, apiKey)
                        modelComboBox?.removeAllItems()
                        models.forEach { model ->
                            modelComboBox?.addItem(model.id)
                        }
                    } catch (e: Exception) {
                        Messages.showErrorDialog(
                            mainPanel,
                            "获取模型列表失败: ${e.message}",
                            "错误"
                        )
                    }
                }
            }
            modelPanel.add(refreshButton, BorderLayout.EAST)

            gbc.gridx = 1
            gbc.gridy = 2
            gbc.weightx = 1.0
            add(modelPanel, gbc)

            // Filler
            gbc.gridx = 0
            gbc.gridy = 3
            gbc.weighty = 1.0
            gbc.gridwidth = 2
            add(Box.createVerticalGlue(), gbc)
        }
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        return apiKeyField?.text != settingsState.apiKey ||
               modelComboBox?.selectedItem != settingsState.selectedModel ||
               providerComboBox?.selectedItem != settingsState.provider
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        apiKeyField?.text?.let { settingsState.apiKey = it }
        modelComboBox?.selectedItem?.let { settingsState.selectedModel = it.toString() }
        providerComboBox?.selectedItem?.let { settingsState.provider = it.toString() }
    }
}
