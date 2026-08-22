package com.example.blacklock

import android.service.quicksettings.TileService

class LockTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LockAccessibilityService.lockScreen()
    }
}
