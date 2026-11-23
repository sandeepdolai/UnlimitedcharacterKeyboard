package com.pasteboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : android.app.Activity() {
    private lateinit var store: SnippetStore
    private lateinit var clipboard: ClipboardManager
    private lateinit var library: LinearLayout
    private lateinit var search: EditText
    private lateinit var editor: EditText
    private lateinit var countLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SnippetStore(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        window.statusBarColor = 0xFFF5F6FB.toInt()
        window.navigationBarColor = 0xFFF5F6FB.toInt()
        if (Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(buildScreen())
        refreshLibrary()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(25), dp(20), dp(30))
            setBackgroundColor(0xFFF5F6FB.toInt())
        }
        scroll.addView(page)

        page.addView(TextView(this).apply {
            text = "PASTEBOARD KEYBOARD"
            textSize = 11f
            letterSpacing = 0.13f
            setTextColor(0xFF6571E5.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Your words.\nAlways ready."
            textSize = 32f
            setLineSpacing(dp(1).toFloat(), 1f)
            setTextColor(0xFF202338.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(7), 0, dp(7))
        })
        page.addView(TextView(this).apply {
            text = "A clean keyboard with a private library for the text you use most."
            textSize = 14f
            setTextColor(0xFF74798B.toInt())
            setPadding(0, 0, 0, dp(18))
        })

        val setupCard = card()
        setupCard.addView(cardTitle("Get started"))
        setupCard.addView(bodyText("Enable Pasteboard in Android keyboard settings, then select it from the keyboard switcher."))
        val setupActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setupActions.addView(actionButton("Enable keyboard", true) {
            runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                .onFailure { Toast.makeText(this@MainActivity, "Open Android Settings and choose On-screen keyboard.", Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(7) })
        setupActions.addView(actionButton("Select keyboard", false) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        setupCard.addView(setupActions)
        page.addView(setupCard, matchWrap().apply { bottomMargin = dp(14) })

        val addCard = card()
        addCard.addView(cardTitle("Add to your library"))
        addCard.addView(bodyText("Save long messages, notes and templates. Paste them later without retyping."))
        editor = EditText(this).apply {
            hint = "Write or paste any length of text…"
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            minLines = 5
            maxLines = 9
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setTextColor(0xFF272A3D.toInt())
            background = rounded(0xFFF9FAFD.toInt(), 0xFFE1E4ED.toInt(), 13)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        addCard.addView(editor, matchWrap().apply { topMargin = dp(3) })
        val editActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        editActions.addView(actionButton("Save text", true) { saveEditorText() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(7) })
        editActions.addView(actionButton("Import clipboard", false) { importClipboard() },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        addCard.addView(editActions)
        page.addView(addCard, matchWrap().apply { bottomMargin = dp(14) })

        val libraryCard = card()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(cardTitle("Saved text"), LinearLayout.LayoutParams(0, -2, 1f))
        countLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF7E8292.toInt())
        }
        header.addView(countLabel)
        libraryCard.addView(header)
        search = EditText(this).apply {
            hint = "Search saved text"
            setSingleLine(true)
            textSize = 14f
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(0xFF272A3D.toInt())
            background = rounded(0xFFF9FAFD.toInt(), 0xFFE1E4ED.toInt(), 12)
        }
        libraryCard.addView(search, matchWrap().apply { topMargin = dp(10); bottomMargin = dp(9) })
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryCard.addView(library)
        page.addView(libraryCard, matchWrap())

        page.addView(TextView(this).apply {
            text = "Private by design  ·  Stored on this device  ·  No account required"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF888C9A.toInt())
            setPadding(0, dp(18), 0, 0)
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshLibrary(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        return scroll
    }

    private fun saveEditorText() {
        val value = editor.text?.toString().orEmpty()
        if (value.isBlank()) {
            editor.error = "Add some text first"
            return
        }
        runCatching { store.save(value) }
            .onSuccess {
                editor.text?.clear()
                refreshLibrary(search.text?.toString().orEmpty())
                Toast.makeText(this, "Saved ${value.length} characters.", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(this, "Could not save text: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    private fun importClipboard() {
        val value = runCatching {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        }.getOrDefault("")
        if (value.isBlank()) {
            Toast.makeText(this, "There is no text on the clipboard.", Toast.LENGTH_SHORT).show()
            return
        }
        editor.setText(value)
        editor.setSelection(editor.text.length)
        Toast.makeText(this, "Clipboard text imported.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshLibrary(query: String = if (::search.isInitialized) search.text?.toString().orEmpty() else "") {
        if (!::library.isInitialized) return
        library.removeAllViews()
        val items = store.getAll(query)
        countLabel.text = "${items.size} saved"
        if (items.isEmpty()) {
            library.addView(TextView(this).apply {
                text = if (query.isBlank()) "Your saved text will appear here." else "No saved text matches your search."
                textSize = 13f
                setTextColor(0xFF8A8E9D.toInt())
                setPadding(dp(4), dp(13), dp(4), dp(13))
            })
            return
        }
        items.forEach { snippet ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(9), dp(11), dp(8))
                background = rounded(0xFFFAFBFE.toInt(), 0xFFE8EAF1.toInt(), 12)
            }
            item.addView(TextView(this).apply {
                text = snippet.text
                textSize = 13f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFF32364A.toInt())
            })
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            controls.addView(TextView(this).apply {
                text = "${snippet.text.length} characters"
                textSize = 11f
                setTextColor(0xFF858999.toInt())
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            controls.addView(actionButton(if (snippet.isFavorite) "★ Pinned" else "☆ Pin", false) {
                store.setFavorite(snippet.id, !snippet.isFavorite)
                refreshLibrary(search.text?.toString().orEmpty())
            }, LinearLayout.LayoutParams(dp(91), dp(32)).apply { marginEnd = dp(6) })
            controls.addView(actionButton("Copy", true) { copyText(snippet.text) },
                LinearLayout.LayoutParams(dp(70), dp(32)).apply { marginEnd = dp(6) })
            controls.addView(actionButton("Delete", false) {
                store.delete(snippet.id)
                refreshLibrary(search.text?.toString().orEmpty())
            }, LinearLayout.LayoutParams(dp(70), dp(32)))
            item.addView(controls)
            library.addView(item, matchWrap().apply { bottomMargin = dp(7) })
        }
    }

    private fun copyText(value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("Saved text", value))
        Toast.makeText(this, "Copied ${value.length} characters.", Toast.LENGTH_SHORT).show()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(0xFFFFFFFF.toInt(), 0xFFE8EAF1.toInt(), 17)
    }

    private fun cardTitle(label: String) = TextView(this).apply {
        text = label
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF272A3D.toInt())
        setPadding(0, 0, 0, dp(5))
    }

    private fun bodyText(label: String) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(0xFF777D8E.toInt())
        setPadding(0, dp(2), 0, dp(11))
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF4D5266.toInt())
        background = rounded(if (primary) 0xFF6875E6.toInt() else 0xFFF0F2F7.toInt(), 0xFFE1E4ED.toInt(), 11)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
