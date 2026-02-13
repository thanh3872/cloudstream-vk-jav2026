package com.thanh3872.vkprovider

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class VKJav2026Plugin : Plugin() {
    override fun load(context: Context) {
        // Đăng ký provider chính
        registerMainAPI(VKJav2026Provider())
        println("DEBUG - VK Jav2026 Plugin loaded successfully!")
    }
}