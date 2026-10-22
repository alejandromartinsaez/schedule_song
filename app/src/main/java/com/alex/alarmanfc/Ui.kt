package com.alex.alarmanfc

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

/** Pantalla con scroll; devuelve el contenedor vertical. */
fun Activity.screen(): LinearLayout {
    val scroll = ScrollView(this).apply { fitsSystemWindows = true }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(32))
    }
    scroll.addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    setContentView(scroll)
    return root
}

fun Activity.card(parent: LinearLayout, title: String?): LinearLayout {
    val card = MaterialCardView(this)
    card.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    card.radius = dp(16).toFloat()
    val inner = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
    }
    card.addView(inner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    if (title != null) label(inner, title, 18f, bold = true)
    parent.addView(card)
    return inner
}

fun Activity.label(parent: LinearLayout, text: CharSequence, size: Float = 15f, bold: Boolean = false): TextView {
    val tv = TextView(this)
    tv.text = text
    tv.textSize = size
    if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
    tv.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
    parent.addView(tv)
    return tv
}

fun Activity.button(parent: LinearLayout, text: String, outlined: Boolean = false, onClick: () -> Unit): MaterialButton {
    val b = if (outlined) MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    else MaterialButton(this)
    b.text = text
    b.isAllCaps = false
    b.setOnClickListener { onClick() }
    b.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
    parent.addView(b)
    return b
}
