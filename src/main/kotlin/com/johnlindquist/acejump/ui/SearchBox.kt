package com.johnlindquist.acejump.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.popup.AbstractPopup
import com.johnlindquist.acejump.keycommands.*
import com.johnlindquist.acejump.search.AceFinder
import com.johnlindquist.acejump.search.guessBestLocation
import java.awt.Color.RED
import java.awt.Color.WHITE
import java.awt.Dimension
import java.awt.Font
import java.awt.Font.BOLD
import java.awt.Graphics
import java.awt.event.*
import java.awt.event.KeyEvent.*
import javax.swing.*
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.event.ChangeListener

class SearchBox(val finder: AceFinder, val editor: EditorImpl) : JTextField() {
  var aceCanvas = AceCanvas(editor)
  var keyMap: Map<Int, AceKeyCommand> = hashMapOf()
  var popupContainer: AbstractPopup? = null
  var defaultKeyCommand = DefaultKeyCommand(finder)

  init {
    configureKeyMap()
    configurePopup()

    finder.eventDispatcher.addListener(ChangeListener {
      aceCanvas.jumpInfos = finder.plotJumpLocations()
      if (aceCanvas.jumpInfos.isEmpty() || aceCanvas.jumpInfos.size == 1) {
        popupContainer?.cancel()
        exit()
      }
      aceCanvas.repaint()
    })

    val search = "dispatch"
    (' '..'~').forEach { inputMap.put(getKeyStroke(it), search) }
    actionMap.put(search, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (e.modifiers == 0) {
          text += e.actionCommand
          defaultKeyCommand.execute(e.actionCommand[0], text)
        }
      }
    })

    val backspace = "backspace"
    inputMap.put(getKeyStroke(VK_BACK_SPACE, 0), backspace)
    actionMap.put(backspace, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (text.isNotEmpty()) {
          text = text.substring(0, text.length - 1)
          defaultKeyCommand.execute(0.toChar(), text)
        }
      }
    })

    val targetMode = "targetMode"
    inputMap.put(getKeyStroke(VK_SEMICOLON, CTRL_MASK), targetMode)
    inputMap.put(getKeyStroke(VK_SEMICOLON, META_MASK), targetMode)
    actionMap.put(targetMode, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (finder.toggleTargetMode())
          background = RED
        else
          background = WHITE

        defaultKeyCommand.execute()
      }
    })

    val specials = "specialKeys"
    (VK_HOME..VK_RIGHT).forEach { inputMap.put(getKeyStroke(it, 0), specials) }
    actionMap.put(specials, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) =
        keyMap[e.actionCommand[0].toInt()]!!.execute()
    })

    defaultKeyCommand.execute()
  }

  override fun processKeyEvent(p0: KeyEvent) {
    if (p0.keyCode == VK_BACK_SPACE && p0.id == KEY_RELEASED) {
      defaultKeyCommand.execute(0.toChar(), text)
    }

    super.processKeyEvent(p0)
  }

  private fun configurePopup() {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val font = Font(scheme.editorFontName, BOLD, scheme.editorFontSize)
    val popupBuilder: ComponentPopupBuilder? =
      JBPopupFactory.getInstance()?.createComponentPopupBuilder(this, this)
    popupBuilder?.setCancelKeyEnabled(true)
    val popup = popupBuilder?.createPopup() as AbstractPopup?
    popup?.show(guessBestLocation(editor))
    popup?.setRequestFocus(true)

    val width = getFontMetrics(font).stringWidth("w")
    val dimension = Dimension(width * 2, (editor.lineHeight))
    if (SystemInfo.isMac) {
      dimension.setSize(dimension.width * 2, dimension.height * 2)
    }

    popup?.size = dimension
    size = dimension
    isFocusable = true
    addFocusListener(object : FocusListener {
      override fun focusGained(p0: FocusEvent) = addAceCanvas()
      override fun focusLost(p0: FocusEvent) = exit()
    })
    popupContainer = popup
  }

  private fun configureKeyMap() {
    val showBeginningOfLines = ShowBeginningOfLines(finder)
    val showEndOfLines = ShowEndOfLines(finder)
    keyMap = mapOf(VK_HOME to showBeginningOfLines,
      VK_LEFT to showBeginningOfLines,
      VK_RIGHT to showEndOfLines,
      VK_END to showEndOfLines,
      VK_UP to ShowFirstCharOfLines(finder),
      VK_SPACE to ShowWhiteSpace(finder))
  }

  override fun requestFocus() {
    transferHandler = null
    super.requestFocus()
  }

  override fun paintBorder(p0: Graphics?) = Unit

  fun addAceCanvas() {
    editor.contentComponent.add(aceCanvas)
    val viewport = editor.scrollPane.viewport
    aceCanvas.setBounds(0, 0, viewport.width + 1000, viewport.height + 1000)
    val root: JRootPane = editor.component.rootPane
    val loc = SwingUtilities.convertPoint(aceCanvas, aceCanvas.location, root)
    aceCanvas.setLocation(-loc.x, -loc.y)
  }

  fun exit() {
    val contentComponent = editor.contentComponent
    contentComponent.remove(aceCanvas)
    contentComponent.repaint()
    finder.tagMap.clear()
  }
}