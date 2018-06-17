package ru.mamykin.paginatedtextview.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.RectF
import android.support.v7.widget.AppCompatTextView
import android.text.Layout
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextUtils
import android.util.AttributeSet
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import ru.mamykin.paginatedtextview.extension.allIndexesOf
import ru.mamykin.paginatedtextview.pagination.PaginationController
import ru.mamykin.paginatedtextview.pagination.ReadState

class PaginatedTextView : AppCompatTextView, OnSwipeListener {

    companion object {
        const val MIN_TEXT_SIZE = 26
    }

    private val textCachedSizes = SparseIntArray()
    private val availableSpaceRect = RectF()
    private var maxTextSize = textSize
    private val textRect = RectF()
    private var initializedDimens: Boolean = false
    private var widthLimit: Int = 0
    private var swipeListener: OnSwipeListener? = null
    private var actionListener: OnActionListener? = null
    private lateinit var controller: PaginationController

    constructor(context: Context) : super(context) {
        initPaginatedTextView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initPaginatedTextView()
    }

    private fun initPaginatedTextView() {
        movementMethod = SwipeableMovementMethod()
        highlightColor = Color.TRANSPARENT
    }

    private fun getSelectedWord(): String {
        return text.subSequence(selectionStart, selectionEnd).trim(' ').toString()
    }

