package com.example.assistant

import android.app.assist.AssistStructure
import android.graphics.RectF
import android.text.InputType
import com.example.domain.model.OcrLine
import com.example.domain.model.OcrWord

object AssistStructureExtractor {

    fun extractLines(
        structure: AssistStructure?,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): List<OcrLine> {
        if (structure == null || screenWidthPx <= 0 || screenHeightPx <= 0) return emptyList()
        val lines = mutableListOf<OcrLine>()
        val w = screenWidthPx.toFloat()
        val h = screenHeightPx.toFloat()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode ?: continue
            traverseNode(
                node = rootNode,
                offsetX = windowNode.left.toFloat(),
                offsetY = windowNode.top.toFloat(),
                screenW = w,
                screenH = h,
                outLines = lines
            )
        }
        return lines
    }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        offsetX: Float,
        offsetY: Float,
        screenW: Float,
        screenH: Float,
        outLines: MutableList<OcrLine>
    ) {
        if (node.visibility != android.view.View.VISIBLE) return

        val nodeLeft = offsetX + node.left
        val nodeTop = offsetY + node.top
        val nodeRight = nodeLeft + node.width
        val nodeBottom = nodeTop + node.height

        // Respect password / sensitive fields
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        if (!isPassword && node.width > 0 && node.height > 0) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length <= 2000) {
                val normRect = RectF(
                    (nodeLeft / screenW).coerceIn(0f, 1f),
                    (nodeTop / screenH).coerceIn(0f, 1f),
                    (nodeRight / screenW).coerceIn(0f, 1f),
                    (nodeBottom / screenH).coerceIn(0f, 1f)
                )
                val lineIndex = outLines.size
                val words = text.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .mapIndexed { idx, token ->
                        OcrWord(
                            index = idx,
                            text = token,
                            normalizedBounds = normRect,
                            lineIndex = lineIndex,
                            paragraphIndex = lineIndex
                        )
                    }
                outLines.add(
                    OcrLine(
                        index = lineIndex,
                        text = text,
                        normalizedBounds = normRect,
                        words = words,
                        paragraphIndex = lineIndex
                    )
                )
            }
        }

        val childOffsetX = nodeLeft - node.scrollX
        val childOffsetY = nodeTop - node.scrollY
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) ?: continue
            traverseNode(child, childOffsetX, childOffsetY, screenW, screenH, outLines)
        }
    }
}
