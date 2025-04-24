/*
 * Copyright (C) 2024 Project Elixir
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.clocks

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import java.io.File
import kotlin.concurrent.thread

private const val TAG = "ElixirUtils: ElixirImageView"
class ElixirImageView : ImageView {
    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!,
        attrs,
        defStyle
    ) {
        init()
    }

    private fun init() {
        try {
            val imgFile = File(Environment.getExternalStorageDirectory().path + "/Elixir/current_depth.png")
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occured getting depth overlay, Error :- " + e.toString() + "\nRetrying again in 2 seconds!")
            Thread.sleep(2000)
            init()
        }
        
    }
}