    fun setup(text: CharSequence) {
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                loadFirstPage(text)
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun loadFirstPage(text: CharSequence) {
        controller = PaginationController(
                text,
                width,
                height,
                paint,
                lineSpacingMultiplier,
                lineSpacingExtra,
                includeFontPadding
        )
        setPageText(controller.getCurrentPage())
    }

    fun setOnActionListener(listener: OnActionListener) {
        this.actionListener = listener
    }

    fun setOnSwipeListener(swipeListener: OnSwipeListener) {
        this.swipeListener = swipeListener
    }

    private fun setPageText(pageState: ReadState) {
        this.text = pageState.pageText
        actionListener?.onPageLoaded(pageState)
        updateWordsSpannables()
    }

    override fun onSwipeLeft() {
        setPageText(controller.getPrevPage())
        swipeListener?.onSwipeLeft()
    }

    override fun onSwipeRight() {
        setPageText(controller.getNextPage())
        swipeListener?.onSwipeRight()
    }

    override fun setTextSize(size: Float) {
        maxTextSize = size
        textCachedSizes.clear()
        adjustTextSize()
    }

    override fun setTextSize(unit: Int, size: Float) {
        val resources = context?.resources ?: Resources.getSystem()
        maxTextSize = TypedValue.applyDimension(unit, size, resources.displayMetrics)
        textCachedSizes.clear()
        adjustTextSize()
    }

    override fun onTextChanged(text: CharSequence, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        adjustTextSize()
    }

    private fun adjustTextSize() {
        if (initializedDimens) {
            val heightLimit = measuredHeight - compoundPaddingBottom - compoundPaddingTop
            widthLimit = measuredWidth - compoundPaddingLeft - compoundPaddingRight
            availableSpaceRect.right = widthLimit.toFloat()
            availableSpaceRect.bottom = heightLimit.toFloat()

            val textSize = efficientTextSizeSearch(MIN_TEXT_SIZE, maxTextSize.toInt(), availableSpaceRect)

            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
        }
    }

    /**
     * Проверка того, что текст вмещается в необходимые размеры
     */
    private fun checkTextFits(suggestedSize: Int, availableRect: RectF): Boolean {
        paint.textSize = suggestedSize.toFloat()
        val layout = StaticLayout(
                text,
                paint,
                widthLimit,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                0.0f,
                true
        )

        textRect.bottom = layout.height.toFloat()
        var maxWidth = -1
        for (i in 0..layout.lineCount) {
            if (maxWidth < layout.getLineWidth(i)) {
                maxWidth = layout.getLineWidth(i).toInt()
            }
        }
        textRect.right = maxWidth.toFloat()
        return containsRectF(availableRect, textRect)
    }

    private fun containsRectF(containerRect: RectF, actualRect: RectF): Boolean {
        containerRect.offset(0f, 0f)
        val aArea = containerRect.width() * containerRect.height()
        actualRect.offset(0f, 0f)
        val bArea = actualRect.width() * actualRect.height()
        return aArea >= bArea
    }

    private fun efficientTextSizeSearch(start: Int, end: Int, availableSpace: RectF): Int {
        val key = text.toString().length
        val size = textCachedSizes.get(key)
        if (size != 0) {
            return size
        }
        textCachedSizes.put(key, size)
        return binarySearch(start, end, availableSpace)
    }

    private fun binarySearch(start: Int, end: Int, availableSpace: RectF): Int {
        var lastBest = start
        var lowSize = start
        var highSize = end - 1
        var currentSize: Int
        while (lowSize <= highSize) {
            currentSize = (lowSize + highSize) / 2
            if (checkTextFits(currentSize, availableSpace)) {
                lastBest = currentSize
                lowSize = currentSize + 1
            } else {
                highSize = currentSize - 1
                lastBest = lowSize
            }
        }
        return lastBest
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        initializedDimens = true
        textCachedSizes.clear()
        if (width != oldWidth || height != oldHeight) {
            adjustTextSize()
        }
    }

    /**
     * Установка swipable spannable на каждое слово в TextView
     */
    private fun updateWordsSpannables() {
        val spans = text as Spannable
        val spaceIndexes = text.trim().allIndexesOf(' ')
        var wordStart = 0
        var wordEnd: Int
        for (i in 0..spaceIndexes.size) {
            val swipeableSpan = createSwipeableSpan()
            wordEnd = if (i < spaceIndexes.size) spaceIndexes[i] else spans.length
            spans.setSpan(swipeableSpan, wordStart, wordEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            wordStart = wordEnd + 1
        }
    }

    private fun getSelectedParagraph(widget: TextView): String? {
        val text = widget.text
        val selStart = widget.selectionStart
        val selEnd = widget.selectionEnd
        var parStart: Int
        var parEnd: Int
        // Если кликнули по пустому параграфу
        if (text.subSequence(selStart, selEnd).toString().contains("\n")) {
            // Номер символа, с которого начинается нужный абзац
            parStart = text.subSequence(0, selEnd).toString().lastIndexOf("\n")
            parStart = if (parStart == -1) 0 else parStart
            // Номер символа на котором кончается абзац
            parEnd = text.subSequence(selEnd, text.length).toString().indexOf("\n")
            parEnd = if (parEnd == -1) text.length else parEnd + selEnd
        } else {
            // Номер символа, с которого начинается нужный абзац
            parStart = text.subSequence(0, selStart).toString().lastIndexOf("\n")
            parStart = if (parStart == -1) 0 else parStart
            // Номер символа на котором кончается абзац
            parEnd = text.subSequence(selEnd, text.length).toString().indexOf("\n")
            parEnd = if (parEnd == -1) text.length else parEnd + selEnd
        }
        return text.subSequence(parStart, parEnd).toString()
    }

    private fun createSwipeableSpan(): SwipeableSpan = object : SwipeableSpan() {

        override fun onClick(widget: View) {
            val paragraph = getSelectedParagraph(widget as TextView)
            if (!TextUtils.isEmpty(paragraph)) {
                actionListener?.onClick(paragraph!!)
            }
        }

        override fun onLongClick(view: View) {
            val word = getSelectedWord()
            if (!TextUtils.isEmpty(word)) {
                actionListener?.onLongClick(word)
            }
        }

        override fun onSwipeLeft(view: View) {
            swipeListener?.onSwipeLeft()
        }

        override fun onSwipeRight(view: View) {
            swipeListener?.onSwipeRight()
        }
    }
}