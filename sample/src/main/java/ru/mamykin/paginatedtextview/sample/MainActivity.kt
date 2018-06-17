package ru.mamykin.paginatedtextview.sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import ru.mamykin.paginatedtextview.pagination.ReadState
import ru.mamykin.paginatedtextview.view.OnActionListener
import ru.mamykin.paginatedtextview.view.OnSwipeListener
import ru.mamykin.paginatedtextview.view.PaginatedTextView

class MainActivity : AppCompatActivity(), OnSwipeListener, OnActionListener {

    private lateinit var tvReadPercent: TextView
    private lateinit var tvReadPages: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvReadPercent = findViewById(R.id.tv_percent_read)
        tvReadPages = findViewById(R.id.tv_pages_read)

        val tvBookContent = findViewById<PaginatedTextView>(R.id.tv_book_content)
        tvBookContent.setup(getText())
        tvBookContent.setOnActionListener(this)
        tvBookContent.setOnSwipeListener(this)
    }

    private fun getText(): String {
        val inputStream = resources.openRawResource(R.raw.sample_text)
        val bytes = ByteArray(inputStream.available())
        inputStream.read(bytes)
        return String(bytes)
    }

    private fun displayReadState(readState: ReadState) {
        tvReadPages.text = "${readState.currentIndex / readState.pagesCount}"
        tvReadPercent.text = "${readState.readPercent}%"
    }

    override fun onSwipeLeft() {
        Toast.makeText(this, "Swipe left", LENGTH_LONG).show()
    }

    override fun onSwipeRight() {
        Toast.makeText(this, "Swipe right", LENGTH_LONG).show()
    }

    override fun onClick(paragraph: String) {
        Toast.makeText(this, "Paragraph clicked: $paragraph", LENGTH_LONG).show()
    }

    override fun onLongClick(word: String) {
        Toast.makeText(this, "Word clicked: $word", LENGTH_LONG).show()
    }

    override fun onPageLoaded(state: ReadState) {
        displayReadState(state)
    }
}