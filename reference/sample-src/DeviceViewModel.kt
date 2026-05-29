package com.example.mower

class DeviceViewModel {
    fun connect(device: Device?) {
        println(device!!.name)
    }
}

data class Device(val name: String)
