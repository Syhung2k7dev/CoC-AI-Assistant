package dev.syhung.cocassistant.detector

import android.graphics.Bitmap

interface SceneDetector {
    /**
     * Analyzes the current screen (as a Bitmap or other source) and identifies the current scene.
     */
    fun detectScene(screenData: Any): Scene
}
