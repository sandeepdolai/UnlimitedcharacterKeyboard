package com.pasteboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class PasteboardImeService : InputMethodService() {
    private enum class Panel { TYPING, CLIPS, EMOJI }

    private lateinit var store: SnippetStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var suggestions: LinearLayout
    private var panel = Panel.TYPING
    private var shifted = false
    private var capsLock = false
    private var symbols = false
    private var secureField = false
    private var darkMode = false
    private var haptics = true
    private var oneHanded = 0 // 0 = off, 1 = left, 2 = right
    private var lastSpaceAt = 0L
    private var trackpadActive = false
    private var trackpadLastX = Float.NaN
    private var trackpadDistance = 0f
    private var emojiCategory = "Smileys"
    private var previewPopup: PopupWindow? = null

    private val wordBank = listOf(
        "about", "after", "again", "also", "and", "any", "are", "because", "been",
        "before", "best", "but", "can", "come", "could", "day", "did", "do", "even",
        "every", "first", "for", "from", "get", "give", "good", "great", "had", "has",
        "have", "hello", "here", "how", "i", "if", "in", "into", "is", "it", "just",
        "know", "like", "love", "make", "me", "more", "most", "my", "new", "no",
        "not", "now", "of", "on", "one", "only", "or", "other", "our", "out",
        "people", "please", "really", "right", "said", "see", "she", "so", "some",
        "than", "thank", "thanks", "that", "the", "their", "them", "then", "there",
        "these", "they", "think", "this", "time", "to", "today", "too", "up", "use",
        "very", "want", "was", "way", "we", "well", "were", "what", "when", "where",
        "which", "who", "will", "with", "would", "yes", "you", "your"
    )

    private val emojiGroups = linkedMapOf(
        "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🙂 🙃 😉 😊 😍 🥰 😘 😎 🤩 🥳 🥹 😭 😢 😡 🤔 🤗 🙌 👏 👍 👎 ❤️ 💖 💯",
        "People" to "👋 🤚 🖐️ ✋ 🫶 🙏 💪 👀 👋🏻 👨‍👩‍👧‍👦 🧑‍💻 👩‍🎓 🧑‍🎨",
        "Nature" to "🐶 🐱 🐼 🦊 🐸 🐵 🦋 🌸 🌻 🌹 🌈 ☀️ 🌙 ⭐ 🌍 🔥 🌊",
        "Food" to "🍎 🍊 🍋 🍉 🍇 🍓 🥭 🍍 🥑 🍕 🍔 🍟 🌮 🍜 🍰 🍫 ☕ 🧋",
        "Symbols" to "✅ ❌ ⚠️ ✨ 💡 🎉 🎯 🚀 📌 🔒 🔔 💬 ♻️ ™️ ©️"
    )

    override fun onCreate() {
        super.onCreate()
        store = SnippetStore(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        readPreferences()
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(7))
            background = solid(if (darkMode) 0xFF22242B.toInt() else 0xFFD5D9E2.toInt(), 0)
        }
        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(4))
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), 0, dp(1), 0)
        }
        root.addView(buildToolbar(), LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(suggestions, LinearLayout.LayoutParams(-1, dp(37)))
        root.addView(body, LinearLayout.LayoutParams(-1, -2))
        renderBody()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        secureField = isSecureField(attribute)
        if (secureField) panel = Panel.TYPING
        val flags = attribute?.inputType ?: InputType.TYPE_CLASS_TEXT
        shifted = (flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        capsLock = false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        secureField = isSecureField(info)
        if (secureField) panel = Panel.TYPING
        if (::body.isInitialized) renderBody()
        if (::root.isInitialized) refreshToolbar()
    }

    private fun readPreferences() {
        val prefs = getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        darkMode = prefs.getBoolean("dark_mode", false)
        haptics = prefs.getBoolean("haptics", true)
        oneHanded = prefs.getInt("one_handed", 0).coerceIn(0, 2)
    }

    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "P"
            textSize = 19f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (darkMode) 0xFFFFFFFF.toInt() else 0xFF25283A.toInt())
            background = rounded(if (darkMode) 0xFF373A45.toInt() else 0xFFE9EBF1.toInt(), 0, 12)
        }
        bar.addView(brand, LinearLayout.LayoutParams(dp(38), dp(34)).apply { marginEnd = dp(6) })

        if (secureField) {
            bar.addView(toolbarChip("Password field", true) {},
                LinearLayout.LayoutParams(0, dp(34), 1f))
        } else {
            bar.addView(toolbarChip("Clips", panel == Panel.CLIPS) {
                panel = if (panel == Panel.CLIPS) Panel.TYPING else Panel.CLIPS
                renderBody()
                refreshToolbar()
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(5) })
            bar.addView(toolbarChip("Emoji", panel == Panel.EMOJI) {
                panel = if (panel == Panel.EMOJI) Panel.TYPING else Panel.EMOJI
                renderBody()
                refreshToolbar()
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(5) })
            bar.addView(toolbarChip("Save", false) { saveCurrentClipboard() },
                LinearLayout.LayoutParams(0, dp(34), 0.85f).apply { marginEnd = dp(5) })
            bar.addView(toolbarChip("•••", false) { showToolsMenu(it) },
                LinearLayout.LayoutParams(dp(48), dp(34)))
        }
        return bar
    }

    private fun refreshToolbar() {
        if (!::root.isInitialized) return
        if (root.childCount > 0) root.removeViewAt(0)
        root.addView(buildToolbar(), 0)
    }

    private fun renderBody() {
        if (!::body.isInitialized) return
        applyKeyboardWidth()
        body.removeAllViews()
        suggestions.visibility = if (panel == Panel.TYPING) View.VISIBLE else View.GONE
        body.background = solid(if (darkMode) 0xFF22242B.toInt() else 0xFFD5D9E2.toInt(), 0)
        when {
            secureField -> {
                body.addView(infoText("Clipboard is hidden while a password field is active."))
                renderTypingKeyboard()
            }
            panel == Panel.CLIPS -> renderSnippetLibrary()
            panel == Panel.EMOJI -> renderEmojiPicker()
            else -> renderTypingKeyboard()
        }
        refreshSuggestions()
    }

    private fun applyKeyboardWidth() {
        if (!::body.isInitialized || !::root.isInitialized) return
        val params = body.layoutParams
        params.width = if (oneHanded == 0) -1 else (resources.displayMetrics.widthPixels * 0.84f).toInt()
        body.layoutParams = params
        root.gravity = when (oneHanded) {
            1 -> Gravity.TOP or Gravity.START
            2 -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    private fun renderTypingKeyboard() {
        val inputType = currentInputEditorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("-", "0", "⌫"),
                listOf("🌐", "space", ".", "↵")
            ).forEach { addKeyRow(it) }
            return
        }

        val rows = if (symbols) {
            listOf(
                "1234567890".map { it.toString() },
                listOf("@", "#", "\$", "%", "&", "-", "+", "(", ")"),
                listOf("=", "*", "\"", "'", ":", ";", "!", "?", "⌫"),
                listOf("ABC", "🌐", ",", "space", ".", "↵")
            )
        } else {
            listOf(
                "qwertyuiop".map { it.toString() },
                "asdfghjkl".map { it.toString() },
                listOf("⇧") + "zxcvbnm".map { it.toString() } + "⌫",
                listOf("?123", "🌐", ",", "space", ".", "↵")
            )
        }
        rows.forEach { addKeyRow(it) }
    }

    private fun addKeyRow(keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keys.forEach { key ->
            val weight = when (key) {
                "space" -> 4.8f
                "?123", "ABC", "⇧", "⌫", "↵" -> 1.45f
                "🌐", ",", "." -> 0.82f
                else -> 1f
            }
            row.addView(keyView(key), LinearLayout.LayoutParams(0, dp(45), weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun keyView(label: String): View = TextView(this).apply {
        val isLetter = label.length == 1 && label[0].isLetter()
        text = when {
            label == "space" -> "English"
            isLetter && shifted -> label.uppercase()
            else -> label
        }
        textSize = when (label) {
            "space" -> 12f
            "↵", "⌫", "⇧", "🌐" -> 17f
            else -> 18f
        }
        gravity = Gravity.CENTER
        setTypeface(typeface, if (label in listOf("?123", "ABC", "⇧", "⌫", "↵")) Typeface.MEDIUM else Typeface.NORMAL)
        setTextColor(
            if (darkMode) {
                if (label == "↵") 0xFFFFFFFF.toInt() else 0xFFF8F8FB.toInt()
            } else {
                if (label == "↵") 0xFFFFFFFF.toInt() else 0xFF25283A.toInt()
            }
        )
        val special = label in listOf("?123", "ABC", "⇧", "⌫", "🌐", ",", ".")
        val color = when {
            label == "↵" -> 0xFF6C78E8.toInt()
            darkMode && special -> 0xFF414550.toInt()
            darkMode -> 0xFF343741.toInt()
            special -> 0xFFB9C0CE.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        background = rounded(color, if (darkMode) 0xFF30333D.toInt() else 0xFFD2D6E0.toInt(), 8)
        if (Build.VERSION.SDK_INT >= 21) elevation = dp(1).toFloat()
        tag = label
        setOnClickListener {
            animate().scaleX(0.94f).scaleY(0.94f).setDuration(65).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            handleKey(label)
        }
        setOnLongClickListener {
            when {
                label == "space" -> {
                    trackpadActive = true
                    trackpadLastX = Float.NaN
                    trackpadDistance = 0f
                    Toast.makeText(this@PasteboardImeService, "Move left or right to position the cursor", Toast.LENGTH_SHORT).show()
                    true
                }
                label == "⇧" -> {
                    capsLock = !capsLock
                    shifted = capsLock
                    renderBody()
                    true
                }
                label == "⌫" -> {
                    deletePreviousWord()
                    true
                }
                isLetter -> {
                    showAlternates(this, label)
                    true
                }
                else -> false
            }
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (isLetter) showKeyPreview(view, if (shifted) label.uppercase() else label)
                MotionEvent.ACTION_MOVE -> {
                    if (trackpadActive && label == "space") {
                        if (trackpadLastX.isNaN()) {
                            trackpadLastX = event.x
                        } else {
                            val delta = event.x - trackpadLastX
                            trackpadLastX = event.x
                            trackpadDistance += delta
                            val threshold = dp(8).toFloat()
                            while (trackpadDistance >= threshold) {
                                moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)
                                trackpadDistance -= threshold
                            }
                            while (trackpadDistance <= -threshold) {
                                moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)
                                trackpadDistance += threshold
                            }
                        }
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dismissPreview()
                    if (label == "space") trackpadActive = false
                }
            }
            false
        }
        if (Build.VERSION.SDK_INT >= 26) setTooltipText(label)
    }

    private fun handleKey(label: String) {
        val connection = currentInputConnection ?: return
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (label) {
            "⇧" -> {
                shifted = !shifted
                capsLock = false
                renderBody()
                return
            }
            "?123" -> {
                symbols = true
                renderBody()
                return
            }
            "ABC" -> {
                symbols = false
                renderBody()
                return
            }
            "🌐" -> {
                switchToNextInputMethod(false)
                return
            }
            "space" -> {
                val now = System.currentTimeMillis()
                val before = connection.getTextBeforeCursor(3, 0)?.toString().orEmpty()
                if (now - lastSpaceAt < 420 && before.endsWith(" ") &&
                    before.dropLast(1).lastOrNull()?.isLetterOrDigit() == true
                ) {
                    connection.deleteSurroundingText(1, 0)
                    connection.commitText(". ", 1)
                } else {
                    connection.commitText(" ", 1)
                }
                lastSpaceAt = now
            }
            "⌫" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.deleteSurroundingTextInCodePoints(1, 0)
                else connection.deleteSurroundingText(1, 0)
            }
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    connection.performEditorAction(action)
                } else {
                    connection.commitText("\n", 1)
                }
            }
            else -> {
                val value = if (shifted && label.length == 1 && label[0].isLetter()) label.uppercase() else label
                connection.commitText(value, 1)
                if (shifted && !capsLock) shifted = false
            }
        }
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        if (!::suggestions.isInitialized) return
        suggestions.removeAllViews()
        if (panel != Panel.TYPING || secureField) {
            suggestions.visibility = View.GONE
            return
        }
        suggestions.visibility = View.VISIBLE
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val prefix = before.takeLastWhile { it.isLetter() }
        val lower = prefix.lowercase()
        val choices = if (lower.isBlank()) {
            listOf("Hello", "Thanks", "Yes")
        } else {
            val matches = wordBank.filter { it.startsWith(lower) && it != lower }.take(2)
            (listOf(prefix) + matches).take(3)
        }
        val finalChoices = choices.toMutableList()
        while (finalChoices.size < 3) finalChoices.add("")
        finalChoices.forEachIndexed { index, word ->
            val cell = TextView(this).apply {
                text = word
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (darkMode) 0xFFE8EAF2.toInt() else 0xFF343748.toInt())
                setOnClickListener { if (word.isNotBlank()) commitSuggestion(word) }
            }
            suggestions.addView(cell, LinearLayout.LayoutParams(0, dp(31), 1f))
            if (index < 2) {
                suggestions.addView(View(this).apply {
                    setBackgroundColor(if (darkMode) 0xFF50535E.toInt() else 0xFFB7BBC5.toInt())
                }, LinearLayout.LayoutParams(dp(1), dp(18)))
            }
        }
    }

    private fun commitSuggestion(word: String) {
        val connection = currentInputConnection ?: return
        val prefix = connection.getTextBeforeCursor(80, 0)?.toString().orEmpty().takeLastWhile { it.isLetter() }
        if (prefix.isNotEmpty()) connection.deleteSurroundingText(prefix.length, 0)
        connection.commitText(word + " ", 1)
        refreshSuggestions()
    }

    private fun moveCursor(keyCode: Int) {
        val connection = currentInputConnection ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun deletePreviousWord() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(500, 0)?.toString().orEmpty()
        val count = before.reversed().takeWhile { it.isWhitespace() }.length +
            before.reversed().dropWhile { it.isWhitespace() }.takeWhile { !it.isWhitespace() }.length
        if (count > 0) connection.deleteSurroundingText(count, 0)
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.deleteSurroundingTextInCodePoints(1, 0)
        else connection.deleteSurroundingText(1, 0)
        refreshSuggestions()
    }

    private fun showKeyPreview(anchor: View, label: String) {
        dismissPreview()
        val preview = TextView(this).apply {
            text = label
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(if (darkMode) 0xFFF8F8FB.toInt() else 0xFF25283A.toInt())
            background = rounded(if (darkMode) 0xFF414550.toInt() else 0xFFFFFFFF.toInt(), 0xFFBFC4D0.toInt(), 12)
            setPadding(dp(14), dp(5), dp(14), dp(5))
        }
        previewPopup = PopupWindow(preview, dp(48), dp(54), false).also { popup ->
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            popup.showAtLocation(root, Gravity.NO_GRAVITY, location[0] + anchor.width / 2 - dp(24), location[1] - dp(54))
        }
    }

    private fun dismissPreview() {
        runCatching { previewPopup?.dismiss() }
        previewPopup = null
    }

    private fun showAlternates(anchor: View, label: String) {
        val variants = when (label.lowercase()) {
            "a" -> listOf("á", "à", "â", "ä", "ã", "å", "æ")
            "e" -> listOf("é", "è", "ê", "ë")
            "i" -> listOf("í", "ì", "î", "ï")
            "o" -> listOf("ó", "ò", "ô", "ö", "õ", "ø")
            "u" -> listOf("ú", "ù", "û", "ü")
            "c" -> listOf("ç", "ć", "č")
            "n" -> listOf("ñ", "ń")
            "s" -> listOf("ß", "ś", "š")
            else -> listOf(label, label.uppercase())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(if (darkMode) 0xFF353842.toInt() else 0xFFFFFFFF.toInt(), 0xFFBFC4D0.toInt(), 12)
        }
        variants.forEach { variant ->
            row.addView(TextView(this).apply {
                text = if (shifted) variant.uppercase() else variant
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (darkMode) 0xFFFFFFFF.toInt() else 0xFF25283A.toInt())
                setPadding(dp(8), dp(7), dp(8), dp(7))
                setOnClickListener {
                    currentInputConnection?.commitText(text, 1)
                    dismissPreview()
                    runCatching { popup.dismiss() }
                    refreshSuggestions()
                }
            }, LinearLayout.LayoutParams(-2, dp(42)))
        }
        val popup = PopupWindow(row, -2, -2, true)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.showAtLocation(root, Gravity.NO_GRAVITY, location[0], location[1] - dp(52))
    }

    private fun renderSnippetLibrary() {
        body.addView(sectionTitle("Your saved text"))
        val search = EditText(this).apply {
            hint = "Search saved text"
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(13), 0, dp(13), 0)
            setTextColor(if (darkMode) 0xFFF5F5F8.toInt() else 0xFF25283A.toInt())
            setHintTextColor(if (darkMode) 0xFF9B9EAA.toInt() else 0xFF85899A.toInt())
            background = rounded(if (darkMode) 0xFF343741.toInt() else 0xFFFFFFFF.toInt(), if (darkMode) 0xFF484C57.toInt() else 0xFFD6DAE4.toInt(), 12)
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(7) })
        val listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(listHost)
        }
        body.addView(scroll, LinearLayout.LayoutParams(-1, dp(222)))
        fun populate(query: String) {
            listHost.removeAllViews()
            val snippets = store.getAll(query)
            if (snippets.isEmpty()) {
                listHost.addView(infoText(if (query.isBlank()) "No saved text yet. Use Save or add text in the app." else "No matching text."))
                return
            }
            snippets.take(70).forEach { snippet ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(7))
                    background = rounded(if (darkMode) 0xFF343741.toInt() else 0xFFFFFFFF.toInt(), if (darkMode) 0xFF484C57.toInt() else 0xFFD6DAE4.toInt(), 12)
                }
                card.addView(TextView(this).apply {
                    text = snippet.text.replace('\n', ' ')
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (darkMode) 0xFFF2F3F7.toInt() else 0xFF272A3A.toInt())
                })
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                actions.addView(TextView(this).apply {
                    text = "${snippet.text.length} characters"
                    textSize = 11f
                    setTextColor(if (darkMode) 0xFFAEB2BE.toInt() else 0xFF777D8D.toInt())
                }, LinearLayout.LayoutParams(0, dp(30), 1f))
                actions.addView(toolbarChip(if (snippet.isFavorite) "★" else "☆", false) {
                    store.setFavorite(snippet.id, !snippet.isFavorite)
                    populate(search.text?.toString().orEmpty())
                }, LinearLayout.LayoutParams(dp(40), dp(30)).apply { marginEnd = dp(5) })
                actions.addView(toolbarChip("Paste", true) {
                    val inserted = currentInputConnection?.commitText(snippet.text, 1) ?: false
                    if (!inserted) Toast.makeText(this@PasteboardImeService, "The text field is no longer active.", Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(dp(74), dp(30)))
                card.addView(actions)
                listHost.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
        populate("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderEmojiPicker() {
        body.addView(sectionTitle("Emoji"))
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        emojiGroups.keys.forEach { category ->
            categories.addView(toolbarChip(category, category == emojiCategory) {
                emojiCategory = category
                renderBody()
            }, LinearLayout.LayoutParams(-2, dp(32)).apply { marginEnd = dp(5) })
        }
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(categories)
        }
        body.addView(categoryScroll, LinearLayout.LayoutParams(-1, dp(36)))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val emojis = emojiGroups[emojiCategory].orEmpty().split(" ")
        emojis.chunked(8).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { emoji ->
                row.addView(TextView(this).apply {
                    text = emoji
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        currentInputConnection?.commitText(emoji, 1)
                        refreshSuggestions()
                    }
                }, LinearLayout.LayoutParams(0, dp(43), 1f))
            }
            while (row.childCount < 8) row.addView(View(this), LinearLayout.LayoutParams(0, dp(43), 1f))
            grid.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, dp(195)))
    }

    private fun showToolsMenu(anchor: View) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(if (darkMode) 0xFF353842.toInt() else 0xFFFFFFFF.toInt(), 0xFFBFC4D0.toInt(), 14)
        }
        val popup = PopupWindow(menu, dp(190), -2, true)
        fun option(label: String, action: () -> Unit) {
            menu.addView(TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (darkMode) 0xFFF4F5F8.toInt() else 0xFF292C3C.toInt())
                setPadding(dp(12), dp(11), dp(12), dp(11))
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            })
        }
        option(if (darkMode) "Light appearance" else "Dark appearance") {
            darkMode = !darkMode
            getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).edit().putBoolean("dark_mode", darkMode).apply()
            updateColors()
        }
        option(if (haptics) "Turn haptic feedback off" else "Turn haptic feedback on") {
            haptics = !haptics
            getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).edit().putBoolean("haptics", haptics).apply()
        }
        option(when (oneHanded) { 0 -> "Enable one-handed mode" 1 -> "Move keyboard to right" else -> "Disable one-handed mode" }) {
            oneHanded = when (oneHanded) { 0 -> 1; 1 -> 2; else -> 0 }
            getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE).edit().putInt("one_handed", oneHanded).apply()
            renderBody()
        }
        option("Manage saved text") { openManager() }
        popup.showAsDropDown(anchor, -dp(130), dp(4))
    }

    private fun updateColors() {
        root.background = solid(if (darkMode) 0xFF22242B.toInt() else 0xFFD5D9E2.toInt(), 0)
        renderBody()
        refreshToolbar()
    }

    private fun saveCurrentClipboard() {
        if (secureField) {
            Toast.makeText(this, "Clipboard is hidden in password fields.", Toast.LENGTH_SHORT).show()
            return
        }
        val clip = try { clipboardManager.primaryClip } catch (_: SecurityException) { null }
        val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (value.isBlank()) {
            Toast.makeText(this, "There is no text on the clipboard.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { store.save(value) }
            .onSuccess {
                panel = Panel.CLIPS
                renderBody()
                refreshToolbar()
                Toast.makeText(this, "Saved ${value.length} characters.", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(this, "Could not save text: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    private fun openManager() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isSecureField(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        val inputClass = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        return (inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun toolbarChip(label: String, selected: Boolean, action: (View) -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(
            if (darkMode) {
                if (selected) 0xFFFFFFFF.toInt() else 0xFFD8DAE2.toInt()
            } else {
                if (selected) 0xFFFFFFFF.toInt() else 0xFF41465A.toInt()
            }
        )
        background = rounded(
            if (selected) 0xFF6875E6.toInt() else if (darkMode) 0xFF343741.toInt() else 0xFFE9ECF3.toInt(),
            if (darkMode) 0xFF484C57.toInt() else 0xFFD1D6E1.toInt(),
            11
        )
        setOnClickListener { action(this) }
    }

    private fun sectionTitle(label: String) = TextView(this).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (darkMode) 0xFFF4F5F8.toInt() else 0xFF282B3C.toInt())
        setPadding(dp(3), dp(3), 0, dp(7))
    }

    private fun infoText(message: String) = TextView(this).apply {
        text = message
        textSize = 12f
        setTextColor(if (darkMode) 0xFFB9BDCA.toInt() else 0xFF777D8D.toInt())
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun rounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun solid(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
