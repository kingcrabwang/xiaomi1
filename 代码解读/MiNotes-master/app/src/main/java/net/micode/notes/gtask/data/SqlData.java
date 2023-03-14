/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;


public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName(); // TAG常量，用于在日志中标记这个类

    private static final int INVALID_ID = -99999; // 无效ID常量

    // PROJECTION_DATA常量数组，用于查询数据表中的字段
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 数据表字段的索引常量，用于读取查询结果集
    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver; // ContentResolver对象，用于操作数据表
    private boolean mIsCreate; // 是否创建了数据
    private long mDataId; // 数据ID
    private String mDataMimeType; // 数据类型
    private String mDataContent; // 数据内容
    private long mDataContentData1; // 数据字段1
    private String mDataContentData3; // 数据字段3
    private ContentValues mDiffDataValues; // ContentValues对象，用于更新数据表的内容

    // 构造函数，初始化ContentResolver对象
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    // 从Cursor对象中读取数据
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN); // 读取数据ID
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN); // 读取数据类型
        mDataContent = c.getString(DATA_CONTENT_COLUMN); // 读取数据内容
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN); // 读取数据字段1
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN); // 读取数据字段3
    }

    // 设置数据内容
    public void setContent(JSONObject js) throws JSONException {
        // 从JSONObject对象中读取数据，若不存在则使用默认值
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";

        // 比较新数据和旧数据，如果不同则添加到ContentValues对象中
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }
    public JSONObject getContent() throws JSONException {
        // 如果数据还没有在数据库中创建，则返回null，并打印错误日志
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        // 将数据内容转换为JSONObject对象，并返回
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }
    public void commit(long noteId, boolean validateVersion, long version) {
        // 如果数据还没有在数据库中创建，则将其插入到数据库中
        if (mIsCreate) {
            // 如果数据id无效且存在于差异数据值中，则从差异数据值中删除该id
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }
            // 将数据与所属笔记的id关联起来，并将其插入到数据库中
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从uri中获取新插入数据的id
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 如果数据已存在于数据库中，则将其更新
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                // 如果需要验证版本号，则只有当笔记的版本号与指定版本号相同时，才能更新数据
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                // 如果更新的数据数量为0，则打印警告日志
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }
        // 清空差异数据值，并将是否创建标志设为false
        mDiffDataValues.clear();
        mIsCreate = false;
    }
    //返回数据索引
    public long getId() {
        return mDataId;
    }
}
