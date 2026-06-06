package com.example.greeting

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

// Review sample only: this file is not compiled as part of the agent.
class GreetingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nameInput = EditText(this).apply { hint = "Name" }
        val ageInput = EditText(this).apply { hint = "Age" }
        val greeting = TextView(this)
        val greetButton = Button(this).apply {
            text = "Say hello"
            setOnClickListener {
                val name = nameInput.text.toString()
                val age = ageInput.text.toString().toInt()
                greeting.text = GreetingFormatter.format(name, age)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameInput)
            addView(ageInput)
            addView(greetButton)
            addView(greeting)
        }
        setContentView(layout)
    }
}
