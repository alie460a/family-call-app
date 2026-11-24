package com.familycall.app;

import android.content.Context;
import android.media.AudioManager;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "AudioRoute")
public class AudioRoutePlugin extends Plugin {

    private AudioManager am() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    // inCall=true: phone-call audio mode, sound goes to the earpiece. false: back to normal.
    @PluginMethod
    public void setMode(PluginCall call) {
        boolean inCall = Boolean.TRUE.equals(call.getBoolean("inCall", false));
        AudioManager a = am();
        a.setMode(inCall ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
        a.setSpeakerphoneOn(false);
        call.resolve();
    }

    @PluginMethod
    public void setSpeaker(PluginCall call) {
        am().setSpeakerphoneOn(Boolean.TRUE.equals(call.getBoolean("on", false)));
        call.resolve();
    }
}
