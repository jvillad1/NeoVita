package com.neovita.app.auth

import android.app.Activity

// Credential Manager shows its account picker over the foreground Activity;
// MainActivity registers itself here (and clears on destroy).
object CurrentActivityHolder {
    @Volatile
    var activity: Activity? = null
}
