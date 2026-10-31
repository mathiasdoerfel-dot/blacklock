package de.matze.blackbox.tools.screenlock

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class LockTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val active = LockAccessibilityService.isServiceOrSettingActive(this)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        LockAccessibilityService.lockScreen()
    }
}
