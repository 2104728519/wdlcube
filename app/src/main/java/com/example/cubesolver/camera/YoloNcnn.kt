package com.example.cubesolver.camera

import android.content.res.AssetManager
import android.view.Surface

object YoloNcnn {
    external fun loadModel(mgr: AssetManager, cpugpu: Int, sizeId: Int, modelName: String): Boolean
    
    external fun openCamera(facing: Int): Boolean
    external fun closeCamera(): Boolean
    external fun setOutputWindow(surface: Surface?): Boolean
    external fun setFpsCap(fps: Int): Boolean
    external fun setProbThreshold(threshold: Float): Boolean


    external fun getLatestColors(): IntArray?
    

    external fun isCubeDetected(): Boolean

    init {
        System.loadLibrary("yolov8ncnn")
    }
}