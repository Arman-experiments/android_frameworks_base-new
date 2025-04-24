/*
 * Copyright (C) 2024 Project Elixir
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.android;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

public class ElixirWallpaperUtils {
    private static final String TAG = "ElixirWallpaperUtils";

    public static Bitmap getSegmentedBitmap(Context mContext) {
        Uri uriWithBitmap = Uri.parse(
                "content://com.elixer.updater.ElixirSubjectSegmentation");
        Log.i(TAG, "Calling ElixirUpdater for segmentation!");
        Cursor cursor = mContext.getContentResolver().query(uriWithBitmap, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            byte[] bytes = cursor.getBlob(cursor.getColumnIndexOrThrow("bitmap"));
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            cursor.close();
            return bitmap;
        }
        return null;
    }
}
