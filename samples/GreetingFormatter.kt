package com.example.greeting

// Used by GreetingActivity to build the text displayed after a button click.
object GreetingFormatter {
    fun format(name: String, age: Int): String {
        return "Hello, $name! Next year you will be ${age + 1}."
    }
}
