package com.github.guchengod.projectassistant.toolWindow

import com.github.guchengod.projectassistant.common.NotificationUtil
import com.github.guchengod.projectassistant.services.AIModelService
import com.github.guchengod.projectassistant.services.AIService
import com.github.guchengod.projectassistant.services.AIServiceConfig
import com.github.guchengod.projectassistant.services.Provider
import com.github.guchengod.projectassistant.settings.AISettingsState
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.text.*

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
        private var aiService: AIService? = null
        private lateinit var messageScrollPane: JBScrollPane
        private lateinit var inputField: JBTextArea
        private lateinit var sendButton: ActionLink
        private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

        private lateinit var messagePane: JTextPane

        private fun initializeAIService() {
            val config = AIServiceConfig(
                provider = when (settingsState.provider) {
                    "DeepSeek" -> Provider.DEEPSEEK
                    "OpenAI" -> Provider.OPENAI
                    else -> Provider.DEEPSEEK
                },
                apiKey = settingsState.apiKey,
                model = settingsState.selectedModel,
                baseUrl = settingsState.baseUrl,
                proxyType = settingsState.proxyType,
                proxyHost = settingsState.proxyHost,
                proxyPort = settingsState.proxyPort
            )
            
            aiService = AIModelService.getInstance().createService(config)
        }

        private fun updateModelLabel() {
            modelLabel.text = "当前模型: ${settingsState.selectedModel}"
        }

        private fun createMessagePane(): JTextPane {
            return JTextPane().apply {
                isEditable = false
                isOpaque = false
                background = null
                border = null
                
                // 使用DefaultStyledDocument而不是HTMLDocument
                document = DefaultStyledDocument()
                
                // 设置基本样式
                val defaultStyle = addStyle("default", null).apply {
                    StyleConstants.setFontFamily(this, JBUI.Fonts.label().family)
                    StyleConstants.setFontSize(this, JBUI.Fonts.label().size)
                    StyleConstants.setForeground(this, JBColor.namedColor(
                        "Label.foreground",
                        JBColor.BLACK
                    ))
                }
                
                // 应用默认样式
                styledDocument.setParagraphAttributes(0, styledDocument.length, defaultStyle, true)
            }
        }

        private fun appendMessage(role: String, content: String) {
            ApplicationManager.getApplication().invokeLater {
                val doc = messagePane.styledDocument

                try {
                    // 添加用户图标（如果是用户消息）
                    if (role == "用户") {
                        val iconStyle = messagePane.addStyle("IconStyle", null)
                        StyleConstants.setIcon(iconStyle, AllIcons.General.User)
                        doc.insertString(doc.length, " ", iconStyle) // 插入一个带图标的空格
                    }
                    
                    // 添加时间
                    val timeStyle = messagePane.addStyle("TimeStyle", null).apply {
                        StyleConstants.setForeground(this, JBColor.namedColor(
                            "Label.infoForeground",
                            JBColor(0x787878, 0x909090)
                        ))
                        StyleConstants.setFontSize(this, JBUI.scaleFontSize(12f))
                    }
                    val currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    val timeText = "\t$currentTime\n"
                    doc.insertString(doc.length, " $currentTime\n", timeStyle)

                    val paragraphStyle = messagePane.addStyle("TimeParagraph", null).apply {
                        val tabStopPos = JBUI.scale(300) // 根据UI缩放调整
                        val tabs = arrayOf(TabStop(tabStopPos.toFloat(), TabStop.ALIGN_RIGHT, TabStop.ALIGN_LEFT))
                        StyleConstants.setTabSet(this, TabSet(tabs))
                    }

                    // 添加消息内容
                    if (role == "用户") {
                        val userStyle = messagePane.addStyle("UserMessage", null).apply {
                            StyleConstants.setForeground(this, JBColor.namedColor(
                                "Label.foreground",
                                JBColor.BLACK
                            ))
                            StyleConstants.setLineSpacing(this, 0.1f)
                        }
                        val startOffset = doc.length
                        doc.insertString(doc.length, "$content\n\n", userStyle)
                        doc.setParagraphAttributes(startOffset, timeText.length, paragraphStyle, false)
                    }

                    val iconStyle = messagePane.addStyle("IconStyle", null)
                    StyleConstants.setIcon(iconStyle, AllIcons.AiAssistant.AiAssistantColored)
                    doc.insertString(doc.length, " ", iconStyle)
                    doc.insertString(doc.length, "\n", null);
                    // 自动滚动到底部
                    messagePane.caretPosition = doc.length
                } catch (e: BadLocationException) {
                    // 处理异常
                }
            }
        }

        private fun appendStreamContent(content: String) {

            fun doAppend() {
                val doc = messagePane.styledDocument
                val style = messagePane.addStyle("StreamContent", null).apply {
                    StyleConstants.setForeground(this, JBColor.namedColor(
                        "Label.foreground",
                        JBColor.BLACK
                    ))
                    StyleConstants.setFontFamily(this, JBUI.Fonts.label().family)
                }

                try {
                    doc.insertString(doc.length, content, style)
                    messagePane.caretPosition = doc.length
                } catch (e: BadLocationException) {
                    // 处理异常
                }
            }

            if (ApplicationManager.getApplication().isDispatchThread) {
                doAppend()
            } else {
                ApplicationManager.getApplication().invokeLater { doAppend() }
            }
        }


        private fun setProcessingState(processing: Boolean) {
            SwingUtilities.invokeLater {
                inputField.isEnabled = !processing
                sendButton.isEnabled = !processing
                sendButton.icon = if (processing) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
                sendButton.repaint()
            }
        }

        private fun sendMessage(message: String) {
            if (message.isBlank()) return
            
            if (aiService == null) {
                initializeAIService()
            }

            appendMessage("用户", message)

            coroutineScope.launch {
                try {
                    aiService?.sendMessage(message) { chunk ->
                        appendStreamContent(chunk)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val doc = messagePane.styledDocument
                        doc.insertString(doc.length, "\n", null);
                        setProcessingState(false)
                        inputField.requestFocus()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Messages.showErrorDialog(
                            toolWindow.component,
                            "发送消息失败: ${e.message}",
                            "错误"
                        )
                        setProcessingState(false)
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
                    val actionLinkRefresh = ActionLink("").apply {
                        icon = AllIcons.Actions.Refresh
                    }
                    actionLinkRefresh.addActionListener {
                        updateModelLabel()
                    }
                    add(actionLinkRefresh)

                    // 设置按钮
                    val actionLinkSetting = ActionLink("").apply {
                        icon = AllIcons.General.Settings
                    }
                    actionLinkSetting.addActionListener {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            toolWindow.project,
                            AISettingsConfigurable::class.java
                        )
                    }
                    add(actionLinkSetting)
                }
                add(buttonsPanel, BorderLayout.EAST)
            }
            add(topPanel, BorderLayout.NORTH)

            // 聊天面板
            val chatPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout(10, 10)

                // ================= 消息显示区域（修改部分） =================
                messagePane = createMessagePane()
                messageScrollPane = JBScrollPane(messagePane).apply {
                    border = null
                    viewport.isOpaque = false
                    setViewportBorder(null)
                    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                }
                add(messageScrollPane, BorderLayout.CENTER)
                
                // 输入区域
                val inputPanel = JBPanel<JBPanel<*>>().apply {
                    layout = BorderLayout(5, 5)
                    
                    // 创建包含输入框和发送按钮的面板
                    val inputWithButtonPanel = JBPanel<JBPanel<*>>().apply {
                        layout = BorderLayout(5, 0)
                        
                        inputField = JBTextArea().apply {
                            lineWrap = true
                            wrapStyleWord = true
                            rows = 1
                            minimumSize = Dimension(0, 24)
                            preferredSize = Dimension(0, 24)
                            margin = JBUI.insets(5)
                            
                            // 自定义边框和焦点效果
                            val cornerRadius = 6
                            val roundedBorder = BorderFactory.createCompoundBorder(
                                object : AbstractBorder() {
                                    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                                        val g2 = g.create() as Graphics2D
                                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                                        g2.color = JBColor.border()
                                        g2.drawRoundRect(x, y, width - 1, height - 1, cornerRadius * 2, cornerRadius * 2)
                                        g2.dispose()
                                    }
                                    
                                    override fun getBorderInsets(c: Component): Insets {
                                        return JBUI.insets(1)
                                    }
                                },
                                BorderFactory.createEmptyBorder(10, 10, 10, 10)
                            )
                            
                            border = roundedBorder
                            
                            // 添加焦点监听器
                            addFocusListener(object : FocusAdapter() {
                                override fun focusGained(e: FocusEvent) {
                                    border = BorderFactory.createCompoundBorder(
                                        object : AbstractBorder() {
                                            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                                                val g2 = g.create() as Graphics2D
                                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                                                g2.color = JBColor.namedColor("Focus.defaultButtonBorderColor", 0x589df6)
                                                g2.drawRoundRect(x, y, width - 1, height - 1, cornerRadius * 2, cornerRadius * 2)
                                                g2.dispose()
                                            }
                                            
                                            override fun getBorderInsets(c: Component): Insets {
                                                return JBUI.insets(1)
                                            }
                                        },
                                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                                    )
                                }
                                
                                override fun focusLost(e: FocusEvent) {
                                    border = roundedBorder
                                }
                            })

                            // 使用主题适配颜色
                            background = JBColor.namedColor(
                                "EditorPane.background",
                            )

                            foreground = JBColor.namedColor(
                                "EditorPane.foreground",
                            )

                            // 添加文本变化监听器来实现自适应高度
                            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                                private fun updateSize() {
                                    val fm = getFontMetrics(font)
                                    val lineHeight = fm.height + 10
                                    val availableWidth = width - insets.left - insets.right
                                    
                                    // 计算实际需要的行数
                                    var totalLines = 0
                                    text.split("\n").forEach { str ->
                                        if (str.isEmpty()) {
                                            totalLines++
                                        } else {
                                            val lineWidth = fm.stringWidth(str)
                                            totalLines += (lineWidth + availableWidth - 1) / availableWidth
                                        }
                                    }
                                    
                                    // 设置新高度，最小1行，最大8行
                                    val newHeight = maxOf(lineHeight, minOf(totalLines * lineHeight, 80 * lineHeight))
                                    if (preferredSize.height != newHeight) {
                                        preferredSize = Dimension(width, newHeight)
                                        revalidate()
                                    }
                                }
                                
                                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = updateSize()
                                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = updateSize()
                                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = updateSize()
                            })

                            // 添加快捷键支持
                            getInputMap(JComponent.WHEN_FOCUSED).put(
                                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
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
                        add(inputField, BorderLayout.CENTER)


                        
                        // 发送按钮
                        sendButton = ActionLink("").apply {
                            icon = AllIcons.Actions.Execute
                            toolTipText = "发送 (Enter)"
                        }
                        sendButton.addActionListener {
                            val message = inputField.text.trim()
                            if (message.isNotEmpty()) {
                                // 先设置为处理中状态
                                setProcessingState(true)
                                
                                // 清空输入框
                                val currentMessage = message
                                inputField.text = ""
                                
                                // 发送消息
                                sendMessage(currentMessage)
                            }
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
    private var balanceLabel: JLabel? = null
    private var proxyTypeGroup: ButtonGroup? = null  // 代理类型单选按钮组
    private var proxyHostField: JTextField? = null
    private var proxyPortField: JTextField? = null
    private var proxyPanel: JPanel? = null  // 用于容纳代理主机和端口的面板
    private val settingsState = project.service<AISettingsState>()
    private lateinit var aiService:AIService

    @NlsContexts.ConfigurableName
    override fun getDisplayName() = "Project Assistant"

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
                    // 当提供商改变时重新加载模型列表
                    loadModels()
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
                    loadModels()
                }
            }
            modelPanel.add(refreshButton, BorderLayout.EAST)

            gbc.gridx = 1
            gbc.gridy = 2
            gbc.weightx = 1.0
            add(modelPanel, gbc)

            // Balance Button and Label
            gbc.gridx = 0
            gbc.gridy = 3
            gbc.weightx = 0.0
            val balanceButton = JButton("查询余额").apply {
                addActionListener {
                    try {
                        initializeAIService()
                        
                        val balance = aiService.fetchBalance()
                        val balanceInfo = balance.balanceInfos.firstOrNull()
                        if (balanceInfo != null) {
                            balanceLabel?.text = "<html>总余额: ${balanceInfo.totalBalance} ${balanceInfo.currency}<br>" +
                                    "赠送余额: ${balanceInfo.grantedBalance} ${balanceInfo.currency}<br>" +
                                    "充值余额: ${balanceInfo.toppedUpBalance} ${balanceInfo.currency}</html>"
                        } else {
                            balanceLabel?.text = "未获取到余额信息"
                        }
                    } catch (e: Exception) {
                        Messages.showErrorDialog(
                            mainPanel,
                            "查询余额失败: ${e.message}",
                            "错误"
                        )
                    }
                }
            }
            add(balanceButton, gbc)

            // Balance display
            balanceLabel = JLabel("点击按钮查询余额")
            gbc.gridx = 1
            gbc.gridy = 3
            gbc.weightx = 1.0
            add(balanceLabel, gbc)

            // 添加代理类型选择（单选按钮）
            gbc.gridx = 0
            gbc.gridy = 4
            gbc.weightx = 0.0
            add(JLabel("代理类型:"), gbc)

            // 创建单选按钮面板
            val radioPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10))
            //radioPanel设置右边距
            proxyTypeGroup = ButtonGroup()
            
            val noneRadio = JRadioButton("无代理", settingsState.proxyType == "none").apply {
                actionCommand = "无代理"  // 设置 actionCommand
            }
            val httpRadio = JRadioButton("HTTP", settingsState.proxyType == "http").apply {
                actionCommand = "HTTP"    // 设置 actionCommand
            }
            val socksRadio = JRadioButton("SOCKS", settingsState.proxyType == "socks").apply {
                actionCommand = "SOCKS"   // 设置 actionCommand
            }
            
            proxyTypeGroup?.add(noneRadio)
            proxyTypeGroup?.add(httpRadio)
            proxyTypeGroup?.add(socksRadio)
            
            radioPanel.add(noneRadio)
            radioPanel.add(httpRadio)
            radioPanel.add(socksRadio)

            gbc.gridx = 1
            gbc.gridy = 4
            gbc.weightx = 1.0
            add(radioPanel, gbc)

            // 创建代理配置面板
            proxyPanel = JPanel(GridBagLayout()).apply {
                val proxyGbc = GridBagConstraints().apply {
                    fill = GridBagConstraints.HORIZONTAL
                    insets = JBUI.insets(5)
                }

                // 代理主机
                proxyGbc.gridx = 0
                proxyGbc.gridy = 0
                proxyGbc.weightx = 0.0
                add(JLabel("代理主机:"), proxyGbc)

                proxyHostField = JTextField(30).apply {
                    text = settingsState.proxyHost
                }
                proxyGbc.gridx = 1
                proxyGbc.gridy = 0
                proxyGbc.weightx = 1.0
                add(proxyHostField, proxyGbc)

                // 代理端口
                proxyGbc.gridx = 0
                proxyGbc.gridy = 1
                proxyGbc.weightx = 0.0
                add(JLabel("代理端口:"), proxyGbc)

                proxyPortField = JTextField(5).apply {
                    text = settingsState.proxyPort?.toString() ?: ""
                }
                proxyGbc.gridx = 1
                proxyGbc.gridy = 1
                proxyGbc.weightx = 1.0
                add(proxyPortField, proxyGbc)
            }

            gbc.gridx = 0
            gbc.gridy = 5
            gbc.gridwidth = 2
            gbc.weightx = 1.0

            add(proxyPanel, gbc)

            // 设置代理面板的初始可见性
            proxyPanel?.isVisible = settingsState.proxyType != "none"

            // 添加单选按钮切换监听器
            val radioListener = ActionListener { e ->
                val selected = (e.source as? JRadioButton)?.text ?: return@ActionListener
                proxyPanel?.isVisible = selected != "无代理"
            }
            
            noneRadio.addActionListener(radioListener)
            httpRadio.addActionListener(radioListener)
            socksRadio.addActionListener(radioListener)

            // Filler
            gbc.gridx = 0
            gbc.gridy = 6
            gbc.weighty = 1.0
            gbc.gridwidth = 2
            add(Box.createVerticalGlue(), gbc)

            // 初始化时加载模型列表
            loadModels()
        }
        return mainPanel!!
    }

    private fun initializeAIService() {
        val config = AIServiceConfig(
            provider = when (settingsState.provider) {
                "DeepSeek" -> Provider.DEEPSEEK
                "OpenAI" -> Provider.OPENAI
                else -> Provider.DEEPSEEK
            },
            apiKey = settingsState.apiKey,
            model = settingsState.selectedModel,
            baseUrl = settingsState.baseUrl,
            proxyType = settingsState.proxyType,
            proxyHost = settingsState.proxyHost,
            proxyPort = settingsState.proxyPort
        )

        aiService = AIModelService.getInstance().createService(config)
    }

    private fun loadModels() {
        try {

            initializeAIService()
            val models = aiService.fetchModels()
            modelComboBox?.removeAllItems()
            models.forEach { model ->
                modelComboBox?.addItem(model.id)
            }
            // 恢复选中的模型
            modelComboBox?.selectedItem = settingsState.selectedModel
        } catch (e: Exception) {
            NotificationUtil.showNotification("获取模型列表失败", e.message ?: "获取模型列表失败", NotificationType.ERROR)
        }
    }

    override fun isModified(): Boolean {
        val selectedProxyType = when (proxyTypeGroup?.selection?.actionCommand) {
            "无代理" -> "none"
            "HTTP" -> "http"
            "SOCKS" -> "socks"
            else -> "none"
        }
        
        return apiKeyField?.text != settingsState.apiKey ||
               modelComboBox?.selectedItem != settingsState.selectedModel ||
               providerComboBox?.selectedItem != settingsState.provider ||
               selectedProxyType != settingsState.proxyType ||
               (selectedProxyType != "none" && (
                   proxyHostField?.text != settingsState.proxyHost ||
                   proxyPortField?.text?.toIntOrNull() != settingsState.proxyPort
               ))
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        apiKeyField?.text?.let { settingsState.apiKey = it }
        modelComboBox?.selectedItem?.let {
            settingsState.selectedModel = it.toString()
        }
        providerComboBox?.selectedItem?.let { settingsState.provider = it.toString() }
        
        // 保存代理设置
        settingsState.proxyType = when (proxyTypeGroup?.selection?.actionCommand) {
            "无代理" -> "none"
            "HTTP" -> "http"
            "SOCKS" -> "socks"
            else -> "none"
        }
        
        // 只有在非"无代理"模式下才保存代理配置
        if (settingsState.proxyType != "none") {
            settingsState.proxyHost = proxyHostField?.text?.takeIf { it.isNotBlank() }
            settingsState.proxyPort = proxyPortField?.text?.toIntOrNull()
        } else {
            settingsState.proxyHost = null
            settingsState.proxyPort = null
        }
    }
}
