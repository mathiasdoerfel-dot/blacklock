package de.matze.blackbox.tools.screenlock

import android.service.quicksettings.TileService

class LockTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LockAccessibilityService.lockScreen()
    }
}
