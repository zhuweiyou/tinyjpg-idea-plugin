package com.zhuweiyou.tinyjpgplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import java.io.OutputStream
import java.io.PrintStream
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException

/**
 * A simple console-like text pane shown in a ToolWindow.
 * Provides a PrintStream for writing log output.
 */
class TinyJpgConsole private constructor(private val textPane: JTextPane) {

    private val outPS = PrintStream(ConsoleOutputStream(), false, Charsets.UTF_8)

    val outputStream: OutputStream get() = ConsoleOutputStream()
    val printStream: PrintStream get() = outPS

    /** Clear all text. */
    fun clear() {
        SwingUtilities.invokeLater { textPane.text = "" }
    }

    /** Append text to the console. */
    fun print(text: String) {
        SwingUtilities.invokeLater {
            try {
                val doc = textPane.document
                doc.insertString(doc.length, text, null)
                val caret = textPane.caret
                caret.dot = doc.length
            } catch (e: BadLocationException) {
                // ignore
            }
        }
    }

    fun println(text: String) = print("$text\n")

    private inner class ConsoleOutputStream : OutputStream() {
        private val buf = StringBuilder()
        override fun write(b: Int) {
            buf.append(b.toChar())
            if (b.toChar() == '\n') {
                flush()
            }
        }
        override fun flush() {
            if (buf.isNotEmpty()) {
                print(buf.toString())
                buf.clear()
            }
        }
    }

    companion object {
        private const val TOOLWINDOW_ID = "TinyJPG Console"

        fun get(project: Project): TinyJpgConsole {
            val manager = ToolWindowManager.getInstance(project)
            var tw = manager.getToolWindow(TOOLWINDOW_ID)

            if (tw == null) {
                val textPane = JTextPane().apply {
                    isEditable = false
                    contentType = "text/plain"
                }
                val scrollPane = JScrollPane(textPane)
                val console = TinyJpgConsole(textPane)

                // registerToolWindow must be called on EDT
                ApplicationManager.getApplication().invokeAndWait {
                    tw = manager.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.BOTTOM, true)
                    tw!!.title = "TinyJPG Console"
                    tw!!.setIcon(com.intellij.icons.AllIcons.Toolwindows.ToolWindowMessages)

                    val newContent = ContentFactory.getInstance().createContent(scrollPane, "", false)
                    tw!!.contentManager.addContent(newContent)
                }

                return console
            }

            // Find existing console from the tool window's content
            val existingContent = tw.contentManager.getContent(0)
            if (existingContent != null) {
                val component = existingContent.component
                if (component is JScrollPane) {
                    val view = component.viewport.view
                    if (view is JTextPane) {
                        return TinyJpgConsole(view)
                    }
                }
            }

            // Fallback: create fresh (already on EDT from invokeAndWait above if first time,
            // but on subsequent calls we may be on a background thread, so wrap again)
            val textPane = JTextPane().apply {
                isEditable = false
                contentType = "text/plain"
            }
            val scrollPane = JScrollPane(textPane)
            val console = TinyJpgConsole(textPane)
            ApplicationManager.getApplication().invokeAndWait {
                val fallbackContent = ContentFactory.getInstance().createContent(scrollPane, "", false)
                tw!!.contentManager.removeAllContents(true)
                tw!!.contentManager.addContent(fallbackContent)
            }
            return console
        }
    }
}